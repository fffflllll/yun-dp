package com.hmdp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfirmMeetPreferenceRequest {

    @Valid
    @NotNull(message = "结构化偏好不能为空")
    private MeetPreferenceData preference;
}
