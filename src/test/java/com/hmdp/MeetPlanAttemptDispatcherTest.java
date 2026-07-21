package com.hmdp;

import com.hmdp.config.QueueConfig;
import com.hmdp.dto.MeetPlanAttemptMessage;
import com.hmdp.entity.MeetPlanAttempt;
import com.hmdp.mapper.MeetPlanAttemptMapper;
import com.hmdp.service.MeetPlanAttemptDispatcher;
import com.hmdp.service.MeetPlanExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetPlanAttemptDispatcherTest {

    @Mock
    private MeetPlanAttemptMapper attemptMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private MeetPlanExecutionService executionService;

    private MeetPlanAttemptDispatcher dispatcher;
    private MeetPlanAttempt attempt;

    @BeforeEach
    void setUp() {
        dispatcher = new MeetPlanAttemptDispatcher(
                attemptMapper, rabbitTemplate, executionService);
        attempt = new MeetPlanAttempt();
        attempt.setId(20L);
        attempt.setRunId(10L);
        when(attemptMapper.selectList(any())).thenReturn(List.of(attempt));
    }

    @Test
    void onlyTheDispatcherThatWinsCasPublishesMessage() {
        when(attemptMapper.claimForDispatch(eq(20L), any()))
                .thenReturn(0);

        dispatcher.dispatchWhenRequested();

        verify(rabbitTemplate, never()).convertAndSend(
                any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void publishesIdentifiersAfterClaimingAttempt() {
        when(attemptMapper.claimForDispatch(eq(20L), any()))
                .thenReturn(1);

        dispatcher.dispatchWhenRequested();

        verify(rabbitTemplate).convertAndSend(
                QueueConfig.MEET_PLAN_EXCHANGE,
                QueueConfig.MEET_PLAN_ROUTING_KEY,
                new MeetPlanAttemptMessage(10L, 20L));
    }

    @Test
    void publishFailureReturnsAttemptToPendingState() {
        when(attemptMapper.claimForDispatch(eq(20L), any()))
                .thenReturn(1);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class),
                        any(Object.class));

        dispatcher.dispatchWhenRequested();

        verify(attemptMapper).releaseDispatch(
                eq(20L), any(LocalDateTime.class), any(LocalDateTime.class));
    }
}
