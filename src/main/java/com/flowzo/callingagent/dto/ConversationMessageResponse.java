package com.flowzo.callingagent.dto;

import com.flowzo.callingagent.enums.SpeakerType;
import java.time.Instant;

public record ConversationMessageResponse(
        Long id,
        SpeakerType speakerType,
        String messageText,
        Integer sequenceNumber,
        String audioUrl,
        Instant timestamp
) {
}
