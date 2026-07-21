package com.hmdp;

import com.hmdp.dto.AiMeetPlanOption;
import com.hmdp.dto.AiMeetPlanSet;
import com.hmdp.dto.MeetRestaurantCandidate;
import com.hmdp.service.MeetPlanPolicyService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetPlanPolicyServiceTest {

    private final MeetPlanPolicyService policy = new MeetPlanPolicyService();

    @Test
    void acceptsExactlyThreeCompleteDistinctCandidatePlans() {
        assertTrue(policy.validate(validPlanSet(), candidates()).isEmpty());
        assertDoesNotThrow(() -> policy.assertValid(validPlanSet(), candidates()));
    }

    @Test
    void rejectsMissingPlanSetOrPlanList() {
        assertEquals(List.of("模型没有返回方案列表"),
                policy.validate(null, candidates()));

        AiMeetPlanSet planSet = new AiMeetPlanSet();
        planSet.setPlans(null);
        assertEquals(List.of("模型没有返回方案列表"),
                policy.validate(planSet, candidates()));
    }

    @Test
    void rejectsAnyPlanCountOtherThanThree() {
        AiMeetPlanSet planSet = validPlanSet();
        planSet.getPlans().remove(2);

        assertTrue(policy.validate(planSet, candidates()).contains(
                "模型必须返回恰好三个方案"));
    }

    @Test
    void rejectsNullPlanAndMissingShopId() {
        AiMeetPlanSet planSet = validPlanSet();
        planSet.getPlans().set(0, null);
        planSet.getPlans().get(1).setShopId(null);

        assertEquals(2, policy.validate(planSet, candidates()).stream()
                .filter("方案缺少餐厅 ID"::equals)
                .count());
    }

    @Test
    void rejectsRestaurantOutsideCandidateSet() {
        AiMeetPlanSet planSet = validPlanSet();
        planSet.getPlans().get(2).setShopId(99L);

        assertTrue(policy.validate(planSet, candidates()).contains(
                "方案引用了候选集外餐厅: 99"));
    }

    @Test
    void rejectsDuplicateRestaurants() {
        AiMeetPlanSet planSet = validPlanSet();
        planSet.getPlans().get(2).setShopId(1L);
        planSet.getPlans().get(2).setMeetingPoint("一号店 地址");

        assertTrue(policy.validate(planSet, candidates()).contains(
                "三个方案不能使用重复餐厅: 1"));
    }

    @Test
    void rejectsBlankSuggestedTimeMeetingPointAndReasoning() {
        AiMeetPlanSet planSet = validPlanSet();
        AiMeetPlanOption option = planSet.getPlans().get(0);
        option.setSuggestedTime(" ");
        option.setMeetingPoint(" ");
        option.setReasoning(" ");

        List<String> errors = policy.validate(planSet, candidates());
        assertTrue(errors.contains("方案缺少建议时间: 1"));
        assertTrue(errors.contains("方案缺少集合地点: 1"));
        assertTrue(errors.contains("方案缺少推荐理由: 1"));
    }

    @Test
    void rejectsMeetingPointDifferentFromCandidateAddress() {
        AiMeetPlanSet planSet = validPlanSet();
        planSet.getPlans().get(0).setMeetingPoint("模型编造的地址");

        assertTrue(policy.validate(planSet, candidates()).contains(
                "集合地点必须使用候选餐厅地址: 1"));
    }

    @Test
    void trimsCandidateAddressWhenComparingMeetingPoint() {
        AiMeetPlanSet planSet = validPlanSet();
        planSet.getPlans().get(0).setMeetingPoint("  一号店 地址  ");

        assertTrue(policy.validate(planSet, candidates()).isEmpty());
    }

    @Test
    void treatsNullCandidatesAsEmptyCandidateSet() {
        List<String> errors = policy.validate(validPlanSet(), null);

        assertEquals(3, errors.stream()
                .filter(error -> error.startsWith("方案引用了候选集外餐厅:"))
                .count());
    }

    @Test
    void assertValidReportsAllDetectedErrors() {
        AiMeetPlanSet planSet = validPlanSet();
        AiMeetPlanOption option = planSet.getPlans().get(0);
        option.setShopId(99L);
        option.setSuggestedTime(null);
        option.setReasoning(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> policy.assertValid(planSet, candidates()));
        assertTrue(exception.getMessage().contains("方案引用了候选集外餐厅: 99"));
        assertTrue(exception.getMessage().contains("方案缺少建议时间: 99"));
        assertTrue(exception.getMessage().contains("方案缺少推荐理由: 99"));
    }

    private AiMeetPlanSet validPlanSet() {
        AiMeetPlanSet planSet = new AiMeetPlanSet();
        planSet.setPlans(new ArrayList<>(List.of(
                plan(1L, "一号店 地址"),
                plan(2L, "二号店 地址"),
                plan(3L, "三号店 地址"))));
        return planSet;
    }

    private AiMeetPlanOption plan(Long shopId, String address) {
        AiMeetPlanOption option = new AiMeetPlanOption();
        option.setShopId(shopId);
        option.setSuggestedTime("周六 18:30");
        option.setMeetingPoint(address);
        option.setReasoning("通过硬约束并兼顾群体偏好");
        return option;
    }

    private List<MeetRestaurantCandidate> candidates() {
        return List.of(
                candidate(1L, "一号店 地址"),
                candidate(2L, "二号店 地址"),
                candidate(3L, "三号店 地址"));
    }

    private MeetRestaurantCandidate candidate(Long shopId, String address) {
        return MeetRestaurantCandidate.builder()
                .shopId(shopId)
                .address(address)
                .build();
    }
}
