package com.flowzo.callingagent.service;

import com.flowzo.callingagent.dto.SpeechTestResponse;
import com.flowzo.callingagent.service.stt.SpeechToTextService;
import com.flowzo.callingagent.service.tts.TextToSpeechService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Speaks a phrase with OpenAI TTS and transcribes the generated audio back with OpenAI STT.
 * A single call therefore proves that the API key, both models and the audio pipeline all work.
 */
@Service
public class SpeechDiagnosticsService {

    private final TextToSpeechService textToSpeechService;
    private final SpeechToTextService speechToTextService;
    private final AudioStorageService audioStorageService;

    public SpeechDiagnosticsService(
            TextToSpeechService textToSpeechService,
            SpeechToTextService speechToTextService,
            AudioStorageService audioStorageService
    ) {
        this.textToSpeechService = textToSpeechService;
        this.speechToTextService = speechToTextService;
        this.audioStorageService = audioStorageService;
    }

    public SpeechTestResponse roundTrip(String text) {
        Path audio = textToSpeechService.synthesize(text, "speech-test");
        String transcript = speechToTextService.transcribe(audio, audio.getFileName().toString());

        return new SpeechTestResponse(
                text,
                audioStorageService.toPublicUrl(audio),
                sizeOf(audio),
                transcript,
                similarity(text, transcript),
                !transcript.isBlank());
    }

    private long sizeOf(Path audio) {
        try {
            return Files.size(audio);
        } catch (IOException ex) {
            return -1;
        }
    }

    /** Word overlap between the spoken phrase and the transcript, as a 0-1 score. */
    private double similarity(String expected, String actual) {
        Set<String> expectedWords = words(expected);
        Set<String> actualWords = words(actual);
        if (expectedWords.isEmpty() || actualWords.isEmpty()) {
            return 0d;
        }
        Set<String> shared = new HashSet<>(expectedWords);
        shared.retainAll(actualWords);
        return Math.round((double) shared.size() / expectedWords.size() * 100) / 100d;
    }

    private Set<String> words(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ");
        return new HashSet<>(Arrays.asList(normalized.trim().split("\\s+")));
    }
}
