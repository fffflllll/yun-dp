package com.hmdp.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MeetRoomDetailVO {

    private Long roomId;

    private Long creatorId;

    private String title;

    private String inviteCode;

    private String status;

    private Double centerX;

    private Double centerY;

    private Integer searchRadiusMeter;

    private Integer maxMembers;

    private Integer minSubmittedMembers;

    private LocalDateTime createTime;

    private List<MeetMemberVO> members;
}