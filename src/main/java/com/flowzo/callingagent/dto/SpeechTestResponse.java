package com.flowzo.callingagent.dto;

/**
 * Result of the TTS -> STT round trip check.
 *
 * @param transcriptSimilarity word overlap between the spoken phrase and the transcript (0-1)
 */
public record SpeechTestResponse(
        String spokenText,
        String audioUrl,
        long audioBytes,
        String transcript,
        double transcriptSimilarity,
        boolean roundTripSucceeded
) {
}
