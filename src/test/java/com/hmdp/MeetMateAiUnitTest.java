package com.hmdp;

import com.hmdp.config.MeetAiProperties;
import com.hmdp.dto.AiMeetClarification;
import com.hmdp.dto.AiMeetPlanOption;
import com.hmdp.dto.AiMeetPlanSet;
import com.hmdp.dto.MeetPreferenceData;
import com.hmdp.dto.MeetRestaurantCandidate;
import com.hmdp.entity.MeetRoom;
import com.hmdp.service.MeetPlanningAiService;
import com.hmdp.service.MeetPreferenceAiService;
import com.hmdp.service.MeetPlanPolicyService;
import com.hmdp.service.MeetPlanningTools;
import org.springframework.ai.support.ToolCallbacks;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MeetMateAiUnitTest {

    @Test
    void preferenceFallbackCreatesEditableDraftWithoutAi() {
        MeetAiProperties properties = new MeetAiProperties();
        properties.setEnabled(false);
        MeetPreferenceAiService service =
                new MeetPreferenceAiService(null, properties);

        MeetPreferenceAiService.PreferenceParseOutcome outcome = service.parse(
                "周六晚餐，人均 120 元以内，2 公里内，不吃辣，想吃火锅");

        MeetPreferenceData data = outcome.preference();
        assertFalse(outcome.aiParsed());
        assertEquals(120, data.getBudgetMax());
        assertEquals(2000, data.getMaxDistanceMeters());
        assertEquals(Boolean.FALSE, data.getAcceptsSpicy());
        assertEquals(List.of("火锅"), data.getPreferredCuisines());
        assertEquals(List.of("BUDGET_MAX", "MAX_DISTANCE", "ACCEPTS_SPICY"),
                data.getHardConstraintKeys());
    }

    @Test
    void deterministicPlanningReturnsThreeOrderedPlans() {
        MeetAiProperties properties = new MeetAiProperties();
        properties.setEnabled(false);
        MeetPlanningAiService service =
                new MeetPlanningAiService(null, properties);
        MeetPreferenceData preference = new MeetPreferenceData();
        preference.setPreferredTime("周六 18:30");
        List<MeetRestaurantCandidate> candidates = List.of(
                candidate(1L, "一号店"),
                candidate(2L, "二号店"),
                candidate(3L, "三号店"),
                candidate(4L, "四号店"));
        MeetRestaurantCandidateServiceSelection selection =
                new MeetRestaurantCandidateServiceSelection(candidates,
                        Map.of(7L, preference));

        AiMeetPlanSet planSet = service.generatePlans(new MeetRoom(),
                selection.value());

        assertEquals(3, planSet.getPlans().size());
        assertEquals(List.of(1L, 2L, 3L), planSet.getPlans().stream()
                .map(plan -> plan.getShopId()).toList());
        assertEquals("周六 18:30", planSet.getPlans().get(0).getSuggestedTime());
    }

    @Test
    void clarificationTargetsLowestBudgetHardConstraint() {
        MeetAiProperties properties = new MeetAiProperties();
        properties.setEnabled(false);
        MeetPlanningAiService service =
                new MeetPlanningAiService(null, properties);
        MeetPreferenceData relaxed = new MeetPreferenceData();
        relaxed.setBudgetMax(300);
        relaxed.setHardConstraintKeys(List.of("BUDGET_MAX"));
        MeetPreferenceData strict = new MeetPreferenceData();
        strict.setBudgetMax(100);
        strict.setHardConstraintKeys(List.of("BUDGET_MAX"));
        com.hmdp.service.MeetRestaurantCandidateService.CandidateSelection selection =
                new com.hmdp.service.MeetRestaurantCandidateService.CandidateSelection(
                        2,
                        List.of(),
                        Map.of("超过成员硬预算", 2),
                        new LinkedHashMap<>(Map.of(11L, relaxed, 22L, strict)));
        AiMeetClarification clarification =
                service.createClarification(selection);

        assertNotNull(clarification);
        assertEquals(22L, clarification.getTargetUserId());
        assertEquals("BUDGET_MAX", clarification.getConstraintKey());
        assertEquals(List.of("RELAX_TO_SOFT", "CANCEL_PLAN"),
                clarification.getOptions());
    }

    @Test
    void clarificationIsAbsentWhenNoSafeRelaxationExists() {
        MeetAiProperties properties = new MeetAiProperties();
        properties.setEnabled(false);
        MeetPlanningAiService service =
                new MeetPlanningAiService(null, properties);
        MeetRestaurantCandidateServiceSelection selection =
                new MeetRestaurantCandidateServiceSelection(List.of(), Map.of());

        assertNull(service.createClarification(selection.value()));
    }

    @Test
    void planningToolsExposeOnlyReadAndValidationActions() {
        MeetPlanningTools tools = new MeetPlanningTools(
                new MeetRoom(),
                1L,
                1L,
                null,
                null,
                null);

        Set<String> toolNames = java.util.Arrays.stream(
                        ToolCallbacks.from(tools))
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "read_confirmed_preferences",
                "find_feasible_restaurants",
                "inspect_restaurant_candidates",
                "validate_plan_draft"), toolNames);
    }

    @Test
    void planPolicyRejectsModelInventedRestaurant() {
        MeetPlanPolicyService policy = new MeetPlanPolicyService();
        AiMeetPlanSet planSet = new AiMeetPlanSet();
        for (long shopId = 1; shopId <= 3; shopId++) {
            AiMeetPlanOption option = new AiMeetPlanOption();
            option.setShopId(shopId);
            option.setSuggestedTime("周六 18:30");
            option.setMeetingPoint("地址");
            option.setReasoning("理由");
            planSet.getPlans().add(option);
        }

        assertFalse(policy.validate(planSet, List.of(
                candidate(1L, "一号店"),
                candidate(2L, "二号店"),
                candidate(4L, "四号店"))).isEmpty());
    }

    private MeetRestaurantCandidate candidate(Long id, String name) {
        return MeetRestaurantCandidate.builder()
                .shopId(id)
                .name(name)
                .address(name + " 地址")
                .build();
    }

    private record MeetRestaurantCandidateServiceSelection(
            List<MeetRestaurantCandidate> candidates,
            Map<Long, MeetPreferenceData> preferences) {
        private com.hmdp.service.MeetRestaurantCandidateService.CandidateSelection value() {
            return new com.hmdp.service.MeetRestaurantCandidateService.CandidateSelection(
                    candidates.size(), candidates, Map.of(), preferences);
        }
    }
}
