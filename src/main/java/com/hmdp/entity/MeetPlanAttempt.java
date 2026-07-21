package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_meet_plan_attempt")
public class MeetPlanAttempt {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Integer attemptNo;
    private String status;
    private String dispatchStatus;
    private Integer dispatchAttempts;
    private String model;
    private String promptVersion;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime nextDispatchAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
