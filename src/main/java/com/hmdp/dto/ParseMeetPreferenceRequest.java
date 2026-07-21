package com.hmdp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ParseMeetPreferenceRequest {

    @NotBlank(message = "请先描述你的聚会偏好")
    @Size(max = 1000, message = "偏好描述不能超过1000个字符")
    private String rawText;
}
