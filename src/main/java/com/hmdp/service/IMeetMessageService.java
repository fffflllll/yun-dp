package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.SendMeetMessageRequest;

public interface IMeetMessageService {

    Result list(Long roomId, Long after);

    Result send(Long roomId, SendMeetMessageRequest request);
}
