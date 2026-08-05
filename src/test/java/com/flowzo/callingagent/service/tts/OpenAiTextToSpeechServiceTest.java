package com.flowzo.callingagent.service.tts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.flowzo.callingagent.config.AppProperties;
import com.flowzo.callingagent.exception.SpeechServiceException;
import com.flowzo.callingagent.service.AudioStorageService;
import com.flowzo.callingagent.service.openai.OpenAiClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Verifies that the WAV returned by OpenAI is stored as a playable file. */
class OpenAiTextToSpeechServiceTest {

    private static final String BASE_URL = "https://openai.test/v1";
    private static final String SPEECH = BASE_URL + "/audio/speech";

    @TempDir
    Path tempDir;

    private MockRestServiceServer server;
    private OpenAiTextToSpeechService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getOpenai().setApiKey("test-key");
        properties.getOpenai().setBaseUrl(BASE_URL);
        properties.getOpenai().setTtsModel("tts-model");
        properties.getOpenai().setTtsVoice("alloy");
        properties.getOpenai().setTtsInstructions("Speak warmly.");
        properties.getStorage().setAudioDir(tempDir.toString());

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new OpenAiTextToSpeechService(
                properties, new OpenAiClient(properties, builder), new AudioStorageService(properties));
    }

    @Test
    void requestsWavAudioAndStoresAPlayableFile() throws Exception {
        server.expect(requestTo(SPEECH))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("tts-model"))
                .andExpect(jsonPath("$.voice").value("alloy"))
                .andExpect(jsonPath("$.response_format").value("wav"))
                .andExpect(jsonPath("$.instructions").value("Speak warmly."))
                .andExpect(jsonPath("$.input").value("Hello from Flowzo CRM"))
                .andRespond(withSuccess(wav(3200), MediaType.parseMediaType("audio/wav")));

        Path output = service.synthesize("Hello from Flowzo CRM", "call-1-ai");

        assertThat(output).exists().hasParent(tempDir);
        assertThat(output.getFileName().toString()).startsWith("call-1-ai-").endsWith(".wav");

        var format = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(Files.readAllBytes(output))).getFormat();
        assertThat(format.getSampleRate()).isEqualTo(24000f);
        server.verify();
    }

    @Test
    void synthesizesRepeatedAgentLinesOnlyOnce() throws Exception {
        server.expect(requestTo(SPEECH))
                .andRespond(withSuccess(wav(1600), MediaType.parseMediaType("audio/wav")));

        Path first = service.synthesize("Roughly how many people are on your sales team?", "call-1-ai");
        Path second = service.synthesize("Roughly how many people are on your sales team?", "call-2-ai");

        // A single expectation was set, so the second call cannot have reached the API.
        server.verify();
        assertThat(second).isNotEqualTo(first).exists();
        assertThat(Files.readAllBytes(second)).isEqualTo(Files.readAllBytes(first));
    }

    @Test
    void rejectsAResponseThatIsNotWavAudio() {
        server.expect(requestTo(SPEECH))
                .andRespond(withSuccess("not audio at all".getBytes(StandardCharsets.UTF_8),
                        MediaType.APPLICATION_OCTET_STREAM));

        assertThatThrownBy(() -> service.synthesize("Hello", "call-1-ai"))
                .isInstanceOf(SpeechServiceException.class)
                .hasMessageContaining("not WAV audio")
                .hasMessageContaining("response_format=wav");
    }

    @Test
    void reportsRateLimitsWithTheWaitTime() {
        server.expect(requestTo(SPEECH))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":{\"message\":\"Rate limit reached. Please try again in 400ms\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.synthesize("Hello", "call-1-ai"))
                .isInstanceOf(OpenAiClient.QuotaExceededException.class)
                .hasMessageContaining("Wait about 1 second");
    }

    @Test
    void rejectsEmptyText() {
        assertThatThrownBy(() -> service.synthesize("  ", "call-1-ai"))
                .isInstanceOf(SpeechServiceException.class);
    }

    /** A minimal 24 kHz mono 16-bit WAV, matching what the speech endpoint returns. */
    private byte[] wav(int pcmBytes) throws IOException {
        int sampleRate = 24000;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        header.putInt(36 + pcmBytes);
        header.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(sampleRate);
        header.putInt(sampleRate * 2);
        header.putShort((short) 2);
        header.putShort((short) 16);
        header.put("data".getBytes(StandardCharsets.US_ASCII));
        header.putInt(pcmBytes);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header.array());
        out.write(new byte[pcmBytes]);
        return out.toByteArray();
    }
}
