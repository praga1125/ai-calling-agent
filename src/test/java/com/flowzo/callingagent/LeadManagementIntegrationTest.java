package com.flowzo.callingagent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowzo.callingagent.support.FakeSpeechConfig;
import com.flowzo.callingagent.support.Recordings;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeSpeechConfig.class)
class LeadManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void leadCanBeReadUpdatedAndDeleted() throws Exception {
        long leadId = createLead("Kavya Rao", "+919000000001", "Bluepeak");

        mockMvc.perform(get("/api/leads/" + leadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kavya Rao"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.totalCalls").value(0))
                .andExpect(jsonPath("$.calls").isEmpty());

        mockMvc.perform(put("/api/leads/" + leadId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Bluepeak Sales\",\"status\":\"CONTACTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kavya Rao"))
                .andExpect(jsonPath("$.companyName").value("Bluepeak Sales"))
                .andExpect(jsonPath("$.status").value("CONTACTED"));

        mockMvc.perform(delete("/api/leads/" + leadId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/leads/" + leadId))
                .andExpect(status().isNotFound());
    }

    @Test
    void leadDetailListsCallHistoryWithOutcome() throws Exception {
        long leadId = createLead("Dev Patel", "+919000000002", "Skyline");
        long callId = startCall(leadId);

        mockMvc.perform(multipart("/api/calls/" + callId + "/response")
                        .file(Recordings.saying("No, I am not interested")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/leads/" + leadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISQUALIFIED"))
                .andExpect(jsonPath("$.totalCalls").value(1))
                .andExpect(jsonPath("$.calls[0].callId").value((int) callId))
                .andExpect(jsonPath("$.calls[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.calls[0].outcome").value("NOT_INTERESTED"))
                .andExpect(jsonPath("$.calls[0].summary").value(Matchers.containsString("not interested")));
    }

    @Test
    void deletingALeadRemovesItsConversationHistory() throws Exception {
        long leadId = createLead("Ishaan Roy", "+919000000003", "Cobalt");
        long callId = startCall(leadId);

        mockMvc.perform(get("/api/calls/" + callId + "/conversation"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/leads/" + leadId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/calls/" + callId + "/conversation"))
                .andExpect(status().isNotFound());
    }

    @Test
    void startingASecondCallClosesThePreviousOne() throws Exception {
        long leadId = createLead("Neha Verma", "+919000000004", "Quartz");
        long firstCall = startCall(leadId);
        long secondCall = startCall(leadId);

        mockMvc.perform(get("/api/calls/" + firstCall + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.summary").value(Matchers.containsString("closed automatically")));

        mockMvc.perform(get("/api/calls/" + secondCall + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/leads/" + leadId))
                .andExpect(jsonPath("$.totalCalls").value(2));
    }

    @Test
    void updateRejectsBlankRequiredFieldsAndUnknownLeads() throws Exception {
        long leadId = createLead("Arun Kumar", "+919000000005", "Nimbus");

        mockMvc.perform(put("/api/leads/" + leadId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("name must not be blank")));

        mockMvc.perform(put("/api/leads/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONVERTED\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/leads/999999"))
                .andExpect(status().isNotFound());
    }

    private long createLead(String name, String phone, String company) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","phoneNumber":"%s","companyName":"%s"}
                                """.formatted(name, phone, company)))
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
