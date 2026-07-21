package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_meet_preference")
public class MeetPreference {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private Long userId;
    private String rawText;
    private String draftJson;
    private String confirmedJson;
    private String status;
    private String parserVersion;
    private Integer version;
    private LocalDateTime confirmedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
