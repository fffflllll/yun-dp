package com.hmdp.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MeetRoomSummaryVO {
    private Long roomId;
    private Long creatorId;
    private String title;
    private String inviteCode;
    private String status;
    private Integer maxMembers;
    private Integer memberCount;
    private LocalDateTime createTime;
    private List<MeetMemberVO> members;
}
