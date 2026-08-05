package com.flowzo.callingagent.service.tts;

import com.flowzo.callingagent.config.AppProperties;
import com.flowzo.callingagent.exception.SpeechServiceException;
import com.flowzo.callingagent.service.AudioStorageService;
import com.flowzo.callingagent.service.openai.OpenAiClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Text-to-Speech through the OpenAI {@code /audio/speech} endpoint. The response is requested as
 * WAV, so the bytes can be written straight to disk and played by any browser.
 */
@Service
public class OpenAiTextToSpeechService implements TextToSpeechService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTextToSpeechService.class);
    private static final int MAX_CACHED_CLIPS = 32;
    private static final byte[] WAV_MAGIC = "RIFF".getBytes(StandardCharsets.US_ASCII);

    private final AppProperties properties;
    private final OpenAiClient openAiClient;
    private final AudioStorageService audioStorageService;

    /**
     * The agent asks the same scripted questions on every call, so identical text is synthesized
     * once and the audio reused. Speech is billed per character, and this keeps a demo well inside
     * the account's rate limit. Bounded so long-running instances cannot grow without limit.
     */
    private final Map<String, byte[]> audioCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                    return size() > MAX_CACHED_CLIPS;
                }
            });

    public OpenAiTextToSpeechService(
            AppProperties properties,
            OpenAiClient openAiClient,
            AudioStorageService audioStorageService
    ) {
        this.properties = properties;
        this.openAiClient = openAiClient;
        this.audioStorageService = audioStorageService;
    }

    @Override
    public Path synthesize(String text, String filePrefix) {
        if (text == null || text.isBlank()) {
            throw new SpeechServiceException("Cannot synthesize empty text");
        }
        AppProperties.OpenAi config = properties.getOpenai();
        String cacheKey = config.getTtsModel() + '|' + config.getTtsVoice() + '|' + text;

        byte[] wav = audioCache.get(cacheKey);
        if (wav == null) {
            wav = requestSpeech(config, text);
            audioCache.put(cacheKey, wav);
        } else {
            log.info("Reusing cached OpenAI audio for a repeated agent line ({} bytes)", wav.length);
        }

        // Each message gets its own file so deleting one call never breaks another call's history.
        return audioStorageService.saveBytes(wav, filePrefix, ".wav");
    }

    private byte[] requestSpeech(AppProperties.OpenAi config, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getTtsModel());
        body.put("input", text);
        body.put("voice", config.getTtsVoice());
        body.put("response_format", "wav");
        if (config.getTtsInstructions() != null && !config.getTtsInstructions().isBlank()) {
            body.put("instructions", config.getTtsInstructions());
        }

        byte[] audio = openAiClient.postForAudio("/audio/speech", body);
        if (audio == null || audio.length == 0) {
            throw new SpeechServiceException(
                    "OpenAI TTS returned no audio for model '" + config.getTtsModel() + "'");
        }
        if (!isWav(audio)) {
            throw new SpeechServiceException("OpenAI TTS returned " + audio.length
                    + " bytes that are not WAV audio, so the file would not play. Check that model '"
                    + config.getTtsModel() + "' supports response_format=wav.");
        }
        log.info("OpenAI TTS ({}, voice {}) generated {} bytes of audio",
                config.getTtsModel(), config.getTtsVoice(), audio.length);
        return audio;
    }

    private static boolean isWav(byte[] audio) {
        if (audio.length < WAV_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < WAV_MAGIC.length; i++) {
            if (audio[i] != WAV_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
