package com.flowzo.callingagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowzo.callingagent.config.AppProperties;
import com.flowzo.callingagent.support.FakeSpeechConfig;
import com.flowzo.callingagent.support.Recordings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeSpeechConfig.class)
class CallFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppProperties appProperties;

    @Test
    void endToEndConversationProducesDemoRequestedSummary() throws Exception {
        long leadId = createLead("Priya Sharma", "+919876543210", "Northwind Sales");

        MvcResult startResult = mockMvc.perform(post("/api/calls/start/" + leadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiMessage").value(Matchers.containsString("Flowzo CRM")))
                .andExpect(jsonPath("$.currentStep").value("AWAIT_INTEREST"))
                .andExpect(jsonPath("$.audioUrl").isNotEmpty())
                .andReturn();

        long callId = objectMapper.readTree(startResult.getResponse().getContentAsString()).get("callId").asLong();

        reply(callId, "Yes, I am interested in a CRM");
        reply(callId, "We have about 10 people in our sales team");
        reply(callId, "We currently manage leads in Excel");
        MvcResult finalTurn = reply(callId, "Yes, I would like a product demo");

        JsonNode finalBody = objectMapper.readTree(finalTurn.getResponse().getContentAsString());
        assertThat(finalBody.get("conversationCompleted").asBoolean()).isTrue();
        assertThat(finalBody.get("outcome").asText()).isEqualTo("DEMO_REQUESTED");

        mockMvc.perform(get("/api/calls/" + callId + "/conversation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(9))
                .andExpect(jsonPath("$.messages[0].speakerType").value("AI"))
                .andExpect(jsonPath("$.messages[1].speakerType").value("CUSTOMER"))
                .andExpect(jsonPath("$.messages[1].messageText").value("Yes, I am interested in a CRM"))
                .andExpect(jsonPath("$.messages[1].audioUrl").isNotEmpty())
                .andExpect(jsonPath("$.messages[8].sequenceNumber").value(9));

        mockMvc.perform(get("/api/calls/" + callId + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DEMO_REQUESTED"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.interestedInCrm").value(true))
                .andExpect(jsonPath("$.salesTeamSize").value(10))
                .andExpect(jsonPath("$.wantsDemo").value(true))
                .andExpect(jsonPath("$.summary").value(Matchers.containsString("Excel")));

        mockMvc.perform(get("/api/leads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + leadId + ")].status").value(Matchers.hasItem("QUALIFIED")));
    }

    @Test
    void notInterestedEndsCallEarly() throws Exception {
        long callId = startCall(createLead("Alex", "+15551212", "Acme"));

        mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(Recordings.saying("No, I am not interested")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputMode").value("VOICE"))
                .andExpect(jsonPath("$.outcome").value("NOT_INTERESTED"))
                .andExpect(jsonPath("$.conversationCompleted").value(true));

        mockMvc.perform(get("/api/calls/" + callId + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interestedInCrm").value(false))
                .andExpect(jsonPath("$.summary").value(Matchers.containsString("not interested")));
    }

    @Test
    void callbackRequestIsCaptured() throws Exception {
        long callId = startCall(createLead("Meera", "+15550000", "Zenith"));

        mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(Recordings.saying("I am busy, please call back later")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CALLBACK_REQUESTED"))
                .andExpect(jsonPath("$.conversationCompleted").value(true));
    }

    @Test
    void agentRepromptsWhenTeamSizeIsMissing() throws Exception {
        long callId = startCall(createLead("Ravi", "+15559999", "Orbit"));

        reply(callId, "Yes, I am interested");
        mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(Recordings.saying("quite a few people actually")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStep").value("AWAIT_TEAM_SIZE"))
                .andExpect(jsonPath("$.aiMessage").value(Matchers.containsString("approximate number")))
                .andExpect(jsonPath("$.conversationCompleted").value(false));
    }

    @Test
    void everyTurnReportsWhatHasBeenCapturedSoFar() throws Exception {
        long callId = startCall(createLead("Anita", "+15558888", "Vertex"));

        mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(Recordings.saying("Yes, I am interested in a CRM")))
                .andExpect(jsonPath("$.captured.interestedInCrm").value(true))
                .andExpect(jsonPath("$.captured.salesTeamSize").isEmpty())
                .andExpect(jsonPath("$.captured.wantsDemo").isEmpty());

        mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(Recordings.saying("We have 12 people on the sales team")))
                .andExpect(jsonPath("$.captured.interestedInCrm").value(true))
                .andExpect(jsonPath("$.captured.salesTeamSize").value(12));

        mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(Recordings.saying("We track everything in Excel today")))
                .andExpect(jsonPath("$.captured.leadManagementMethod")
                        .value(Matchers.containsString("Excel")));
    }

    @Test
    void aRefusedCallDisqualifiesTheLeadAndAcknowledgesIt() throws Exception {
        long leadId = createLead("Vikram", "+15554444", "Ridge");
        long callId = startCall(leadId);

        mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(Recordings.saying("No, I am not interested")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NOT_INTERESTED"))
                .andExpect(jsonPath("$.conversationCompleted").value(true))
                .andExpect(jsonPath("$.aiMessage").value(Matchers.startsWith("Okay")))
                .andExpect(jsonPath("$.captured.interestedInCrm").value(false));

        mockMvc.perform(get("/api/leads/" + leadId))
                .andExpect(jsonPath("$.status").value("DISQUALIFIED"));
    }

    @Test
    void anUnfinishedCallCanBeReopenedAtTheStepItStoppedAt() throws Exception {
        long leadId = createLead("Farah", "+15553333", "Kestrel");
        long callId = startCall(leadId);
        reply(callId, "Yes, I am interested");

        // What the UI reads when a lead is selected: the stage to resume at, then the stored turns.
        mockMvc.perform(get("/api/leads/" + leadId))
                .andExpect(jsonPath("$.calls[0].callId").value((int) callId))
                .andExpect(jsonPath("$.calls[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.calls[0].currentStep").value("AWAIT_TEAM_SIZE"));

        mockMvc.perform(get("/api/calls/" + callId + "/conversation"))
                .andExpect(jsonPath("$.messages.length()").value(3));

        mockMvc.perform(get("/api/calls/" + callId + "/summary"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.interestedInCrm").value(true));

        // Resuming means the same call continues rather than a new one starting.
        mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(Recordings.saying("We have 8 salespeople")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callId").value((int) callId))
                .andExpect(jsonPath("$.captured.salesTeamSize").value(8));
    }

    @Test
    void generatedAudioIsDownloadable() throws Exception {
        long callId = startCall(createLead("Sam", "+15551111", "Delta"));

        String audioUrl = objectMapper.readTree(
                        mockMvc.perform(get("/api/calls/" + callId + "/conversation"))
                                .andReturn().getResponse().getContentAsString())
                .get("messages").get(0).get("audioUrl").asText();

        String path = audioUrl.substring(audioUrl.indexOf("/api/audio/"));
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo("audio/wav"));
    }

    @Test
    void unrecognisableRecordingIsRejectedInsteadOfStored() throws Exception {
        long callId = startCall(createLead("Leela", "+15556666", "Aster"));

        mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(Recordings.saying(FakeSpeechConfig.SILENCE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("could not be read")))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("type the reply")));

        mockMvc.perform(get("/api/calls/" + callId + "/conversation"))
                .andExpect(jsonPath("$.messages.length()").value(1));
    }

    @Test
    void aRecordingThatFailedTranscriptionIsKeptForInspectionNotDeleted() throws Exception {
        long callId = startCall(createLead("Nadia", "+15557777", "Umbra"));

        mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(Recordings.saying(FakeSpeechConfig.SILENCE)))
                .andExpect(status().isBadRequest());

        // "Could not be read" and a genuinely silent take look identical from outside the call; the
        // file itself is the only way to tell them apart afterwards, so it must still be on disk.
        Path audioDir = Path.of(appProperties.getStorage().getAudioDir());
        try (Stream<Path> files = Files.list(audioDir)) {
            assertThat(files.anyMatch(p -> p.getFileName().toString().startsWith("unreadable-"))).isTrue();
        }
    }

    @Test
    void typedReplyIsStoredWithoutAudioAndStillGetsASpokenAnswer() throws Exception {
        long callId = startCall(createLead("Karthik", "+15557777", "Lumen"));

        mockMvc.perform(typedReply(callId, "Yes, I am interested in a CRM", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputMode").value("TEXT"))
                .andExpect(jsonPath("$.customerTranscript").value("Yes, I am interested in a CRM"))
                .andExpect(jsonPath("$.currentStep").value("AWAIT_TEAM_SIZE"))
                .andExpect(jsonPath("$.audioUrl").isNotEmpty());

        mockMvc.perform(get("/api/calls/" + callId + "/conversation"))
                .andExpect(jsonPath("$.messages[1].speakerType").value("CUSTOMER"))
                .andExpect(jsonPath("$.messages[1].messageText").value("Yes, I am interested in a CRM"))
                .andExpect(jsonPath("$.messages[1].audioUrl").isEmpty())
                .andExpect(jsonPath("$.messages[2].audioUrl").isNotEmpty());
    }

    @Test
    void typedReplyCanSkipSpeechGenerationWhileARateLimitResets() throws Exception {
        long callId = startCall(createLead("Divya", "+15558888", "Cobalt"));

        mockMvc.perform(typedReply(callId, "Yes, I am interested", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiMessage").isNotEmpty())
                .andExpect(jsonPath("$.audioUrl").isEmpty());

        mockMvc.perform(get("/api/calls/" + callId + "/conversation"))
                .andExpect(jsonPath("$.messages[2].speakerType").value("AI"))
                .andExpect(jsonPath("$.messages[2].audioUrl").isEmpty());
    }

    @Test
    void typedAndSpokenTurnsCanBeMixedInOneCall() throws Exception {
        long callId = startCall(createLead("Farhan", "+15554444", "Beacon"));

        mockMvc.perform(typedReply(callId, "Yes, I am interested in a CRM", null)).andExpect(status().isOk());
        reply(callId, "We have about 10 people in our sales team");
        mockMvc.perform(typedReply(callId, "We currently manage leads in Excel", null)).andExpect(status().isOk());

        mockMvc.perform(typedReply(callId, "Yes, I would like a product demo", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DEMO_REQUESTED"))
                .andExpect(jsonPath("$.conversationCompleted").value(true));

        mockMvc.perform(get("/api/calls/" + callId + "/summary"))
                .andExpect(jsonPath("$.salesTeamSize").value(10))
                .andExpect(jsonPath("$.wantsDemo").value(true));
    }

    @Test
    void blankTypedReplyIsRejected() throws Exception {
        long callId = startCall(createLead("Zoya", "+15551313", "Kite"));

        mockMvc.perform(typedReply(callId, "   ", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/calls/" + callId + "/conversation"))
                .andExpect(jsonPath("$.messages.length()").value(1));
    }

    @Test
    void validationAndLookupFailuresReturnStructuredErrors() throws Exception {
        mockMvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+1555\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/calls/start/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));

        mockMvc.perform(get("/api/calls/999999/conversation"))
                .andExpect(status().isNotFound());

        long callId = startCall(createLead("Nina", "+15552222", "Vertex"));
        mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(new MockMultipartFile("audio", "empty.wav", "audio/wav", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void completedCallRejectsFurtherResponses() throws Exception {
        long callId = startCall(createLead("Omar", "+15553333", "Helix"));
        reply(callId, "No, I am not interested");

        mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(Recordings.saying("Actually yes")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("already completed")));

        mockMvc.perform(typedReply(callId, "Actually yes", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("already completed")));
    }

    @Test
    void speechStatusReportsOpenAiConfiguration() throws Exception {
        mockMvc.perform(get("/api/speech/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("openai"))
                .andExpect(jsonPath("$.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.sttModel").isNotEmpty())
                .andExpect(jsonPath("$.ttsModel").isNotEmpty());
    }

    @Test
    void speechTestSynthesizesAndTranscribesBack() throws Exception {
        mockMvc.perform(post("/api/speech/test").param("text", "Hello from Flowzo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundTripSucceeded").value(true))
                .andExpect(jsonPath("$.transcript").value("Hello from Flowzo"))
                .andExpect(jsonPath("$.transcriptSimilarity").value(1.0));
    }

    private long createLead(String name, String phone, String company) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phoneNumber":"%s","companyName":"%s"}
                                """.formatted(name, phone, company)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long startCall(long leadId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/calls/start/" + leadId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("callId").asLong();
    }

    /** Builds a typed customer turn; a null {@code speak} leaves the endpoint on its default. */
    private MockHttpServletRequestBuilder typedReply(long callId, String text, Boolean speak) {
        MockHttpServletRequestBuilder request = post("/api/calls/" + callId + "/response/text")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"%s\"}".formatted(text));
        return speak == null ? request : request.param("speak", speak.toString());
    }

    private MvcResult reply(long callId, String spokenWords) throws Exception {
        return mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(Recordings.saying(spokenWords)))
                .andExpect(status().isOk())
                .andReturn();
    }
}
