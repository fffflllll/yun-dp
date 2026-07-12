package com.hmdp.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MeetMemberVO {

    private Long userId;

    private String nickName;

    private String icon;

    private String role;

    private String status;

    private String preferenceStatus;

    private LocalDateTime joinTime;
}