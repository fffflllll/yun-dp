package com.hmdp.service;

import com.hmdp.dto.AnswerMeetClarificationRequest;
import com.hmdp.dto.ConfirmMeetPlanRequest;
import com.hmdp.dto.Result;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IMeetPlanService {
    Result start(Long roomId);
    Result getRun(Long runId);
    Result answerClarification(
            Long runId, Long clarificationId,
            AnswerMeetClarificationRequest request);
    Result confirmPlan(Long runId, ConfirmMeetPlanRequest request);
    SseEmitter subscribe(Long runId, long afterSequence);
}
