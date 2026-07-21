package com.hmdp.dto;

import java.io.Serializable;

public record MeetPlanAttemptMessage(Long runId, Long attemptId) implements Serializable {
}
