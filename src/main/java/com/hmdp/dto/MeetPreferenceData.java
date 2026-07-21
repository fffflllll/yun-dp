package com.hmdp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MeetPreferenceData {

    @Min(value = 1, message = "预算必须大于0")
    @Max(value = 10000, message = "预算不能超过10000元")
    private Integer budgetMax;

    @NotNull
    @Size(max = 10, message = "偏好菜系不能超过10项")
    private List<@Size(max = 64, message = "菜系名称不能超过64个字符") String>
            preferredCuisines = new ArrayList<>();

    @NotNull
    @Size(max = 10, message = "忌口不能超过10项")
    private List<@Size(max = 64, message = "忌口内容不能超过64个字符") String>
            avoidFoods = new ArrayList<>();

    @NotNull
    @Size(max = 10, message = "过敏原不能超过10项")
    private List<@Size(max = 64, message = "过敏原不能超过64个字符") String>
            allergens = new ArrayList<>();
    private Boolean acceptsSpicy;

    @Min(value = 100, message = "距离不能小于100米")
    @Max(value = 50000, message = "距离不能超过50000米")
    private Integer maxDistanceMeters;

    @Size(max = 100, message = "偏好时间不能超过100个字符")
    private String preferredTime;

    @NotNull
    @Size(max = 5, message = "硬约束不能超过5项")
    private List<@Pattern(
            regexp = "BUDGET_MAX|ALLERGENS|ACCEPTS_SPICY|MAX_DISTANCE",
            message = "存在不支持的硬约束") String> hardConstraintKeys =
            new ArrayList<>();

    @NotNull
    @Size(max = 5, message = "补充说明不能超过5项")
    private List<@Size(max = 200, message = "单条补充说明不能超过200个字符") String>
            notes = new ArrayList<>();
}
