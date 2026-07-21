package com.hmdp;

import com.hmdp.dto.AiMeetPlanOption;
import com.hmdp.dto.AiMeetPlanSet;
import com.hmdp.dto.MeetPreferenceData;
import com.hmdp.dto.MeetRestaurantCandidate;
import com.hmdp.entity.MeetRoom;
import com.hmdp.service.MeetPlanEventService;
import com.hmdp.service.MeetPlanPolicyService;
import com.hmdp.service.MeetPlanningTools;
import com.hmdp.service.MeetRestaurantCandidateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetPlanningToolsTest {

    @Mock
    private MeetRestaurantCandidateService candidateService;
    @Mock
    private MeetPlanPolicyService policyService;
    @Mock
    private MeetPlanEventService eventService;

    private MeetRoom room;
    private MeetRestaurantCandidate candidate;
    private MeetRestaurantCandidateService.CandidateSelection selection;

    @BeforeEach
    void setUp() {
        room = new MeetRoom();
        room.setId(1L);
        candidate = MeetRestaurantCandidate.builder()
                .shopId(10L)
                .name("候选餐厅")
                .address("候选地址")
                .build();
        selection = new MeetRestaurantCandidateService.CandidateSelection(
                1, List.of(candidate), Map.of(),
                Map.of(7L, new MeetPreferenceData()));
    }

    @Test
    void rejectsToolCallsThatSkipRequiredPredecessor() {
        MeetPlanningTools tools = tools(8);

        assertThrows(IllegalStateException.class,
                tools::findFeasibleRestaurants);
    }

    @Test
    void rejectsCallsBeyondConfiguredToolBudget() {
        when(candidateService.loadConfirmedPreferences(1L))
                .thenReturn(selection.memberPreferences());
        MeetPlanningTools tools = tools(4);

        for (int index = 0; index < 4; index++) {
            tools.readConfirmedPreferences();
        }

        assertThrows(IllegalStateException.class,
                tools::readConfirmedPreferences);
    }

    @Test
    void finalPlanMustMatchASuccessfullyValidatedDraft() {
        when(candidateService.loadConfirmedPreferences(1L))
                .thenReturn(selection.memberPreferences());
        when(candidateService.select(room, selection.memberPreferences()))
                .thenReturn(selection);
        AiMeetPlanSet planSet = planSet();
        when(policyService.validate(planSet, selection.candidates()))
                .thenReturn(List.of());
        MeetPlanningTools tools = tools(8);

        tools.readConfirmedPreferences();
        tools.findFeasibleRestaurants();
        tools.inspectRestaurantCandidates(List.of(10L));
        tools.validatePlanDraft(planSet);

        assertTrue(tools.wasSuccessfullyValidated(planSet));
        planSet.getPlans().get(0).setReasoning("校验后被修改");
        assertFalse(tools.wasSuccessfullyValidated(planSet));
    }

    private MeetPlanningTools tools(int maxToolCalls) {
        return new MeetPlanningTools(
                room, 2L, 3L, candidateService, policyService,
                eventService, maxToolCalls);
    }

    private AiMeetPlanSet planSet() {
        AiMeetPlanOption option = new AiMeetPlanOption();
        option.setShopId(10L);
        option.setSuggestedTime("周六 18:30");
        option.setMeetingPoint("候选地址");
        option.setReasoning("满足已确认偏好");
        AiMeetPlanSet result = new AiMeetPlanSet();
        result.setPlans(new java.util.ArrayList<>(List.of(option)));
        return result;
    }
}
