package com.flowzo.callingagent.service.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.flowzo.callingagent.config.AppProperties;
import com.flowzo.callingagent.exception.SpeechServiceException;
import com.flowzo.callingagent.service.openai.OpenAiClient;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Verifies the OpenAI transcription contract without calling the real API. */
class OpenAiSpeechToTextServiceTest {

    private static final String BASE_URL = "https://openai.test/v1";
    private static final String TRANSCRIPTIONS = BASE_URL + "/audio/transcriptions";

    @TempDir
    Path tempDir;

    private AppProperties properties;
    private MockRestServiceServer server;
    private OpenAiSpeechToTextService service;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.getOpenai().setApiKey("test-key");
        properties.getOpenai().setBaseUrl(BASE_URL);
        properties.getOpenai().setSttModel("stt-model");
        properties.getOpenai().setLanguage("en");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new OpenAiSpeechToTextService(properties, new OpenAiClient(properties, builder));
    }

    @Test
    void uploadsTheRecordingAsMultipartAndReturnsTheTranscript() throws Exception {
        Path audio = writeAudio();

        server.expect(requestTo(TRANSCRIPTIONS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                // The filename is what OpenAI uses to detect the container, so it must survive.
                .andExpect(content().string(Matchers.containsString("filename=\"reply.wav\"")))
                .andExpect(content().string(Matchers.containsString("stt-model")))
                .andExpect(content().string(Matchers.containsString("en")))
                .andRespond(withSuccess("{\"text\":\"Yes, I am interested\"}", MediaType.APPLICATION_JSON));

        assertThat(service.transcribe(audio, "reply.wav")).isEqualTo("Yes, I am interested");
        server.verify();
    }

    @Test
    void returnsEmptyTranscriptWhenTheClipHasNoSpeech() throws Exception {
        Path audio = writeAudio();

        server.expect(requestTo(TRANSCRIPTIONS))
                .andRespond(withSuccess("{\"text\":\"  \"}", MediaType.APPLICATION_JSON));

        assertThat(service.transcribe(audio, "reply.wav")).isEmpty();
    }

    @Test
    void reportsRateLimitsWithTheWaitTime() throws Exception {
        Path audio = writeAudio();

        server.expect(requestTo(TRANSCRIPTIONS))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":{\"message\":\"Rate limit reached. Please try again in 8.5s\","
                                + "\"code\":\"rate_limit_exceeded\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.transcribe(audio, "reply.wav"))
                .isInstanceOf(OpenAiClient.QuotaExceededException.class)
                .hasMessageContaining("Wait about 9 seconds");
        server.verify();
    }

    @Test
    void explainsAnEmptyBalanceInsteadOfSuggestingAWait() throws Exception {
        Path audio = writeAudio();

        server.expect(requestTo(TRANSCRIPTIONS))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":{\"message\":\"You exceeded your current quota\","
                                + "\"type\":\"insufficient_quota\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.transcribe(audio, "reply.wav"))
                .isInstanceOf(OpenAiClient.QuotaExceededException.class)
                .hasMessageContaining("out of credit")
                .hasMessageContaining("billing");
    }

    @Test
    void namesTheConfigurationKeyWhenTheModelIsUnknown() throws Exception {
        Path audio = writeAudio();

        server.expect(requestTo(TRANSCRIPTIONS))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"error\":{\"message\":\"The model 'stt-model' does not exist\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.transcribe(audio, "reply.wav"))
                .isInstanceOf(SpeechServiceException.class)
                .hasMessageContaining("app.openai.stt-model");
    }

    @Test
    void failsClearlyWhenTheApiKeyIsMissing() throws Exception {
        properties.getOpenai().setApiKey("");
        Path audio = writeAudio();

        assertThatThrownBy(() -> service.transcribe(audio, "reply.wav"))
                .isInstanceOf(SpeechServiceException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    private Path writeAudio() throws Exception {
        Path audio = tempDir.resolve("stored-reply.wav");
        Files.write(audio, new byte[]{1, 2, 3, 4});
        return audio;
    }
}
