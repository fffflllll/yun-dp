package com.hmdp.service;

import com.hmdp.dto.ConfirmMeetPreferenceRequest;
import com.hmdp.dto.ParseMeetPreferenceRequest;
import com.hmdp.dto.Result;

public interface IMeetPreferenceService {
    Result parse(Long roomId, ParseMeetPreferenceRequest request);
    Result confirm(Long roomId, ConfirmMeetPreferenceRequest request);
    Result getMine(Long roomId);
}
