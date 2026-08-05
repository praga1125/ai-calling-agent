package com.flowzo.callingagent.dto;

import com.flowzo.callingagent.enums.CallOutcome;
import com.flowzo.callingagent.enums.CallStatus;
import com.flowzo.callingagent.enums.ConversationStep;
import com.flowzo.callingagent.enums.CustomerInputMode;

/**
 * @param customerTranscript what the customer said: transcribed by OpenAI for VOICE turns, or the
 *                           text as typed for TEXT turns
 * @param inputMode          which endpoint produced this turn, so a typed reply is never mistaken
 *                           for a transcription
 * @param audioUrl           the agent's reply as audio, or null when speech generation was skipped
 * @param captured           the qualification answers gathered so far, so the caller can show
 *                           progress during the call rather than only in the final summary
 */
public record CustomerResponseResult(
        Long callId,
        String customerTranscript,
        CustomerInputMode inputMode,
        String aiMessage,
        String audioUrl,
        ConversationStep currentStep,
        CallStatus status,
        CallOutcome outcome,
        boolean conversationCompleted,
        CapturedDetails captured
) {
}
