package com.hmdp;

import com.hmdp.mapper.MeetClarificationMapper;
import com.hmdp.mapper.MeetPlanAttemptMapper;
import com.hmdp.mapper.MeetPlanRunMapper;
import com.hmdp.mapper.MeetProposalMapper;
import com.hmdp.mapper.MeetRoomMapper;
import com.hmdp.service.MeetPlanEventService;
import com.hmdp.service.MeetPlanExecutionService;
import com.hmdp.service.MeetPlanPolicyService;
import com.hmdp.service.MeetPlanningAgent;
import com.hmdp.service.MeetPlanningAiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetPlanExecutionServiceTest {

    @Mock
    private MeetPlanRunMapper runMapper;
    @Mock
    private MeetPlanAttemptMapper attemptMapper;
    @Mock
    private MeetRoomMapper roomMapper;
    @Mock
    private MeetProposalMapper proposalMapper;
    @Mock
    private MeetClarificationMapper clarificationMapper;
    @Mock
    private MeetPlanningAgent planningAgent;
    @Mock
    private MeetPlanningAiService planningAiService;
    @Mock
    private MeetPlanPolicyService planPolicyService;
    @Mock
    private MeetPlanEventService eventService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private MeetPlanExecutionService executionService;

    @BeforeEach
    void setUp() {
        executionService = new MeetPlanExecutionService(
                runMapper,
                attemptMapper,
                roomMapper,
                proposalMapper,
                clarificationMapper,
                planningAgent,
                planningAiService,
                planPolicyService,
                eventService,
                transactionTemplate);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void ignoresDeliveryWhenAtomicClaimIsRejected() {
        executionService.execute(10L, 20L);

        verifyNoInteractions(runMapper, roomMapper, proposalMapper,
                clarificationMapper,
                planningAgent, planningAiService, planPolicyService, eventService);
    }

    @Test
    void duplicateDeliveryNeverInvokesPlanningAgent() {
        executionService.execute(10L, 20L);
        executionService.execute(10L, 20L);

        verifyNoInteractions(runMapper, roomMapper, proposalMapper,
                clarificationMapper,
                planningAgent, planningAiService, planPolicyService, eventService);
    }
}
