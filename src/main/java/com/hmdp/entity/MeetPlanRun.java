package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_meet_plan_run")
public class MeetPlanRun {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private String status;
    private Integer currentAttempt;
    private Integer clarificationCount;
    private Long selectedProposalId;
    private String errorCode;
    private String errorMessage;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime finishedAt;
}
