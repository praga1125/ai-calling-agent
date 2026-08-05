package com.flowzo.callingagent.service.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.flowzo.callingagent.config.AppProperties;
import com.flowzo.callingagent.exception.SpeechServiceException;
import com.flowzo.callingagent.service.openai.OpenAiClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Verifies transcription through a chat audio model, the path used by projects that are granted
 * {@code gpt-audio-mini} but not the transcription models.
 */
class ChatAudioSpeechToTextServiceTest {

    private static final String BASE_URL = "https://openai.test/v1";
    private static final String CHAT = BASE_URL + "/chat/completions";

    @TempDir
    Path tempDir;

    private AppProperties properties;
    private MockRestServiceServer server;
    private ChatAudioSpeechToTextService service;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.getOpenai().setApiKey("test-key");
        properties.getOpenai().setBaseUrl(BASE_URL);
        properties.getOpenai().setSttModel("gpt-audio-mini");
        properties.getOpenai().setLanguage("en");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new ChatAudioSpeechToTextService(properties, new OpenAiClient(properties, builder));
    }

    @Test
    void sendsTheRecordingInlineAsBase64AndReturnsTheSpokenWords() throws Exception {
        Path audio = writeAudio("hello there");

        server.expect(requestTo(CHAT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("gpt-audio-mini"))
                .andExpect(jsonPath("$.modalities[0]").value("text"))
                .andExpect(jsonPath("$.messages[1].content[1].input_audio.format").value("wav"))
                .andExpect(jsonPath("$.messages[1].content[1].input_audio.data")
                        .value(Base64.getEncoder().encodeToString("hello there".getBytes(StandardCharsets.UTF_8))))
                .andRespond(withSuccess(reply("Yes, I am interested"), MediaType.APPLICATION_JSON));

        assertThat(service.transcribe(audio, "reply.wav")).isEqualTo("Yes, I am interested");
        server.verify();
    }

    @Test
    void stripsQuotesTheModelWrapsAroundTheTranscript() throws Exception {
        Path audio = writeAudio("spoken");

        server.expect(requestTo(CHAT))
                .andRespond(withSuccess(reply("\\\"We use Excel today\\\""), MediaType.APPLICATION_JSON));

        assertThat(service.transcribe(audio, "reply.wav")).isEqualTo("We use Excel today");
    }

    @Test
    void reportsNoSpeechAsAnEmptyTranscript() throws Exception {
        Path audio = writeAudio("silence");

        server.expect(requestTo(CHAT))
                .andRespond(withSuccess(reply("NO_SPEECH"), MediaType.APPLICATION_JSON));

        assertThat(service.transcribe(audio, "reply.wav")).isEmpty();
    }

    @Test
    void retriesWhenTheModelAnswersTheInstructionInsteadOfTranscribing() throws Exception {
        Path audio = writeAudio("spoken");

        server.expect(requestTo(CHAT)).andRespond(withSuccess(
                reply("Sure, please go ahead and provide the audio recording, and I'll transcribe it for you."),
                MediaType.APPLICATION_JSON));
        // The retry drops the framing and leads with the audio itself.
        server.expect(requestTo(CHAT))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].content[0].type").value("input_audio"))
                .andRespond(withSuccess(reply("We have twelve salespeople"), MediaType.APPLICATION_JSON));

        assertThat(service.transcribe(audio, "reply.wav")).isEqualTo("We have twelve salespeople");
        server.verify();
    }

    @Test
    void fallsBackToTheStrictAudioOnlyFramingWhenTheFirstTwoAreTalkedAround() throws Exception {
        Path audio = writeAudio("spoken");

        String chatter = reply("Sure, please go ahead and provide the audio recording, and I'll transcribe it.");
        server.expect(requestTo(CHAT)).andRespond(withSuccess(chatter, MediaType.APPLICATION_JSON));
        server.expect(requestTo(CHAT)).andRespond(withSuccess(chatter, MediaType.APPLICATION_JSON));
        // The third attempt has no question in the user turn at all, just the clip.
        server.expect(requestTo(CHAT))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[1].content.length()").value(1))
                .andExpect(jsonPath("$.messages[1].content[0].type").value("input_audio"))
                .andRespond(withSuccess(reply("We have twelve salespeople"), MediaType.APPLICATION_JSON));

        assertThat(service.transcribe(audio, "reply.wav")).isEqualTo("We have twelve salespeople");
        server.verify();
    }

    @Test
    void treatsAThriceRefusedRecordingAsUnusableWithoutRepeatingTheRefusal() throws Exception {
        Path audio = writeAudio("spoken");

        // Typographic apostrophes and fresh wording must not slip past the check.
        String chatter = reply("I\\u2019m sorry, but I can\\u2019t analyze or transcribe the audio based on a "
                + "voice sample. If you can provide a description, I can help.");
        server.expect(requestTo(CHAT)).andRespond(withSuccess(chatter, MediaType.APPLICATION_JSON));
        server.expect(requestTo(CHAT)).andRespond(withSuccess(chatter, MediaType.APPLICATION_JSON));
        server.expect(requestTo(CHAT)).andRespond(withSuccess(chatter, MediaType.APPLICATION_JSON));

        // The model's excuse is logged for whoever runs the app, never handed back as the answer.
        assertThat(service.transcribe(audio, "reply.wav")).isEmpty();
        server.verify();
    }

    @Test
    void neverStoresTheInstructionTheModelHandedBack() throws Exception {
        Path audio = writeAudio("spoken");

        // Left alone, "What is said in this clip?" becomes the customer's turn, and a caller who
        // just said "yes" is asked to confirm the interest they had already given.
        server.expect(requestTo(CHAT)).andRespond(withSuccess(
                reply("What is said in this clip? Answer with those words only."),
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(CHAT)).andRespond(withSuccess(
                reply("What is said in this clip?"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(CHAT)).andRespond(withSuccess(reply("NO_SPEECH"), MediaType.APPLICATION_JSON));

        assertThat(service.transcribe(audio, "reply.wav")).isEmpty();
        server.verify();
    }

    @Test
    void keepsAShortAnswerThatOverlapsTheQuestionItAnswers() throws Exception {
        Path audio = writeAudio("spoken");

        server.expect(requestTo(CHAT))
                .andRespond(withSuccess(reply("What is that"), MediaType.APPLICATION_JSON));

        assertThat(service.transcribe(audio, "reply.wav")).isEqualTo("What is that");
    }

    @Test
    void keepsACustomerAnswerThatHappensToMentionRecordings() throws Exception {
        Path audio = writeAudio("spoken");

        server.expect(requestTo(CHAT)).andRespond(withSuccess(
                reply("We keep a recording of every sales call in a shared folder"),
                MediaType.APPLICATION_JSON));

        assertThat(service.transcribe(audio, "reply.wav"))
                .isEqualTo("We keep a recording of every sales call in a shared folder");
    }

    @Test
    void keepsARealAnswerThatMerelySoundsPolite() throws Exception {
        Path audio = writeAudio("spoken");

        server.expect(requestTo(CHAT))
                .andRespond(withSuccess(reply("Sure, we are interested in a CRM"), MediaType.APPLICATION_JSON));

        assertThat(service.transcribe(audio, "reply.wav")).isEqualTo("Sure, we are interested in a CRM");
    }

    @Test
    void refusesContainersTheChatEndpointCannotRead() throws Exception {
        Path audio = writeAudio("opus bytes");

        assertThatThrownBy(() -> service.transcribe(audio, "reply.webm"))
                .isInstanceOf(SpeechServiceException.class)
                .hasMessageContaining("WAV or MP3");
    }

    @Test
    void explainsThatTheProjectLacksTheModelWhenOpenAiAnswers403() throws Exception {
        Path audio = writeAudio("hello");

        server.expect(requestTo(CHAT))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("{\"error\":{\"message\":\"Project `proj_x` does not have access to model "
                                + "`gpt-audio-mini`\",\"code\":\"model_not_found\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.transcribe(audio, "reply.wav"))
                .isInstanceOf(SpeechServiceException.class)
                .hasMessageContaining("cannot use the configured model")
                .hasMessageContaining("app.openai.stt-model");
    }

    @Test
    void rejectsAnEmptyRecording() throws Exception {
        Path audio = tempDir.resolve("empty.wav");
        Files.write(audio, new byte[0]);

        assertThatThrownBy(() -> service.transcribe(audio, "empty.wav"))
                .isInstanceOf(SpeechServiceException.class)
                .hasMessageContaining("empty");
    }

    private String reply(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + content + "\"}}]}";
    }

    private Path writeAudio(String bytes) throws Exception {
        Path audio = tempDir.resolve("reply.wav");
        Files.writeString(audio, bytes);
        return audio;
    }
}
