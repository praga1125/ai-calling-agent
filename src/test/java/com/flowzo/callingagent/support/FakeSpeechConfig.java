package com.flowzo.callingagent.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.flowzo.callingagent.service.AudioStorageService;
import com.flowzo.callingagent.service.stt.SpeechToTextService;
import com.flowzo.callingagent.service.tts.TextToSpeechService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the OpenAI speech services during integration tests. An "audio file" here is simply a
 * UTF-8 text file holding the spoken words, so the whole call flow can run without network access
 * while still exercising the real controllers, services, engine and database.
 */
@TestConfiguration
public class FakeSpeechConfig {

    /** Upload this text to simulate a recording that contains no recognisable speech. */
    public static final String SILENCE = "SILENCE";

    @Bean
    @Primary
    TextToSpeechService fakeTextToSpeech(AudioStorageService audioStorageService) {
        return (text, filePrefix) -> audioStorageService.saveBytes(text.getBytes(UTF_8), filePrefix, ".wav");
    }

    @Bean
    @Primary
    SpeechToTextService fakeSpeechToText() {
        return (audioFile, originalFilename) -> {
            String spoken = read(audioFile);
            return SILENCE.equals(spoken) ? "" : spoken;
        };
    }

    private static String read(Path audioFile) {
        try {
            return Files.readString(audioFile, UTF_8).trim();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
