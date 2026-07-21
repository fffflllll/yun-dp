package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_shop_meet_meta")
public class ShopMeetMeta {

    @TableId("shop_id")
    private Long shopId;
    private String cuisine;
    private String tagsJson;
    private Integer spicyLevel;
    private String allergenTagsJson;
    private String source;
    private Integer confidence;
    private LocalDateTime updateTime;
}
