package com.hmdp.service;

import com.hmdp.config.MeetAiProperties;
import com.hmdp.dto.AiMeetPlanSet;
import com.hmdp.entity.MeetRoom;
import com.hmdp.enums.MeetPlanEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded Plan-and-Execute orchestration for a single planning attempt.
 *
 * <p>The workflow is deliberately finite: Java creates the plan, executes
 * read-only tools, lets Spring AI perform a bounded tool-calling loop for
 * drafting, and then applies the same Java policy a second time.</p>
 */
@Service
@RequiredArgsConstructor
public class MeetPlanningAgent {

    private static final List<String> PLAN_STEPS = List.of(
            "read_confirmed_preferences",
            "find_feasible_restaurants",
            "inspect_restaurant_candidates",
            "draft_and_validate_plan");
    private static final Set<String> REQUIRED_MODEL_TOOLS = Set.of(
            "read_confirmed_preferences",
            "find_feasible_restaurants",
            "inspect_restaurant_candidates",
            "validate_plan_draft");

    private final MeetRestaurantCandidateService candidateService;
    private final MeetPlanPolicyService planPolicyService;
    private final MeetPlanEventService eventService;
    private final MeetPlanningAiService planningAiService;
    private final MeetAiProperties aiProperties;

    public PlanningResult plan(
            MeetRoom room,
            Long runId,
            Long attemptId) {
        eventService.append(
                runId,
                attemptId,
                MeetPlanEventType.AGENT_PLAN_CREATED,
                "Agent 已创建受控执行计划：读取偏好 → 检查候选 → 组织并校验方案",
                cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                        "strategy", "BOUNDED_REACT",
                        "steps", PLAN_STEPS,
                        "writesBusinessState", false)));

        MeetPlanningTools tools = new MeetPlanningTools(
                room,
                runId,
                attemptId,
                candidateService,
                planPolicyService,
                eventService,
                aiProperties.getMaxToolCalls());
        MeetRestaurantCandidateService.CandidateSelection selection =
                tools.preflight();
        if (selection.candidates().size() < 3) {
            tools.readConfirmedPreferences();
            tools.findFeasibleRestaurants();
            return new PlanningResult(selection, null);
        }

        AiMeetPlanSet planSet = planningAiService.generatePlans(
                room, selection, tools);
        if (planningAiService.isEnabled()) {
            Set<String> missingTools = new java.util.HashSet<>(
                    REQUIRED_MODEL_TOOLS);
            missingTools.removeAll(tools.completedToolNames());
            if (!missingTools.isEmpty()) {
                throw new IllegalStateException(
                        "Agent 未完成必要工具调用: "
                                + String.join(", ", missingTools));
            }
            if (planSet.getPlans() == null) {
                throw new IllegalStateException("Agent 未返回方案列表");
            }
            Set<Long> uninspectedShopIds = planSet.getPlans().stream()
                    .map(com.hmdp.dto.AiMeetPlanOption::getShopId)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            uninspectedShopIds.removeAll(tools.inspectedCandidateIds());
            if (!uninspectedShopIds.isEmpty()) {
                throw new IllegalStateException(
                        "Agent 未查看所选餐厅详情: " + uninspectedShopIds);
            }
            if (!tools.wasSuccessfullyValidated(planSet)) {
                throw new IllegalStateException(
                        "Agent 最终方案不是已通过校验的同一份草稿");
            }
        } else {
            tools.readConfirmedPreferences();
            tools.findFeasibleRestaurants();
            List<Long> topShopIds = selection.candidates().stream()
                    .limit(3)
                    .map(com.hmdp.dto.MeetRestaurantCandidate::getShopId)
                    .toList();
            tools.inspectRestaurantCandidates(topShopIds);
            MeetPlanningTools.PlanValidationResult validation =
                    tools.validatePlanDraft(planSet);
            if (!validation.valid()) {
                throw new IllegalArgumentException(
                        String.join("；", validation.errors()));
            }
        }
        planPolicyService.assertValid(planSet, selection.candidates());
        return new PlanningResult(selection, planSet);
    }

    public record PlanningResult(
            MeetRestaurantCandidateService.CandidateSelection selection,
            AiMeetPlanSet planSet) {

        public boolean needsClarification() {
            return planSet == null;
        }
    }
}
