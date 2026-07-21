package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_meet_proposal")
public class MeetProposal {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long attemptId;
    private Integer proposalRank;
    private Boolean recommended;
    private Long shopId;
    private String suggestedTime;
    private String meetingPoint;
    private Long estimatedPerCapita;
    private String reasoning;
    private String satisfiedJson;
    private String tradeoffsJson;
    private LocalDateTime createTime;
}
