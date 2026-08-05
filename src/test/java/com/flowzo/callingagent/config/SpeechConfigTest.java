package com.flowzo.callingagent.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowzo.callingagent.service.openai.OpenAiClient;
import com.flowzo.callingagent.service.stt.ChatAudioSpeechToTextService;
import com.flowzo.callingagent.service.stt.OpenAiSpeechToTextService;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** The model name alone decides which OpenAI endpoint hears the customer. */
class SpeechConfigTest {

    private final SpeechConfig config = new SpeechConfig();

    @Test
    void chatAudioModelsUseTheChatEndpoint() {
        assertThat(serviceFor("gpt-audio-mini")).isInstanceOf(ChatAudioSpeechToTextService.class);
        assertThat(serviceFor("gpt-4o-audio-preview")).isInstanceOf(ChatAudioSpeechToTextService.class);
    }

    @Test
    void transcriptionModelsUseTheTranscriptionEndpoint() {
        assertThat(serviceFor("gpt-4o-mini-transcribe")).isInstanceOf(OpenAiSpeechToTextService.class);
        assertThat(serviceFor("whisper-1")).isInstanceOf(OpenAiSpeechToTextService.class);
    }

    private Object serviceFor(String sttModel) {
        AppProperties properties = new AppProperties();
        properties.getOpenai().setSttModel(sttModel);
        return config.speechToTextService(properties, new OpenAiClient(properties, RestClient.builder()));
    }
}
