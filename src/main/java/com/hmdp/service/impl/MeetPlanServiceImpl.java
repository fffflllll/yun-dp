package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.config.MeetAiProperties;
import com.hmdp.dto.AnswerMeetClarificationRequest;
import com.hmdp.dto.ConfirmMeetPlanRequest;
import com.hmdp.dto.MeetPreferenceData;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.MeetClarification;
import com.hmdp.entity.MeetMember;
import com.hmdp.entity.MeetPlanAttempt;
import com.hmdp.entity.MeetPlanEvent;
import com.hmdp.entity.MeetPlanRun;
import com.hmdp.entity.MeetPreference;
import com.hmdp.entity.MeetProposal;
import com.hmdp.entity.MeetRoom;
import com.hmdp.entity.Shop;
import com.hmdp.enums.MeetClarificationStatus;
import com.hmdp.enums.MeetMemberStatus;
import com.hmdp.enums.MeetPlanAttemptStatus;
import com.hmdp.enums.MeetPlanDispatchStatus;
import com.hmdp.enums.MeetPlanEventType;
import com.hmdp.enums.MeetPlanRunStatus;
import com.hmdp.enums.MeetRoomStatus;
import com.hmdp.mapper.MeetClarificationMapper;
import com.hmdp.mapper.MeetMemberMapper;
import com.hmdp.mapper.MeetPlanAttemptMapper;
import com.hmdp.mapper.MeetPlanRunMapper;
import com.hmdp.mapper.MeetPreferenceMapper;
import com.hmdp.mapper.MeetProposalMapper;
import com.hmdp.mapper.MeetRoomMapper;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IMeetPlanService;
import com.hmdp.service.MeetPlanAttemptDispatcher;
import com.hmdp.service.MeetPlanEventService;
import com.hmdp.service.MeetRestaurantCandidateService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MeetPlanServiceImpl implements IMeetPlanService {

    private static final Set<String> ACTIVE_RUN_STATUSES = Set.of(
            MeetPlanRunStatus.QUEUED.name(),
            MeetPlanRunStatus.RUNNING.name(),
            MeetPlanRunStatus.WAITING_INPUT.name());

    private final MeetRoomMapper roomMapper;
    private final MeetMemberMapper memberMapper;
    private final MeetPlanRunMapper runMapper;
    private final MeetPlanAttemptMapper attemptMapper;
    private final MeetPlanEventService eventService;
    private final MeetPlanAttemptDispatcher attemptDispatcher;
    private final MeetRestaurantCandidateService candidateService;
    private final MeetClarificationMapper clarificationMapper;
    private final MeetPreferenceMapper preferenceMapper;
    private final MeetProposalMapper proposalMapper;
    private final ShopMapper shopMapper;
    private final MeetAiProperties aiProperties;
    private final RedissonClient redissonClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result start(Long roomId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }

        RLock lock = redissonClient.getLock("lock:meet:plan:" + roomId);
        if (!lock.tryLock()) {
            return Result.fail("规划任务正在创建，请勿重复操作");
        }
        try {
            MeetRoom room = roomMapper.selectById(roomId);
            if (room == null) {
                return Result.fail("聚会房间不存在");
            }
            if (!user.getId().equals(room.getCreatorId())) {
                return Result.fail("只有房主可以开始规划");
            }
            if (!MeetRoomStatus.READY_TO_PLAN.name()
                    .equals(room.getStatus())) {
                return Result.fail("所有锁定成员确认偏好后才能开始规划");
            }

            Long activeCount = runMapper.selectCount(
                    new LambdaQueryWrapper<MeetPlanRun>()
                            .eq(MeetPlanRun::getRoomId, roomId)
                            .in(MeetPlanRun::getStatus,
                                    ACTIVE_RUN_STATUSES)
            );
            if (activeCount > 0) {
                return Result.fail("该房间已有进行中的规划任务");
            }

            LocalDateTime now = LocalDateTime.now();
            MeetPlanRun run = new MeetPlanRun();
            run.setRoomId(roomId);
            run.setStatus(MeetPlanRunStatus.QUEUED.name());
            run.setCurrentAttempt(1);
            run.setClarificationCount(0);
            run.setVersion(0);
            run.setCreateTime(now);
            run.setUpdateTime(now);
            runMapper.insert(run);

            MeetPlanAttempt attempt = createAttempt(run.getId(), 1, now);
            attemptMapper.insert(attempt);

            int claimedRoom = roomMapper.update(
                    null,
                    new LambdaUpdateWrapper<MeetRoom>()
                            .eq(MeetRoom::getId, roomId)
                            .eq(MeetRoom::getStatus,
                                    MeetRoomStatus.READY_TO_PLAN.name())
                            .set(MeetRoom::getStatus,
                                    MeetRoomStatus.PLANNING.name())
                            .set(MeetRoom::getUpdateTime, now)
                            .setSql("version = version + 1"));
            if (claimedRoom != 1) {
                throw new IllegalStateException("规划启动状态已发生变化");
            }

            eventService.append(run.getId(), attempt.getId(),
                    MeetPlanEventType.RUN_QUEUED,
                    "规划任务已进入队列", null);
            requestDispatchAfterCommit();
            return Result.ok(Map.of(
                    "runId", run.getId(),
                    "status", run.getStatus()));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result getRun(Long runId) {
        AccessContext access = requireRunMember(runId);
        if (access.error() != null) {
            return Result.fail(access.error());
        }

        List<MeetPlanAttempt> attempts = attemptMapper.selectList(
                new LambdaQueryWrapper<MeetPlanAttempt>()
                        .eq(MeetPlanAttempt::getRunId, runId)
                        .orderByAsc(MeetPlanAttempt::getAttemptNo));
        List<MeetPlanEvent> events = eventService.listAfter(runId, 0);
        List<Map<String, Object>> proposals = proposalMapper.selectList(
                        new LambdaQueryWrapper<MeetProposal>()
                                .eq(MeetProposal::getRunId, runId)
                                .orderByAsc(MeetProposal::getProposalRank))
                .stream()
                .map(this::proposalView)
                .toList();
        MeetClarification clarification = clarificationMapper.selectOne(
                new LambdaQueryWrapper<MeetClarification>()
                        .eq(MeetClarification::getRunId, runId)
                        .orderByDesc(MeetClarification::getId)
                        .last("limit 1"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("run", access.run());
        response.put("attempts", attempts);
        response.put("events", events);
        response.put("clarification", clarification);
        response.put("proposals", proposals);
        return Result.ok(response);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result answerClarification(
            Long runId,
            Long clarificationId,
            AnswerMeetClarificationRequest request) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }

        MeetPlanRun run = runMapper.selectById(runId);
        MeetClarification clarification =
                clarificationMapper.selectById(clarificationId);
        if (run == null || clarification == null
                || !runId.equals(clarification.getRunId())) {
            return Result.fail("澄清问题不存在");
        }
        if (!MeetPlanRunStatus.WAITING_INPUT.name()
                .equals(run.getStatus())
                || !MeetClarificationStatus.PENDING.name()
                .equals(clarification.getStatus())) {
            return Result.fail("该澄清问题已失效");
        }
        if (!user.getId().equals(clarification.getTargetUserId())) {
            return Result.fail("只有被询问的成员可以回答");
        }
        if (!List.of("RELAX_TO_SOFT", "CANCEL_PLAN")
                .contains(request.getAnswer())) {
            return Result.fail("澄清答案不合法");
        }

        LocalDateTime now = LocalDateTime.now();
        MeetRoom room = roomMapper.selectById(run.getRoomId());
        if (room == null) {
            return Result.fail("聚会房间不存在，无法继续规划");
        }
        if ("CANCEL_PLAN".equals(request.getAnswer())) {
            int answered = clarificationMapper.update(
                    null,
                    new LambdaUpdateWrapper<MeetClarification>()
                            .eq(MeetClarification::getId, clarificationId)
                            .eq(MeetClarification::getRunId, runId)
                            .eq(MeetClarification::getStatus,
                                    MeetClarificationStatus.PENDING.name())
                            .set(MeetClarification::getAnswer,
                                    request.getAnswer())
                            .set(MeetClarification::getStatus,
                                    MeetClarificationStatus.ANSWERED.name())
                            .set(MeetClarification::getAnsweredAt, now));
            if (answered != 1) {
                return Result.fail("该澄清问题已被回答");
            }
            int cancelledRun = runMapper.update(
                    null,
                    new LambdaUpdateWrapper<MeetPlanRun>()
                            .eq(MeetPlanRun::getId, runId)
                            .eq(MeetPlanRun::getStatus,
                                    MeetPlanRunStatus.WAITING_INPUT.name())
                            .set(MeetPlanRun::getStatus,
                                    MeetPlanRunStatus.CANCELLED.name())
                            .set(MeetPlanRun::getFinishedAt, now)
                            .set(MeetPlanRun::getUpdateTime, now));
            if (cancelledRun != 1) {
                throw new IllegalStateException("规划任务状态已发生变化");
            }
            int cancelledRoom = roomMapper.update(
                    null,
                    new LambdaUpdateWrapper<MeetRoom>()
                            .eq(MeetRoom::getId, room.getId())
                            .eq(MeetRoom::getStatus,
                                    MeetRoomStatus.WAITING_INPUT.name())
                            .set(MeetRoom::getStatus,
                                    MeetRoomStatus.CANCELLED.name())
                            .set(MeetRoom::getUpdateTime, now));
            if (cancelledRoom != 1) {
                throw new IllegalStateException("房间状态已发生变化");
            }
            eventService.append(runId, null,
                    MeetPlanEventType.RUN_CANCELLED,
                    "成员取消了本次规划", null);
            return Result.ok();
        }

        MeetPreference preference = preferenceMapper.selectOne(
                new LambdaQueryWrapper<MeetPreference>()
                        .eq(MeetPreference::getRoomId, run.getRoomId())
                        .eq(MeetPreference::getUserId,
                                clarification.getTargetUserId()));
        if (preference == null || preference.getConfirmedJson() == null) {
            return Result.fail("成员确认偏好不存在，无法重新规划");
        }

        int answered = clarificationMapper.update(
                null,
                new LambdaUpdateWrapper<MeetClarification>()
                        .eq(MeetClarification::getId, clarificationId)
                        .eq(MeetClarification::getRunId, runId)
                        .eq(MeetClarification::getStatus,
                                MeetClarificationStatus.PENDING.name())
                        .set(MeetClarification::getAnswer,
                                request.getAnswer())
                        .set(MeetClarification::getStatus,
                                MeetClarificationStatus.ANSWERED.name())
                        .set(MeetClarification::getAnsweredAt, now));
        if (answered != 1) {
            return Result.fail("该澄清问题已被回答");
        }

        MeetPreferenceData data = JSONUtil.toBean(
                preference.getConfirmedJson(), MeetPreferenceData.class);
        if (data.getHardConstraintKeys() == null) {
            data.setHardConstraintKeys(new java.util.ArrayList<>());
        }
        data.getHardConstraintKeys().remove(
                clarification.getConstraintKey());
        preference.setConfirmedJson(JSONUtil.toJsonStr(data));
        preference.setVersion(preference.getVersion() + 1);
        preference.setUpdateTime(now);
        preferenceMapper.updateById(preference);

        int nextAttemptNo = run.getCurrentAttempt() + 1;
        MeetPlanAttempt nextAttempt = createAttempt(
                runId, nextAttemptNo, now);
        attemptMapper.insert(nextAttempt);
        int resumed = runMapper.update(
                null,
                new LambdaUpdateWrapper<MeetPlanRun>()
                        .eq(MeetPlanRun::getId, runId)
                        .eq(MeetPlanRun::getStatus,
                                MeetPlanRunStatus.WAITING_INPUT.name())
                        .eq(MeetPlanRun::getCurrentAttempt,
                                run.getCurrentAttempt())
                        .set(MeetPlanRun::getStatus,
                                MeetPlanRunStatus.QUEUED.name())
                        .set(MeetPlanRun::getCurrentAttempt, nextAttemptNo)
                        .set(MeetPlanRun::getUpdateTime, now)
                        .setSql("version = version + 1"));
        if (resumed != 1) {
            throw new IllegalStateException("规划任务状态已发生变化");
        }
        int planningRoom = roomMapper.update(
                null,
                new LambdaUpdateWrapper<MeetRoom>()
                        .eq(MeetRoom::getId, room.getId())
                        .eq(MeetRoom::getStatus,
                                MeetRoomStatus.WAITING_INPUT.name())
                        .set(MeetRoom::getStatus,
                                MeetRoomStatus.PLANNING.name())
                        .set(MeetRoom::getUpdateTime, now));
        if (planningRoom != 1) {
            throw new IllegalStateException("房间状态已发生变化");
        }
        eventService.append(runId, nextAttempt.getId(),
                MeetPlanEventType.RUN_QUEUED,
                "成员已回答澄清，正在重新规划", null);
        requestDispatchAfterCommit();
        return Result.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result confirmPlan(
            Long runId, ConfirmMeetPlanRequest request) {
        UserDTO user = UserHolder.getUser();
        MeetPlanRun run = runMapper.selectById(runId);
        if (user == null) {
            return Result.fail("用户未登录");
        }
        if (run == null || !MeetPlanRunStatus.SUCCEEDED.name()
                .equals(run.getStatus())) {
            return Result.fail("规划任务尚未生成可确认方案");
        }

        MeetRoom room = roomMapper.selectById(run.getRoomId());
        if (room == null) {
            return Result.fail("聚会房间不存在，无法确认方案");
        }
        if (!user.getId().equals(room.getCreatorId())) {
            return Result.fail("只有房主可以确认方案");
        }
        MeetProposal proposal = proposalMapper.selectById(
                request.getProposalId());
        if (proposal == null || !runId.equals(proposal.getRunId())) {
            return Result.fail("所选方案不属于本次规划");
        }
        if (shopMapper.selectById(proposal.getShopId()) == null) {
            return Result.fail("方案中的餐厅已不存在");
        }
        boolean stillFeasible = candidateService.select(room)
                .candidates()
                .stream()
                .anyMatch(candidate -> proposal.getShopId()
                        .equals(candidate.getShopId()));
        if (!stillFeasible) {
            return Result.fail("该方案已不满足当前成员硬约束，请重新规划");
        }

        LocalDateTime now = LocalDateTime.now();
        int finalizedRoom = roomMapper.update(
                null,
                new LambdaUpdateWrapper<MeetRoom>()
                        .eq(MeetRoom::getId, room.getId())
                        .eq(MeetRoom::getStatus,
                                MeetRoomStatus.PLANS_READY.name())
                        .isNull(MeetRoom::getConfirmedProposalId)
                        .set(MeetRoom::getConfirmedProposalId,
                                proposal.getId())
                        .set(MeetRoom::getStatus,
                                MeetRoomStatus.FINALIZED.name())
                        .set(MeetRoom::getUpdateTime, now));
        if (finalizedRoom != 1) {
            return Result.fail("聚会方案已被确认或房间状态已变化");
        }
        run.setSelectedProposalId(proposal.getId());
        run.setUpdateTime(now);
        runMapper.updateById(run);
        eventService.append(runId, proposal.getAttemptId(),
                MeetPlanEventType.PLAN_CONFIRMED,
                "房主已确认聚会方案",
                JSONUtil.toJsonStr(Map.of(
                        "proposalId", proposal.getId(),
                        "shopId", proposal.getShopId())));
        return Result.ok();
    }

    @Override
    public SseEmitter subscribe(Long runId, long afterSequence) {
        AccessContext access = requireRunMember(runId);
        if (access.error() != null) {
            throw new IllegalArgumentException(access.error());
        }
        return eventService.subscribe(runId, afterSequence);
    }

    private MeetPlanAttempt createAttempt(
            Long runId, int attemptNo, LocalDateTime now) {
        MeetPlanAttempt attempt = new MeetPlanAttempt();
        attempt.setRunId(runId);
        attempt.setAttemptNo(attemptNo);
        attempt.setStatus(MeetPlanAttemptStatus.QUEUED.name());
        attempt.setDispatchStatus(MeetPlanDispatchStatus.PENDING.name());
        attempt.setDispatchAttempts(0);
        attempt.setPromptVersion(aiProperties.getPromptVersion());
        attempt.setNextDispatchAt(now);
        attempt.setCreateTime(now);
        attempt.setUpdateTime(now);
        return attempt;
    }

    private AccessContext requireRunMember(Long runId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return AccessContext.error("用户未登录");
        }
        MeetPlanRun run = runMapper.selectById(runId);
        if (run == null) {
            return AccessContext.error("规划任务不存在");
        }
        Long memberCount = memberMapper.selectCount(
                new LambdaQueryWrapper<MeetMember>()
                        .eq(MeetMember::getRoomId, run.getRoomId())
                        .eq(MeetMember::getUserId, user.getId())
                        .eq(MeetMember::getStatus,
                                MeetMemberStatus.JOINED.name()));
        return memberCount == 0
                ? AccessContext.error("你不是该房间成员")
                : new AccessContext(run, null);
    }

    private Map<String, Object> proposalView(MeetProposal proposal) {
        Shop shop = shopMapper.selectById(proposal.getShopId());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("proposalId", proposal.getId());
        view.put("rank", proposal.getProposalRank());
        view.put("recommended", proposal.getRecommended());
        view.put("shopId", proposal.getShopId());
        view.put("shopName", shop == null ? null : shop.getName());
        view.put("address", shop == null ? null : shop.getAddress());
        view.put("images", shop == null ? null : shop.getImages());
        view.put("suggestedTime", proposal.getSuggestedTime());
        view.put("meetingPoint", proposal.getMeetingPoint());
        view.put("estimatedPerCapita", proposal.getEstimatedPerCapita());
        view.put("reasoning", proposal.getReasoning());
        view.put("satisfiedPreferences",
                JSONUtil.toList(proposal.getSatisfiedJson(), String.class));
        view.put("tradeoffs",
                JSONUtil.toList(proposal.getTradeoffsJson(), String.class));
        return view;
    }

    private void requestDispatchAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            attemptDispatcher.requestDispatch();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        attemptDispatcher.requestDispatch();
                    }
                });
    }

    private record AccessContext(MeetPlanRun run, String error) {
        private static AccessContext error(String message) {
            return new AccessContext(null, message);
        }
    }
}
