package com.hmdp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.config.QueueConfig;
import com.hmdp.dto.MeetPlanAttemptMessage;
import com.hmdp.entity.MeetPlanAttempt;
import com.hmdp.enums.MeetPlanAttemptStatus;
import com.hmdp.enums.MeetPlanDispatchStatus;
import com.hmdp.mapper.MeetPlanAttemptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetPlanAttemptDispatcher {

    private final MeetPlanAttemptMapper attemptMapper;
    private final RabbitTemplate rabbitTemplate;
    private final MeetPlanExecutionService executionService;

    @Value("${meetmate.plan.running-timeout-ms:180000}")
    private long runningTimeoutMillis;

    @Scheduled(fixedDelayString = "${meetmate.plan.dispatch-interval:1000}")
    public void dispatchWhenRequested() {
        dispatchPending();
    }

    @Scheduled(
            fixedDelayString = "${meetmate.plan.recovery-interval:30000}",
            initialDelayString = "${meetmate.plan.recovery-interval:30000}")
    public void recoverStaleDispatches() {
        LocalDateTime now = LocalDateTime.now();
        int resetDispatches = attemptMapper.resetStaleDispatches(
                now.minusSeconds(30), now);
        long timeoutMillis = Math.max(1_000L, runningTimeoutMillis);
        int timedOutRuns = executionService.recoverTimedOutAttempts(
                now.minus(Duration.ofMillis(timeoutMillis)), now);
        if (resetDispatches > 0 || timedOutRuns > 0) {
            log.info("规划任务恢复完成, resetDispatches={}, timedOutRuns={}",
                    resetDispatches, timedOutRuns);
        }
    }

    public void requestDispatch() {
        // Durable polling is the source of truth; callers need no in-memory wake-up.
    }

    private void dispatchPending() {
        LocalDateTime now = LocalDateTime.now();
        List<MeetPlanAttempt> attempts = attemptMapper.selectList(
                new LambdaQueryWrapper<MeetPlanAttempt>()
                        .eq(MeetPlanAttempt::getStatus,
                                MeetPlanAttemptStatus.QUEUED.name())
                        .eq(MeetPlanAttempt::getDispatchStatus,
                                MeetPlanDispatchStatus.PENDING.name())
                        .and(wrapper -> wrapper
                                .isNull(MeetPlanAttempt::getNextDispatchAt)
                                .or()
                                .le(MeetPlanAttempt::getNextDispatchAt, now))
                        .orderByAsc(MeetPlanAttempt::getId)
                        .last("limit 20"));

        for (MeetPlanAttempt attempt : attempts) {
            if (attemptMapper.claimForDispatch(attempt.getId(), now) == 0) {
                continue;
            }
            try {
                rabbitTemplate.convertAndSend(
                        QueueConfig.MEET_PLAN_EXCHANGE,
                        QueueConfig.MEET_PLAN_ROUTING_KEY,
                        new MeetPlanAttemptMessage(
                                attempt.getRunId(), attempt.getId()));
            } catch (RuntimeException exception) {
                log.warn("规划尝试投递失败, attemptId={}",
                        attempt.getId(), exception);
                LocalDateTime failedAt = LocalDateTime.now();
                attemptMapper.releaseDispatch(
                        attempt.getId(), failedAt.plusSeconds(5), failedAt);
            }
        }
    }
}
