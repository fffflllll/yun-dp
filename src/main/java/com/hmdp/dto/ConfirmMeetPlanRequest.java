package com.hmdp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfirmMeetPlanRequest {

    @NotNull(message = "请选择要确认的方案")
    private Long proposalId;
}
