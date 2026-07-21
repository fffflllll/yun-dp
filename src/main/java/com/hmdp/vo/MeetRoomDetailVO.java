package com.hmdp.vo;

import lombok.Builder;
import lombok.Data;

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

    private LocalDateTime createTime;

    private LocalDateTime lockedAt;

    private Long confirmedProposalId;

    private Long latestPlanRunId;

    private String latestPlanRunStatus;

    private List<MeetMemberVO> members;
}
