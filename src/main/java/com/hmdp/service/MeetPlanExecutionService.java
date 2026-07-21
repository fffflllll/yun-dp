package com.hmdp.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.AiMeetClarification;
import com.hmdp.dto.AiMeetPlanOption;
import com.hmdp.dto.AiMeetPlanSet;
import com.hmdp.dto.MeetRestaurantCandidate;
import com.hmdp.entity.MeetClarification;
import com.hmdp.entity.MeetPlanAttempt;
import com.hmdp.entity.MeetPlanRun;
import com.hmdp.entity.MeetProposal;
import com.hmdp.entity.MeetRoom;
import com.hmdp.enums.MeetClarificationStatus;
import com.hmdp.enums.MeetPlanAttemptStatus;
import com.hmdp.enums.MeetPlanEventType;
import com.hmdp.enums.MeetPlanRunStatus;
import com.hmdp.enums.MeetRoomStatus;
import com.hmdp.mapper.MeetClarificationMapper;
import com.hmdp.mapper.MeetPlanAttemptMapper;
import com.hmdp.mapper.MeetPlanRunMapper;
import com.hmdp.mapper.MeetProposalMapper;
import com.hmdp.mapper.MeetRoomMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetPlanExecutionService {

    private final MeetPlanRunMapper runMapper;
    private final MeetPlanAttemptMapper attemptMapper;
    private final MeetRoomMapper roomMapper;
    private final MeetProposalMapper proposalMapper;
    private final MeetClarificationMapper clarificationMapper;
    private final MeetPlanningAgent planningAgent;
    private final MeetPlanningAiService planningAiService;
    private final MeetPlanPolicyService planPolicyService;
    private final MeetPlanEventService eventService;
    private final TransactionTemplate transactionTemplate;

    @Value("${spring.ai.openai.chat.options.model:unknown}")
    private String model;

    @Value("${meetmate.plan.max-execution-attempts:2}")
    private int maxExecutionAttempts;

    public void execute(Long runId, Long attemptId) {
        ExecutionContext context = claimExecution(runId, attemptId);
        if (context == null) {
            return;
        }

        try {
            MeetPlanningAgent.PlanningResult planningResult =
                    planningAgent.plan(
                            context.room(), runId, attemptId);
            MeetRestaurantCandidateService.CandidateSelection selection =
                    planningResult.selection();

            if (planningResult.needsClarification()) {
                waitForClarificationOrFail(context, selection);
                return;
            }

            AiMeetPlanSet planSet = planningResult.planSet();
            planPolicyService.assertValid(
                    planSet, selection.candidates());
            if (!persistSuccess(
                    context, planSet, selection.candidates())) {
                log.info("忽略已失效的规划结果, runId={}, attemptId={}",
                        runId, attemptId);
            }
        } catch (RuntimeException exception) {
            log.error("MeetMate 规划执行失败, runId={}, attemptId={}",
                    runId, attemptId, exception);
            String publicMessage = "规划服务暂时不可用";
            boolean transitioned = shouldRetry(context, exception)
                    ? persistRetry(context, "PLAN_EXECUTION_RETRY", publicMessage)
                    : persistFailure(
                            context, "PLAN_EXECUTION_FAILED", publicMessage);
            if (!transitioned) {
                log.info("规划已由其他状态转换处理, runId={}, attemptId={}",
                        runId, attemptId);
            }
        }
    }

    /**
     * Marks attempts that remained RUNNING beyond the configured deadline as
     * failed. The guarded SQL transition keeps a late worker result from
     * overwriting the recovered state.
     */
    public int recoverTimedOutAttempts(
            LocalDateTime staleBefore, LocalDateTime recoveredAt) {
        List<MeetPlanAttempt> staleAttempts = attemptMapper.selectList(
                new LambdaQueryWrapper<MeetPlanAttempt>()
                        .eq(MeetPlanAttempt::getStatus,
                                MeetPlanAttemptStatus.RUNNING.name())
                        .lt(MeetPlanAttempt::getUpdateTime, staleBefore)
                        .orderByAsc(MeetPlanAttempt::getId)
                        .last("limit 20"));

        int recovered = 0;
        for (MeetPlanAttempt attempt : staleAttempts) {
            Boolean transitioned = transactionTemplate.execute(status -> {
                int updated = attemptMapper.transitionTimedOut(
                        attempt.getRunId(), attempt.getId(), staleBefore,
                        "规划执行超时，请重新发起规划", recoveredAt);
                if (updated == 0) {
                    return false;
                }
                eventService.append(
                        attempt.getRunId(), attempt.getId(),
                        MeetPlanEventType.RUN_FAILED,
                        "规划执行超时，可以重新发起规划",
                        JSONUtil.toJsonStr(Map.of(
                                "errorCode", "RUN_TIMED_OUT")));
                return true;
            });
            if (Boolean.TRUE.equals(transitioned)) {
                recovered++;
            }
        }
        return recovered;
    }

    /**
     * Atomically claims the attempt and its run before any remote model call.
     * The event is part of the same short transaction as the claim.
     */
    private ExecutionContext claimExecution(Long runId, Long attemptId) {
        LocalDateTime now = LocalDateTime.now();
        return transactionTemplate.execute(status -> {
            if (attemptMapper.claimForExecution(
                    runId, attemptId, model, now) == 0) {
                return null;
            }

            MeetPlanRun run = runMapper.selectById(runId);
            MeetPlanAttempt attempt = attemptMapper.selectById(attemptId);
            MeetRoom room = run == null
                    ? null
                    : roomMapper.selectById(run.getRoomId());
            if (run == null || attempt == null || room == null
                    || !runId.equals(attempt.getRunId())
                    || !MeetPlanAttemptStatus.RUNNING.name()
                    .equals(attempt.getStatus())
                    || !MeetPlanRunStatus.RUNNING.name()
                    .equals(run.getStatus())
                    || !Objects.equals(
                            run.getCurrentAttempt(), attempt.getAttemptNo())
                    || !MeetRoomStatus.PLANNING.name()
                    .equals(room.getStatus())) {
                throw new IllegalStateException(
                        "规划尝试领取后状态校验失败");
            }
            eventService.append(
                    runId,
                    attemptId,
                    MeetPlanEventType.RUN_STARTED,
                    "Agent 开始读取已确认偏好",
                    null);
            return new ExecutionContext(run, attempt, room);
        });
    }

    private void waitForClarificationOrFail(
            ExecutionContext context,
            MeetRestaurantCandidateService.CandidateSelection selection) {
        if (context.run().getClarificationCount() >= 1) {
            persistFailure(context, "NO_FEASIBLE_PLAN",
                    "一次澄清后仍不足三个可行餐厅方案");
            return;
        }

        AiMeetClarification aiClarification =
                planningAiService.createClarification(selection);
        if (aiClarification == null) {
            persistFailure(context, "NO_SAFE_RELAXATION",
                    "没有可以安全放宽的硬约束");
            return;
        }

        if (!persistWaitingForClarification(context, aiClarification)) {
            log.info("忽略已失效的澄清结果, runId={}, attemptId={}",
                    context.run().getId(), context.attempt().getId());
        }
    }

    private boolean persistWaitingForClarification(
            ExecutionContext context,
            AiMeetClarification aiClarification) {
        Boolean persisted = transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            if (attemptMapper.transitionToWaitingInput(
                    context.run().getId(),
                    context.attempt().getId(),
                    now) == 0) {
                return false;
            }

            MeetClarification clarification = new MeetClarification();
            clarification.setRunId(context.run().getId());
            clarification.setTargetUserId(
                    aiClarification.getTargetUserId());
            clarification.setConstraintKey(
                    aiClarification.getConstraintKey());
            clarification.setQuestion(aiClarification.getQuestion());
            clarification.setOptionsJson(
                    JSONUtil.toJsonStr(aiClarification.getOptions()));
            clarification.setStatus(
                    MeetClarificationStatus.PENDING.name());
            clarification.setCreateTime(now);
            clarificationMapper.insert(clarification);

            eventService.append(
                    context.run().getId(),
                    context.attempt().getId(),
                    MeetPlanEventType.WAITING_INPUT,
                    "需要一位成员确认是否放宽硬约束",
                    JSONUtil.toJsonStr(Map.of(
                            "clarificationId", clarification.getId(),
                            "targetUserId", clarification.getTargetUserId(),
                            "constraintKey", clarification.getConstraintKey(),
                            "question", clarification.getQuestion(),
                            "options", aiClarification.getOptions())));
            return true;
        });
        return Boolean.TRUE.equals(persisted);
    }

    private boolean persistSuccess(
            ExecutionContext context,
            AiMeetPlanSet planSet,
            List<MeetRestaurantCandidate> candidates) {
        Boolean persisted = transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            if (attemptMapper.transitionToSucceeded(
                    context.run().getId(),
                    context.attempt().getId(),
                    now) == 0) {
                return false;
            }

            eventService.append(
                    context.run().getId(),
                    context.attempt().getId(),
                    MeetPlanEventType.PLANS_DRAFTED,
                    "Agent 已组织首选和备选方案",
                    null);
            saveProposals(
                    context.run().getId(),
                    context.attempt().getId(),
                    planSet,
                    candidates,
                    now);
            eventService.append(
                    context.run().getId(),
                    context.attempt().getId(),
                    MeetPlanEventType.PLANS_VALIDATED,
                    "Java 已校验三个方案的餐厅和硬约束",
                    null);
            eventService.append(
                    context.run().getId(),
                    context.attempt().getId(),
                    MeetPlanEventType.RUN_COMPLETED,
                    "方案集已生成，等待房主确认",
                    null);
            return true;
        });
        return Boolean.TRUE.equals(persisted);
    }

    private void saveProposals(
            Long runId,
            Long attemptId,
            AiMeetPlanSet planSet,
            List<MeetRestaurantCandidate> candidates,
            LocalDateTime now) {
        Map<Long, MeetRestaurantCandidate> candidateMap =
                candidates.stream().collect(Collectors.toMap(
                        MeetRestaurantCandidate::getShopId,
                        Function.identity()));
        for (int index = 0; index < planSet.getPlans().size(); index++) {
            AiMeetPlanOption option = planSet.getPlans().get(index);
            MeetRestaurantCandidate candidate =
                    candidateMap.get(option.getShopId());
            MeetProposal proposal = new MeetProposal();
            proposal.setRunId(runId);
            proposal.setAttemptId(attemptId);
            proposal.setProposalRank(index + 1);
            proposal.setRecommended(index == 0);
            proposal.setShopId(option.getShopId());
            proposal.setSuggestedTime(option.getSuggestedTime().trim());
            proposal.setMeetingPoint(
                    option.getMeetingPoint() == null
                            || option.getMeetingPoint().isBlank()
                            ? candidate.getAddress()
                            : option.getMeetingPoint().trim());
            proposal.setEstimatedPerCapita(candidate.getAvgPrice());
            proposal.setReasoning(option.getReasoning());
            proposal.setSatisfiedJson(JSONUtil.toJsonStr(
                    option.getSatisfiedPreferences() == null
                            ? List.of()
                            : option.getSatisfiedPreferences()));
            proposal.setTradeoffsJson(JSONUtil.toJsonStr(
                    option.getTradeoffs() == null
                            ? List.of()
                            : option.getTradeoffs()));
            proposal.setCreateTime(now);
            proposalMapper.insert(proposal);
        }
    }

    private boolean persistFailure(
            ExecutionContext context,
            String errorCode,
            String message) {
        String safeMessage = limit(message);
        Boolean persisted = transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            if (attemptMapper.transitionToFailed(
                    context.run().getId(),
                    context.attempt().getId(),
                    errorCode,
                    safeMessage,
                    now) == 0) {
                return false;
            }
            eventService.append(
                    context.run().getId(),
                    context.attempt().getId(),
                    MeetPlanEventType.RUN_FAILED,
                    "规划失败：" + safeMessage,
                    JSONUtil.toJsonStr(Map.of("errorCode", errorCode)));
            return true;
        });
        return Boolean.TRUE.equals(persisted);
    }

    private boolean shouldRetry(
            ExecutionContext context, RuntimeException exception) {
        int executionNo = context.attempt().getDispatchAttempts() == null
                ? 1
                : context.attempt().getDispatchAttempts();
        return executionNo < Math.max(1, maxExecutionAttempts)
                && isTransient(exception);
    }

    private boolean isTransient(Throwable exception) {
        for (Throwable current = exception;
             current != null;
             current = current.getCause()) {
            if (current instanceof DataAccessException
                    || current instanceof TimeoutException
                    || current instanceof java.net.ConnectException
                    || current instanceof java.net.SocketTimeoutException
                    || current instanceof java.io.IOException) {
                return true;
            }
        }
        return false;
    }

    private boolean persistRetry(
            ExecutionContext context,
            String errorCode,
            String message) {
        Boolean persisted = transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            int executionNo = context.attempt().getDispatchAttempts() == null
                    ? 1
                    : context.attempt().getDispatchAttempts();
            long delaySeconds = Math.min(30L, 1L << Math.min(executionNo, 5));
            LocalDateTime nextDispatchAt = now.plusSeconds(delaySeconds);
            if (attemptMapper.transitionToRetry(
                    context.run().getId(),
                    context.attempt().getId(),
                    errorCode,
                    limit(message),
                    nextDispatchAt,
                    now) == 0) {
                return false;
            }
            eventService.append(
                    context.run().getId(),
                    context.attempt().getId(),
                    MeetPlanEventType.RUN_QUEUED,
                    "规划调用暂时失败，正在进行受限重试",
                    JSONUtil.toJsonStr(Map.of(
                            "errorCode", errorCode,
                            "nextDispatchAt", nextDispatchAt)));
            return true;
        });
        return Boolean.TRUE.equals(persisted);
    }

    private String limit(String value) {
        if (value == null || value.isBlank()) {
            return "未知错误";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private record ExecutionContext(
            MeetPlanRun run,
            MeetPlanAttempt attempt,
            MeetRoom room) {
    }
}
