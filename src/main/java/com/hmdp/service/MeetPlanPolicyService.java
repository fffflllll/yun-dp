package com.hmdp.service;

import com.hmdp.dto.AiMeetPlanOption;
import com.hmdp.dto.AiMeetPlanSet;
import com.hmdp.dto.MeetRestaurantCandidate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Business validation for model-generated plan drafts.
 *
 * <p>The model can suggest a plan, but only this policy service can decide
 * whether it is based on the Java-owned candidate set.</p>
 */
@Service
public class MeetPlanPolicyService {

    public List<String> validate(
            AiMeetPlanSet planSet,
            List<MeetRestaurantCandidate> candidates) {
        List<String> errors = new ArrayList<>();
        if (planSet == null || planSet.getPlans() == null) {
            return List.of("模型没有返回方案列表");
        }
        if (planSet.getPlans().size() != 3) {
            errors.add("模型必须返回恰好三个方案");
        }

        Map<Long, MeetRestaurantCandidate> candidateMap = candidates == null
                ? Map.of()
                : candidates.stream()
                .collect(Collectors.toMap(
                        MeetRestaurantCandidate::getShopId,
                        candidate -> candidate));
        Set<Long> selectedShopIds = new HashSet<>();
        for (AiMeetPlanOption option : planSet.getPlans()) {
            if (option == null || option.getShopId() == null) {
                errors.add("方案缺少餐厅 ID");
                continue;
            }
            MeetRestaurantCandidate candidate = candidateMap.get(
                    option.getShopId());
            if (candidate == null) {
                errors.add("方案引用了候选集外餐厅: " + option.getShopId());
            }
            if (!selectedShopIds.add(option.getShopId())) {
                errors.add("三个方案不能使用重复餐厅: " + option.getShopId());
            }
            if (option.getSuggestedTime() == null
                    || option.getSuggestedTime().isBlank()) {
                errors.add("方案缺少建议时间: " + option.getShopId());
            } else if (option.getSuggestedTime().length() > 100) {
                errors.add("方案建议时间过长: " + option.getShopId());
            }
            if (option.getMeetingPoint() == null
                    || option.getMeetingPoint().isBlank()) {
                errors.add("方案缺少集合地点: " + option.getShopId());
            } else if (option.getMeetingPoint().length() > 255) {
                errors.add("方案集合地点过长: " + option.getShopId());
            } else if (candidate != null
                    && candidate.getAddress() != null
                    && !candidate.getAddress().isBlank()
                    && !candidate.getAddress().trim().equals(
                    option.getMeetingPoint().trim())) {
                errors.add("集合地点必须使用候选餐厅地址: "
                        + option.getShopId());
            }
            if (option.getReasoning() == null
                    || option.getReasoning().isBlank()) {
                errors.add("方案缺少推荐理由: " + option.getShopId());
            } else if (option.getReasoning().length() > 1000) {
                errors.add("方案推荐理由过长: " + option.getShopId());
            }
            validateTextList(errors, option.getShopId(), "满足偏好",
                    option.getSatisfiedPreferences());
            validateTextList(errors, option.getShopId(), "取舍说明",
                    option.getTradeoffs());
        }
        return errors;
    }

    public void assertValid(
            AiMeetPlanSet planSet,
            List<MeetRestaurantCandidate> candidates) {
        List<String> errors = validate(planSet, candidates);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("；", errors));
        }
    }

    private void validateTextList(
            List<String> errors,
            Long shopId,
            String field,
            List<String> values) {
        if (values == null) {
            return;
        }
        if (values.size() > 10) {
            errors.add(field + "不能超过10项: " + shopId);
        }
        if (values.stream().anyMatch(value -> value == null
                || value.isBlank() || value.length() > 200)) {
            errors.add(field + "包含空值或超长文本: " + shopId);
        }
    }
}
