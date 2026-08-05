package com.flowzo.callingagent.config;

import com.flowzo.callingagent.service.openai.OpenAiClient;
import com.flowzo.callingagent.service.stt.ChatAudioSpeechToTextService;
import com.flowzo.callingagent.service.stt.OpenAiSpeechToTextService;
import com.flowzo.callingagent.service.stt.SpeechToTextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpeechConfig {

    private static final Logger log = LoggerFactory.getLogger(SpeechConfig.class);

    /**
     * OpenAI serves transcription on two unrelated endpoints, and which one applies is decided by
     * the model: {@code gpt-4o-mini-transcribe} and {@code whisper-1} live on
     * {@code /audio/transcriptions}, while {@code gpt-audio-mini} and the other audio chat models
     * take the recording inline on {@code /chat/completions}. Projects are commonly granted one
     * family and not the other, so the endpoint follows {@code app.openai.stt-model} rather than
     * forcing a second setting to be kept in sync with it.
     */
    @Bean
    SpeechToTextService speechToTextService(AppProperties properties, OpenAiClient openAiClient) {
        AppProperties.OpenAi config = properties.getOpenai();
        if (config.usesChatAudioModel()) {
            log.info("Speech-to-text: {} on /chat/completions", config.getSttModel());
            return new ChatAudioSpeechToTextService(properties, openAiClient);
        }
        log.info("Speech-to-text: {} on /audio/transcriptions", config.getSttModel());
        return new OpenAiSpeechToTextService(properties, openAiClient);
    }
}
