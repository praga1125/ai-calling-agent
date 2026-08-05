package com.flowzo.callingagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowzo.callingagent.enums.CallOutcome;
import com.flowzo.callingagent.enums.LeadStatus;
import com.flowzo.callingagent.support.FakeSpeechConfig;
import com.flowzo.callingagent.support.Recordings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The dashboard aggregates rows written by the other tests too, so every assertion is a delta
 * against the totals read before the scenario runs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeSpeechConfig.class)
class DashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void dashboardCountsLeadsByStageAndCallsByOutcome() throws Exception {
        JsonNode before = dashboard();

        long converted = createLead("Meera Nair", "+919100000001");
        mockMvc.perform(put("/api/leads/" + converted)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONVERTED\"}"))
                .andExpect(status().isOk());

        long disqualified = createLead("Rohit Menon", "+919100000002");
        long callId = startCall(disqualified);
        mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(Recordings.saying("No, I am not interested")))
                .andExpect(status().isOk());

        JsonNode after = dashboard();

        assertThat(after.get("totalLeads").asLong()).isEqualTo(before.get("totalLeads").asLong() + 2);
        assertThat(leadsIn(after, LeadStatus.CONVERTED)).isEqualTo(leadsIn(before, LeadStatus.CONVERTED) + 1);
        assertThat(leadsIn(after, LeadStatus.DISQUALIFIED)).isEqualTo(leadsIn(before, LeadStatus.DISQUALIFIED) + 1);
        assertThat(after.get("totalCalls").asLong()).isEqualTo(before.get("totalCalls").asLong() + 1);
        assertThat(after.get("completedCalls").asLong()).isEqualTo(before.get("completedCalls").asLong() + 1);
        assertThat(after.get("callsByOutcome").get(CallOutcome.NOT_INTERESTED.name()).asLong())
                .isEqualTo(before.get("callsByOutcome").get(CallOutcome.NOT_INTERESTED.name()).asLong() + 1);
    }

    @Test
    void dashboardAlwaysReportsEveryStageEvenWhenEmpty() throws Exception {
        JsonNode board = dashboard();

        for (LeadStatus status : LeadStatus.values()) {
            assertThat(board.get("leadsByStatus").has(status.name()))
                    .as("lead stage %s", status)
                    .isTrue();
        }
        for (CallOutcome outcome : CallOutcome.values()) {
            assertThat(board.get("callsByOutcome").has(outcome.name()))
                    .as("call outcome %s", outcome)
                    .isTrue();
        }
        assertThat(board.get("activeCalls").asLong() + board.get("completedCalls").asLong())
                .isEqualTo(board.get("totalCalls").asLong());
    }

    private JsonNode dashboard() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long leadsIn(JsonNode dashboard, LeadStatus status) {
        return dashboard.get("leadsByStatus").get(status.name()).asLong();
    }

    private long createLead(String name, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phoneNumber":"%s","companyName":"Dashboard Co"}
                                """.formatted(name, phone)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long startCall(long leadId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/calls/start/" + leadId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("callId").asLong();
    }
}
