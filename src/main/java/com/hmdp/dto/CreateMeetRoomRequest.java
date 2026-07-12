package com.hmdp.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateMeetRoomRequest {

    @NotBlank(message = "聚会标题不能为空")
    @Size(max = 100, message = "聚会标题不能超过100个字符")
    private String title;

    @NotNull(message = "搜索中心经度不能为空")
    private Double centerX;

    @NotNull(message = "搜索中心纬度不能为空")
    private Double centerY;

    @Min(value = 500, message = "搜索半径不能小于500米")
    @Max(value = 20000, message = "搜索半径不能超过20000米")
    private Integer searchRadiusMeter = 5000;

    @Min(value = 2, message = "房间至少允许2人")
    @Max(value = 20, message = "房间最多允许20人")
    private Integer maxMembers = 6;

    @Min(value = 1, message = "最少提交人数不能小于1")
    @Max(value = 20, message = "最少提交人数不能超过20")
    private Integer minSubmittedMembers = 2;
}