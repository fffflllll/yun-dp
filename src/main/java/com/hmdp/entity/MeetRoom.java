package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_meet_room")
public class MeetRoom {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long creatorId;

    private String title;

    private String inviteCode;

    private String status;

    private Double centerX;

    private Double centerY;

    private Integer searchRadiusMeter;

    private Integer maxMembers;

    private Integer minSubmittedMembers;

    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}