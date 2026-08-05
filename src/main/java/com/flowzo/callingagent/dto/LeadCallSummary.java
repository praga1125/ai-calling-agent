package com.flowzo.callingagent.dto;

import com.flowzo.callingagent.enums.CallOutcome;
import com.flowzo.callingagent.enums.CallStatus;
import com.flowzo.callingagent.enums.ConversationStep;
import java.time.Instant;

/** Compact view of one call, used inside the lead detail response. */
public record LeadCallSummary(
        Long callId,
        CallStatus status,
        ConversationStep currentStep,
        CallOutcome outcome,
        String summary,
        Instant startedAt,
        Instant completedAt
) {
}
