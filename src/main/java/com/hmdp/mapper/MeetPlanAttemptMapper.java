package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.MeetPlanAttempt;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface MeetPlanAttemptMapper extends BaseMapper<MeetPlanAttempt> {

    @Update("""
            UPDATE tb_meet_plan_attempt
            SET dispatch_status = 'DISPATCHED',
                dispatch_attempts = dispatch_attempts + 1,
                update_time = #{now}
            WHERE id = #{attemptId}
              AND status = 'QUEUED'
              AND dispatch_status = 'PENDING'
              AND (next_dispatch_at IS NULL OR next_dispatch_at <= #{now})
            """)
    int claimForDispatch(
            @Param("attemptId") Long attemptId,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE tb_meet_plan_attempt
            SET dispatch_status = 'PENDING',
                next_dispatch_at = #{nextDispatchAt},
                update_time = #{now}
            WHERE id = #{attemptId}
              AND status = 'QUEUED'
              AND dispatch_status = 'DISPATCHED'
            """)
    int releaseDispatch(
            @Param("attemptId") Long attemptId,
            @Param("nextDispatchAt") LocalDateTime nextDispatchAt,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE tb_meet_plan_attempt
            SET dispatch_status = 'PENDING',
                next_dispatch_at = #{now},
                update_time = #{now}
            WHERE status = 'QUEUED'
              AND dispatch_status = 'DISPATCHED'
              AND update_time < #{staleBefore}
            """)
    int resetStaleDispatches(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE tb_meet_plan_attempt attempt
            JOIN tb_meet_plan_run run ON run.id = attempt.run_id
            JOIN tb_meet_room room ON room.id = run.room_id
            SET attempt.status = 'RUNNING',
                attempt.model = #{model},
                attempt.started_at = #{startedAt},
                attempt.finished_at = NULL,
                attempt.error_code = NULL,
                attempt.error_message = NULL,
                attempt.update_time = #{startedAt},
                run.status = 'RUNNING',
                run.finished_at = NULL,
                run.error_code = NULL,
                run.error_message = NULL,
                run.update_time = #{startedAt}
            WHERE attempt.id = #{attemptId}
              AND attempt.run_id = #{runId}
              AND attempt.status = 'QUEUED'
              AND run.status = 'QUEUED'
              AND run.current_attempt = attempt.attempt_no
              AND room.status = 'PLANNING'
            """)
    int claimForExecution(
            @Param("runId") Long runId,
            @Param("attemptId") Long attemptId,
            @Param("model") String model,
            @Param("startedAt") LocalDateTime startedAt);

    @Update("""
            UPDATE tb_meet_plan_attempt attempt
            JOIN tb_meet_plan_run run ON run.id = attempt.run_id
            JOIN tb_meet_room room ON room.id = run.room_id
            SET attempt.status = 'WAITING_INPUT',
                attempt.finished_at = #{finishedAt},
                attempt.update_time = #{finishedAt},
                run.status = 'WAITING_INPUT',
                run.clarification_count = run.clarification_count + 1,
                run.update_time = #{finishedAt},
                room.status = 'WAITING_INPUT',
                room.update_time = #{finishedAt}
            WHERE attempt.id = #{attemptId}
              AND attempt.run_id = #{runId}
              AND attempt.status = 'RUNNING'
              AND run.status = 'RUNNING'
              AND run.current_attempt = attempt.attempt_no
              AND run.clarification_count < 1
              AND room.status = 'PLANNING'
            """)
    int transitionToWaitingInput(
            @Param("runId") Long runId,
            @Param("attemptId") Long attemptId,
            @Param("finishedAt") LocalDateTime finishedAt);

    @Update("""
            UPDATE tb_meet_plan_attempt attempt
            JOIN tb_meet_plan_run run ON run.id = attempt.run_id
            JOIN tb_meet_room room ON room.id = run.room_id
            SET attempt.status = 'SUCCEEDED',
                attempt.finished_at = #{finishedAt},
                attempt.update_time = #{finishedAt},
                run.status = 'SUCCEEDED',
                run.finished_at = #{finishedAt},
                run.update_time = #{finishedAt},
                room.status = 'PLANS_READY',
                room.update_time = #{finishedAt}
            WHERE attempt.id = #{attemptId}
              AND attempt.run_id = #{runId}
              AND attempt.status = 'RUNNING'
              AND run.status = 'RUNNING'
              AND run.current_attempt = attempt.attempt_no
              AND room.status = 'PLANNING'
            """)
    int transitionToSucceeded(
            @Param("runId") Long runId,
            @Param("attemptId") Long attemptId,
            @Param("finishedAt") LocalDateTime finishedAt);

    @Update("""
            UPDATE tb_meet_plan_attempt attempt
            JOIN tb_meet_plan_run run ON run.id = attempt.run_id
            JOIN tb_meet_room room ON room.id = run.room_id
            SET attempt.status = 'FAILED',
                attempt.error_code = #{errorCode},
                attempt.error_message = #{errorMessage},
                attempt.finished_at = #{finishedAt},
                attempt.update_time = #{finishedAt},
                run.status = 'FAILED',
                run.error_code = #{errorCode},
                run.error_message = #{errorMessage},
                run.finished_at = #{finishedAt},
                run.update_time = #{finishedAt},
                room.status = 'FAILED',
                room.update_time = #{finishedAt}
            WHERE attempt.id = #{attemptId}
              AND attempt.run_id = #{runId}
              AND attempt.status = 'RUNNING'
              AND run.status = 'RUNNING'
              AND run.current_attempt = attempt.attempt_no
              AND room.status = 'PLANNING'
            """)
    int transitionToFailed(
            @Param("runId") Long runId,
            @Param("attemptId") Long attemptId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") LocalDateTime finishedAt);

    @Update("""
            UPDATE tb_meet_plan_attempt attempt
            JOIN tb_meet_plan_run run ON run.id = attempt.run_id
            JOIN tb_meet_room room ON room.id = run.room_id
            SET attempt.status = 'QUEUED',
                attempt.dispatch_status = 'PENDING',
                attempt.next_dispatch_at = #{nextDispatchAt},
                attempt.error_code = #{errorCode},
                attempt.error_message = #{errorMessage},
                attempt.finished_at = NULL,
                attempt.update_time = #{queuedAt},
                run.status = 'QUEUED',
                run.error_code = NULL,
                run.error_message = NULL,
                run.finished_at = NULL,
                run.update_time = #{queuedAt}
            WHERE attempt.id = #{attemptId}
              AND attempt.run_id = #{runId}
              AND attempt.status = 'RUNNING'
              AND run.status = 'RUNNING'
              AND run.current_attempt = attempt.attempt_no
              AND room.status = 'PLANNING'
            """)
    int transitionToRetry(
            @Param("runId") Long runId,
            @Param("attemptId") Long attemptId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("nextDispatchAt") LocalDateTime nextDispatchAt,
            @Param("queuedAt") LocalDateTime queuedAt);

    @Update("""
            UPDATE tb_meet_plan_attempt attempt
            JOIN tb_meet_plan_run run ON run.id = attempt.run_id
            JOIN tb_meet_room room ON room.id = run.room_id
            SET attempt.status = 'FAILED',
                attempt.error_code = 'RUN_TIMED_OUT',
                attempt.error_message = #{errorMessage},
                attempt.finished_at = #{finishedAt},
                attempt.update_time = #{finishedAt},
                run.status = 'FAILED',
                run.error_code = 'RUN_TIMED_OUT',
                run.error_message = #{errorMessage},
                run.finished_at = #{finishedAt},
                run.update_time = #{finishedAt},
                room.status = 'READY_TO_PLAN',
                room.update_time = #{finishedAt}
            WHERE attempt.id = #{attemptId}
              AND attempt.run_id = #{runId}
              AND attempt.status = 'RUNNING'
              AND attempt.update_time < #{staleBefore}
              AND run.status = 'RUNNING'
              AND run.current_attempt = attempt.attempt_no
              AND room.status = 'PLANNING'
            """)
    int transitionTimedOut(
            @Param("runId") Long runId,
            @Param("attemptId") Long attemptId,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") LocalDateTime finishedAt);
}
