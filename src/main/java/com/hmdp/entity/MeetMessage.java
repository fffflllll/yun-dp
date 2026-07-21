package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_meet_message")
public class MeetMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long roomId;
    private Long userId;
    private String content;
    private LocalDateTime createTime;
}
