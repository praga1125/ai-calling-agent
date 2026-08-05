package com.flowzo.callingagent.dto;

/** Reports how speech is configured so the UI can warn before a call is started. */
public record SpeechStatusResponse(
        String provider,
        boolean apiKeyConfigured,
        String sttModel,
        String sttEndpoint,
        String ttsModel,
        String ttsVoice,
        String language,
        String note
) {
}
