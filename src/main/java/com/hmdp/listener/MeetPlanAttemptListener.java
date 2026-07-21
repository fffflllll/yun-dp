package com.hmdp.listener;

import com.hmdp.config.QueueConfig;
import com.hmdp.dto.MeetPlanAttemptMessage;
import com.hmdp.service.MeetPlanExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MeetPlanAttemptListener {

    private final MeetPlanExecutionService executionService;

    @RabbitListener(queues = QueueConfig.MEET_PLAN_QUEUE)
    public void execute(MeetPlanAttemptMessage message) {
        executionService.execute(message.runId(), message.attemptId());
    }
}
