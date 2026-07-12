package com.hmdp.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MeetMemberVO {

    private Long userId;

    private String role;

    private String status;

    private String preferenceStatus;

    private LocalDateTime joinTime;
}