package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_meet_clarification")
public class MeetClarification {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long targetUserId;
    private String constraintKey;
    private String question;
    private String optionsJson;
    private String status;
    private String answer;
    private LocalDateTime createTime;
    private LocalDateTime answeredAt;
}
