package com.hmdp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class JoinMeetRoomRequest {

    @NotBlank(message = "邀请码不能为空")
    @Pattern(
            regexp = "^[A-Za-z0-9]{6}$",
            message = "邀请码必须是6位字母或数字"
    )
    private String inviteCode;
}