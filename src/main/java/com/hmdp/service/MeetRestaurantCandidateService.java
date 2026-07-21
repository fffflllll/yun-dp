package com.hmdp.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.MeetPreferenceData;
import com.hmdp.dto.MeetRestaurantCandidate;
import com.hmdp.entity.MeetPreference;
import com.hmdp.entity.MeetRoom;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopMeetMeta;
import com.hmdp.enums.MeetPreferenceStatus;
import com.hmdp.mapper.MeetPreferenceMapper;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.ShopMeetMetaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetRestaurantCandidateService {

    private static final double EARTH_RADIUS_METERS = 6_371_000D;

    private final ShopMapper shopMapper;
    private final ShopMeetMetaMapper metaMapper;
    private final MeetPreferenceMapper preferenceMapper;

    public CandidateSelection select(MeetRoom room) {
        return select(room, loadConfirmedPreferences(room.getId()));
    }

    public Map<Long, MeetPreferenceData> loadConfirmedPreferences(Long roomId) {
        List<MeetPreference> preferences = preferenceMapper.selectList(
                new LambdaQueryWrapper<MeetPreference>()
                        .eq(MeetPreference::getRoomId, roomId)
                        .eq(MeetPreference::getStatus,
                                MeetPreferenceStatus.CONFIRMED.name())
        );
        return preferences.stream().collect(Collectors.toMap(
                MeetPreference::getUserId,
                item -> item.getConfirmedJson() == null
                        ? new MeetPreferenceData()
                        : JSONUtil.toBean(
                                item.getConfirmedJson(),
                                MeetPreferenceData.class),
                (left, right) -> right,
                LinkedHashMap::new));
    }

    public CandidateSelection select(
            MeetRoom room,
            Map<Long, MeetPreferenceData> memberPreferences) {
        Map<Long, MeetPreferenceData> safePreferences = memberPreferences == null
                ? Map.of()
                : memberPreferences;

        List<Shop> recalled = shopMapper.selectList(
                new LambdaQueryWrapper<Shop>()
                        .eq(Shop::getTypeId, 1L)
        );
        List<Long> shopIds = recalled.stream().map(Shop::getId).toList();
        Map<Long, ShopMeetMeta> metas = shopIds.isEmpty()
                ? Map.of()
                : metaMapper.selectBatchIds(shopIds).stream()
                .collect(Collectors.toMap(
                        ShopMeetMeta::getShopId,
                        Function.identity()));

        Map<String, Integer> filteredReasons = new LinkedHashMap<>();
        List<MeetRestaurantCandidate> candidates = new ArrayList<>();
        for (Shop shop : recalled) {
            ShopMeetMeta meta = metas.get(shop.getId());
            double distance = distanceMeters(
                    room.getCenterY(), room.getCenterX(),
                    shop.getY(), shop.getX());
            String rejected = rejectionReason(
                    room, shop, meta, distance,
                    safePreferences.values());
            if (rejected != null) {
                filteredReasons.merge(rejected, 1, Integer::sum);
                continue;
            }

            double groupScore = score(
                    shop, meta, distance,
                    safePreferences.values());
            candidates.add(MeetRestaurantCandidate.builder()
                    .shopId(shop.getId())
                    .name(shop.getName())
                    .cuisine(meta == null ? "未标注" : meta.getCuisine())
                    .area(shop.getArea())
                    .address(shop.getAddress())
                    .avgPrice(shop.getAvgPrice())
                    .score(shop.getScore())
                    .openHours(shop.getOpenHours())
                    .spicyLevel(meta == null ? null : meta.getSpicyLevel())
                    .distanceMeters(Math.round(distance * 10D) / 10D)
                    .groupScore(Math.round(groupScore * 10D) / 10D)
                    .build());
        }

        candidates.sort(Comparator.comparing(
                MeetRestaurantCandidate::getGroupScore).reversed());
        return new CandidateSelection(
                recalled.size(), candidates, filteredReasons,
                safePreferences);
    }

    private String rejectionReason(
            MeetRoom room,
            Shop shop,
            ShopMeetMeta meta,
            double distance,
            Iterable<MeetPreferenceData> preferences) {
        if (distance > room.getSearchRadiusMeter()) {
            return "超出房间搜索半径";
        }

        for (MeetPreferenceData preference : preferences) {
            Set<String> hard = new HashSet<>(
                    preference.getHardConstraintKeys() == null
                            ? List.of()
                            : preference.getHardConstraintKeys());
            if (hard.contains("BUDGET_MAX")
                    && preference.getBudgetMax() != null) {
                if (shop.getAvgPrice() == null) {
                    return "餐厅均价信息缺失";
                }
                if (shop.getAvgPrice() > preference.getBudgetMax()) {
                    return "超过成员硬预算";
                }
            }
            if (hard.contains("MAX_DISTANCE")
                    && preference.getMaxDistanceMeters() != null
                    && distance > preference.getMaxDistanceMeters()) {
                return "超过成员最大距离";
            }
            if (hard.contains("ACCEPTS_SPICY")
                    && Boolean.FALSE.equals(preference.getAcceptsSpicy())
                    && (meta == null || meta.getSpicyLevel() == null
                    || meta.getSpicyLevel() > 1)) {
                return "辣度不符合硬约束";
            }
            if (hard.contains("ALLERGENS")
                    && preference.getAllergens() != null
                    && !preference.getAllergens().isEmpty()) {
                if (meta == null || meta.getAllergenTagsJson() == null) {
                    return "过敏原信息缺失";
                }
                Set<String> shopAllergens = JSONUtil.toList(
                                meta.getAllergenTagsJson(), String.class)
                        .stream()
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());
                boolean conflicts = preference.getAllergens().stream()
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .anyMatch(shopAllergens::contains);
                if (conflicts) {
                    return "存在成员过敏原";
                }
            }
        }
        return null;
    }

    private double score(
            Shop shop,
            ShopMeetMeta meta,
            double distance,
            Iterable<MeetPreferenceData> preferences) {
        double result = shop.getScore() == null ? 30D : shop.getScore();
        result -= Math.min(distance / 1000D, 10D) * 1.5D;
        for (MeetPreferenceData preference : preferences) {
            if (preference.getBudgetMax() != null
                    && shop.getAvgPrice() != null
                    && shop.getAvgPrice() <= preference.getBudgetMax()) {
                result += 3D;
            }
            if (meta != null && meta.getCuisine() != null
                    && preference.getPreferredCuisines() != null
                    && preference.getPreferredCuisines().stream()
                    .anyMatch(value -> meta.getCuisine().contains(value))) {
                result += 6D;
            }
        }
        return result;
    }

    private double distanceMeters(
            Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return Double.MAX_VALUE;
        }
        double latitudeDelta = Math.toRadians(lat2 - lat1);
        double longitudeDelta = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(longitudeDelta / 2)
                * Math.sin(longitudeDelta / 2);
        return EARTH_RADIUS_METERS
                * 2D * Math.atan2(Math.sqrt(a), Math.sqrt(1D - a));
    }

    public record CandidateSelection(
            int recalledCount,
            List<MeetRestaurantCandidate> candidates,
            Map<String, Integer> filteredReasons,
            Map<Long, MeetPreferenceData> memberPreferences) {
    }
}
