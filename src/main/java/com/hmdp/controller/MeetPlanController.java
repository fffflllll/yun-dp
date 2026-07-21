package com.hmdp.controller;

import com.hmdp.dto.AnswerMeetClarificationRequest;
import com.hmdp.dto.ConfirmMeetPlanRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.IMeetPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/meet")
@RequiredArgsConstructor
public class MeetPlanController {

    private final IMeetPlanService planService;

    @PostMapping("/rooms/{roomId}/plan-runs")
    public Result start(@PathVariable Long roomId) {
        return planService.start(roomId);
    }

    @GetMapping("/plan-runs/{runId}")
    public Result getRun(@PathVariable Long runId) {
        return planService.getRun(runId);
    }

    @GetMapping(value = "/plan-runs/{runId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @PathVariable Long runId,
            @RequestHeader(value = "Last-Event-ID", required = false)
            String lastEventId,
            @RequestParam(value = "after", defaultValue = "0")
            long after) {
        long headerSequence = parseSequence(lastEventId);
        return planService.subscribe(runId,
                Math.max(after, headerSequence));
    }

    @PostMapping("/plan-runs/{runId}/clarifications/{clarificationId}/answer")
    public Result answer(
            @PathVariable Long runId,
            @PathVariable Long clarificationId,
            @Valid @RequestBody AnswerMeetClarificationRequest request) {
        return planService.answerClarification(
                runId, clarificationId, request);
    }

    @PostMapping("/plan-runs/{runId}/confirm")
    public Result confirm(
            @PathVariable Long runId,
            @Valid @RequestBody ConfirmMeetPlanRequest request) {
        return planService.confirmPlan(runId, request);
    }

    private long parseSequence(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
