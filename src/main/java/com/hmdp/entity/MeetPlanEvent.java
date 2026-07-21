package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_meet_plan_event")
public class MeetPlanEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long attemptId;
    private Long sequence;
    private String eventType;
    private String summary;
    private String payloadJson;
    private LocalDateTime createTime;
}
