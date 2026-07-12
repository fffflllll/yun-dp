package com.hmdp.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateMeetRoomResponse {

    private Long roomId;

    private String inviteCode;

    private String status;
}