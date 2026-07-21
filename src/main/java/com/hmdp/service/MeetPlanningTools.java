package com.hmdp.service;

import com.hmdp.dto.AiMeetPlanSet;
import com.hmdp.dto.MeetPreferenceData;
import com.hmdp.dto.MeetRestaurantCandidate;
import com.hmdp.entity.MeetRoom;
import com.hmdp.enums.MeetPlanEventType;
import lombok.Getter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only tools exposed to the planning model for one room and one attempt.
 *
 * <p>The instance owns the room context, so the model cannot choose another
 * room by passing an arbitrary room ID. No method mutates business state.</p>
 */
public final class MeetPlanningTools {

    private final MeetRoom room;
    private final Long runId;
    private final Long attemptId;
    private final MeetRestaurantCandidateService candidateService;
    private final MeetPlanPolicyService planPolicyService;
    private final MeetPlanEventService eventService;
    private final int maxToolCalls;

    private Map<Long, MeetPreferenceData> confirmedPreferences;
    private final Set<String> completedToolNames = new LinkedHashSet<>();
    private final Set<Long> inspectedCandidateIds = new LinkedHashSet<>();
    private final Set<String> validPlanFingerprints = new LinkedHashSet<>();
    private int toolCallCount;

    @Getter
    private MeetRestaurantCandidateService.CandidateSelection selection;

    public MeetPlanningTools(
            MeetRoom room,
            Long runId,
            Long attemptId,
            MeetRestaurantCandidateService candidateService,
            MeetPlanPolicyService planPolicyService,
            MeetPlanEventService eventService) {
        this(room, runId, attemptId, candidateService, planPolicyService,
                eventService, 8);
    }

    public MeetPlanningTools(
            MeetRoom room,
            Long runId,
            Long attemptId,
            MeetRestaurantCandidateService candidateService,
            MeetPlanPolicyService planPolicyService,
            MeetPlanEventService eventService,
            int maxToolCalls) {
        this.room = room;
        this.runId = runId;
        this.attemptId = attemptId;
        this.candidateService = candidateService;
        this.planPolicyService = planPolicyService;
        this.eventService = eventService;
        this.maxToolCalls = maxToolCalls;
    }

    @Tool(
            name = "read_confirmed_preferences",
            description = "读取当前聚会房间中所有已确认的成员偏好。只能读取确认后的结构化偏好，不能读取或修改草稿。")
    public ConfirmedPreferencesResult readConfirmedPreferences() {
        toolStarted("read_confirmed_preferences", "Agent 正在读取已确认成员偏好");
        try {
            if (confirmedPreferences == null) {
                confirmedPreferences = candidateService
                        .loadConfirmedPreferences(room.getId());
            }
            eventService.append(
                    runId,
                    attemptId,
                    MeetPlanEventType.PREFERENCES_READ,
                    "已读取 " + confirmedPreferences.size()
                            + " 位成员的确认偏好",
                    null);
            toolCompleted(
                    "read_confirmed_preferences",
                    "已读取 " + confirmedPreferences.size() + " 位成员偏好",
                    Map.of("memberCount", confirmedPreferences.size()));
            completedToolNames.add("read_confirmed_preferences");
            Map<Long, PlanningPreferenceView> safePreferences =
                    confirmedPreferences.entrySet().stream().collect(
                            java.util.stream.Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> PlanningPreferenceView.from(
                                            entry.getValue()),
                                    (left, right) -> right,
                                    LinkedHashMap::new));
            return new ConfirmedPreferencesResult(
                    confirmedPreferences.size(),
                    safePreferences);
        } catch (RuntimeException exception) {
            toolFailed("read_confirmed_preferences", exception);
            throw exception;
        }
    }

    @Tool(
            name = "find_feasible_restaurants",
            description = "根据 Java 侧已确认的成员偏好、硬约束和房间搜索半径召回并过滤餐厅。返回的餐厅是唯一允许被方案引用的候选集。")
    public CandidateSearchResult findFeasibleRestaurants() {
        toolStarted("find_feasible_restaurants", "Agent 正在检查硬约束并召回餐厅");
        try {
            requireCompleted("read_confirmed_preferences");
            ensurePreferencesLoaded();
            if (selection == null) {
                selection = candidateService.select(room, confirmedPreferences);
            }
            eventService.append(
                    runId,
                    attemptId,
                    MeetPlanEventType.CONSTRAINTS_CHECKED,
                    "Java 已完成预算、距离、忌口和过敏原硬约束检查",
                    toJson(selection.filteredReasons()));
            eventService.append(
                    runId,
                    attemptId,
                    MeetPlanEventType.RESTAURANTS_RECALLED,
                    "已召回 " + selection.recalledCount() + " 家餐厅",
                    null);
            eventService.append(
                    runId,
                    attemptId,
                    MeetPlanEventType.RESTAURANTS_FILTERED,
                    "硬约束过滤后剩余 " + selection.candidates().size()
                            + " 家餐厅",
                    toJson(selection.filteredReasons()));
            List<CandidateSummary> topCandidates = selection.candidates()
                    .stream()
                    .limit(20)
                    .map(candidate -> new CandidateSummary(
                            candidate.getShopId(),
                            candidate.getName(),
                            candidate.getCuisine(),
                            candidate.getAvgPrice(),
                            candidate.getScore(),
                            candidate.getDistanceMeters(),
                            candidate.getGroupScore()))
                    .toList();
            toolCompleted(
                    "find_feasible_restaurants",
                    "硬约束过滤后剩余 " + selection.candidates().size() + " 家餐厅",
                    Map.of(
                            "recalledCount", selection.recalledCount(),
                            "candidateCount", selection.candidates().size()));
            completedToolNames.add("find_feasible_restaurants");
            return new CandidateSearchResult(
                    selection.recalledCount(),
                    selection.candidates().size(),
                    new LinkedHashMap<>(selection.filteredReasons()),
                    topCandidates);
        } catch (RuntimeException exception) {
            toolFailed("find_feasible_restaurants", exception);
            throw exception;
        }
    }

    @Tool(
            name = "inspect_restaurant_candidates",
            description = "查看候选集中的餐厅详情。只能查询 find_feasible_restaurants 返回的餐厅，最多查询 8 家，不能查询或创造候选集外的餐厅。")
    public CandidateDetailsResult inspectRestaurantCandidates(
            @ToolParam(description = "需要查看的候选餐厅 ID，最多 8 个")
            List<Long> shopIds) {
        toolStarted("inspect_restaurant_candidates", "Agent 正在查看候选餐厅详情");
        try {
            requireCompleted("find_feasible_restaurants");
            ensureSelectionLoaded();
            if (shopIds == null || shopIds.isEmpty()) {
                throw new IllegalArgumentException("至少需要一个候选餐厅 ID");
            }
            if (shopIds.size() > 8) {
                throw new IllegalArgumentException("一次最多查看 8 家候选餐厅");
            }
            Map<Long, MeetRestaurantCandidate> candidateMap = selection.candidates()
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(
                            MeetRestaurantCandidate::getShopId,
                            value -> value));
            List<Long> unknownShopIds = shopIds.stream()
                    .filter(id -> !candidateMap.containsKey(id))
                    .distinct()
                    .toList();
            List<MeetRestaurantCandidate> candidates = shopIds.stream()
                    .distinct()
                    .map(candidateMap::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            candidates.stream()
                    .map(MeetRestaurantCandidate::getShopId)
                    .forEach(inspectedCandidateIds::add);
            toolCompleted(
                    "inspect_restaurant_candidates",
                    "已查看 " + candidates.size() + " 家候选餐厅",
                    Map.of("candidateCount", candidates.size()));
            completedToolNames.add("inspect_restaurant_candidates");
            return new CandidateDetailsResult(candidates, unknownShopIds);
        } catch (RuntimeException exception) {
            toolFailed("inspect_restaurant_candidates", exception);
            throw exception;
        }
    }

    @Tool(
            name = "validate_plan_draft",
            description = "使用 Java 业务规则校验方案草稿：必须恰好三个不同餐厅，且餐厅来自候选集，建议时间、集合地点和理由不能为空。只能返回校验结果，不能保存方案。")
    public PlanValidationResult validatePlanDraft(
            @ToolParam(description = "待校验的三个方案草稿") AiMeetPlanSet planSet) {
        toolStarted("validate_plan_draft", "Java 正在校验 Agent 方案草稿");
        try {
            requireCompleted("inspect_restaurant_candidates");
            ensureSelectionLoaded();
            List<String> errors = planPolicyService.validate(
                    planSet, selection.candidates());
            eventService.append(
                    runId,
                    attemptId,
                    MeetPlanEventType.PLAN_REVIEWED,
                    errors.isEmpty()
                            ? "Java 方案校验通过"
                            : "Java 方案校验发现 " + errors.size() + " 个问题",
                    toJson(Map.of("errorCount", errors.size())));
            toolCompleted(
                    "validate_plan_draft",
                    errors.isEmpty()
                            ? "方案草稿校验通过"
                            : "方案草稿需要修正",
                    Map.of("valid", errors.isEmpty()));
            if (errors.isEmpty()) {
                completedToolNames.add("validate_plan_draft");
                validPlanFingerprints.add(planFingerprint(planSet));
            }
            return new PlanValidationResult(errors.isEmpty(), errors);
        } catch (RuntimeException exception) {
            toolFailed("validate_plan_draft", exception);
            throw exception;
        }
    }

    private void ensurePreferencesLoaded() {
        if (confirmedPreferences == null) {
            confirmedPreferences = candidateService
                    .loadConfirmedPreferences(room.getId());
        }
    }

    private void ensureSelectionLoaded() {
        if (selection == null) {
            ensurePreferencesLoaded();
            selection = candidateService.select(room, confirmedPreferences);
        }
    }

    public MeetRestaurantCandidateService.CandidateSelection preflight() {
        ensureSelectionLoaded();
        return selection;
    }

    public Set<String> completedToolNames() {
        return Set.copyOf(completedToolNames);
    }

    public Set<Long> inspectedCandidateIds() {
        return Set.copyOf(inspectedCandidateIds);
    }

    public boolean wasSuccessfullyValidated(AiMeetPlanSet planSet) {
        return planSet != null
                && validPlanFingerprints.contains(planFingerprint(planSet));
    }

    private void toolStarted(String name, String summary) {
        toolCallCount++;
        if (toolCallCount > maxToolCalls) {
            throw new IllegalStateException(
                    "Agent 工具调用超过上限: " + maxToolCalls);
        }
        eventService.append(
                runId,
                attemptId,
                MeetPlanEventType.TOOL_STARTED,
                summary,
                toJson(Map.of("tool", name)));
    }

    private void toolCompleted(
            String name, String summary, Map<String, Object> payload) {
        Map<String, Object> safePayload = new LinkedHashMap<>(payload);
        safePayload.put("tool", name);
        eventService.append(
                runId,
                attemptId,
                MeetPlanEventType.TOOL_COMPLETED,
                summary,
                toJson(safePayload));
    }

    private void toolFailed(String name, RuntimeException exception) {
        eventService.append(
                runId,
                attemptId,
                MeetPlanEventType.TOOL_FAILED,
                "Agent 工具执行失败：" + name,
                toJson(Map.of(
                        "tool", name,
                        "errorType", exception.getClass().getSimpleName())));
    }

    private String toJson(Object value) {
        return cn.hutool.json.JSONUtil.toJsonStr(value);
    }

    private void requireCompleted(String toolName) {
        if (!completedToolNames.contains(toolName)) {
            throw new IllegalStateException(
                    "必须先完成工具调用: " + toolName);
        }
    }

    private String planFingerprint(AiMeetPlanSet planSet) {
        return cn.hutool.json.JSONUtil.toJsonStr(planSet);
    }

    public record ConfirmedPreferencesResult(
            int memberCount,
            Map<Long, PlanningPreferenceView> preferences) {
    }

    public record PlanningPreferenceView(
            Integer budgetMax,
            List<String> preferredCuisines,
            List<String> avoidFoods,
            Boolean acceptsSpicy,
            Integer maxDistanceMeters,
            String preferredTime,
            List<String> hardConstraintKeys) {

        private static PlanningPreferenceView from(MeetPreferenceData data) {
            return new PlanningPreferenceView(
                    data.getBudgetMax(),
                    safeList(data.getPreferredCuisines()),
                    safeList(data.getAvoidFoods()),
                    data.getAcceptsSpicy(),
                    data.getMaxDistanceMeters(),
                    data.getPreferredTime(),
                    safeList(data.getHardConstraintKeys()));
        }

        private static List<String> safeList(List<String> values) {
            return values == null ? List.of() : List.copyOf(values);
        }
    }

    public record CandidateSearchResult(
            int recalledCount,
            int candidateCount,
            Map<String, Integer> filteredReasons,
            List<CandidateSummary> topCandidates) {
    }

    public record CandidateSummary(
            Long shopId,
            String name,
            String cuisine,
            Long avgPrice,
            Integer score,
            Double distanceMeters,
            Double groupScore) {
    }

    public record CandidateDetailsResult(
            List<MeetRestaurantCandidate> candidates,
            List<Long> unknownShopIds) {
    }

    public record PlanValidationResult(
            boolean valid,
            List<String> errors) {
    }
}
