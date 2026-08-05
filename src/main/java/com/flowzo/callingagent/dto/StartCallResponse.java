package com.flowzo.callingagent.dto;

import com.flowzo.callingagent.enums.CallOutcome;
import com.flowzo.callingagent.enums.CallStatus;
import com.flowzo.callingagent.enums.ConversationStep;
import java.time.Instant;

/**
 * @param aiMessage the greeting text
 * @param audioUrl  the same greeting spoken by OpenAI TTS
 */
public record StartCallResponse(
        Long callId,
        Long leadId,
        String leadName,
        CallStatus status,
        ConversationStep currentStep,
        CallOutcome outcome,
        String aiMessage,
        String audioUrl,
        Instant startedAt
) {
}
