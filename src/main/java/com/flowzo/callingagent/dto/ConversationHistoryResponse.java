package com.flowzo.callingagent.dto;

import com.flowzo.callingagent.enums.CallOutcome;
import com.flowzo.callingagent.enums.CallStatus;
import java.time.Instant;
import java.util.List;

public record ConversationHistoryResponse(
        Long callId,
        Long leadId,
        String leadName,
        CallStatus status,
        CallOutcome outcome,
        Instant startedAt,
        Instant completedAt,
        List<ConversationMessageResponse> messages
) {
}
