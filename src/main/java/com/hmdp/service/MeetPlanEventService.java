package com.hmdp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.MeetPlanEvent;
import com.hmdp.enums.MeetPlanEventType;
import com.hmdp.mapper.MeetPlanEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetPlanEventService {

    private final MeetPlanEventMapper eventMapper;
    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public synchronized MeetPlanEvent append(
            Long runId,
            Long attemptId,
            MeetPlanEventType type,
            String summary,
            String payloadJson) {
        Long lastSequence = eventMapper.selectObjs(
                        new LambdaQueryWrapper<MeetPlanEvent>()
                                .select(MeetPlanEvent::getSequence)
                                .eq(MeetPlanEvent::getRunId, runId)
                                .orderByDesc(MeetPlanEvent::getSequence)
                                .last("limit 1"))
                .stream()
                .findFirst()
                .map(value -> ((Number) value).longValue())
                .orElse(0L);

        MeetPlanEvent event = new MeetPlanEvent();
        event.setRunId(runId);
        event.setAttemptId(attemptId);
        event.setSequence(lastSequence + 1);
        event.setEventType(type.name());
        event.setSummary(summary);
        event.setPayloadJson(payloadJson);
        event.setCreateTime(LocalDateTime.now());
        eventMapper.insert(event);

        publishAfterCommit(event);
        return event;
    }

    public List<MeetPlanEvent> listAfter(Long runId, long afterSequence) {
        return eventMapper.selectList(
                new LambdaQueryWrapper<MeetPlanEvent>()
                        .eq(MeetPlanEvent::getRunId, runId)
                        .gt(MeetPlanEvent::getSequence, afterSequence)
                        .orderByAsc(MeetPlanEvent::getSequence)
        );
    }

    public SseEmitter subscribe(Long runId, long afterSequence) {
        SseEmitter emitter = new SseEmitter(0L);
        Set<SseEmitter> runEmitters = emitters.computeIfAbsent(
                runId, ignored -> new CopyOnWriteArraySet<>());
        runEmitters.add(emitter);
        Runnable cleanup = () -> remove(runId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("runId", runId)));
            for (MeetPlanEvent event : listAfter(runId, afterSequence)) {
                send(emitter, event);
            }
        } catch (IOException exception) {
            cleanup.run();
        }
        return emitter;
    }

    private void publishAfterCommit(MeetPlanEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            publish(event);
                        }
                    });
        } else {
            publish(event);
        }
    }

    private void publish(MeetPlanEvent event) {
        for (SseEmitter emitter :
                emitters.getOrDefault(event.getRunId(), Set.of())) {
            try {
                send(emitter, event);
            } catch (IOException exception) {
                remove(event.getRunId(), emitter);
            }
        }
    }

    private void send(SseEmitter emitter, MeetPlanEvent event)
            throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventId", event.getId());
        data.put("runId", event.getRunId());
        data.put("attemptId", event.getAttemptId());
        data.put("sequence", event.getSequence());
        data.put("type", event.getEventType());
        data.put("summary", event.getSummary());
        data.put("payload", event.getPayloadJson());
        data.put("createdAt", event.getCreateTime());
        emitter.send(SseEmitter.event()
                .id(String.valueOf(event.getSequence()))
                .name(event.getEventType().toLowerCase())
                .data(data));
    }

    private void remove(Long runId, SseEmitter emitter) {
        Set<SseEmitter> runEmitters = emitters.get(runId);
        if (runEmitters == null) {
            return;
        }
        runEmitters.remove(emitter);
        if (runEmitters.isEmpty()) {
            emitters.remove(runId, runEmitters);
        }
    }
}
