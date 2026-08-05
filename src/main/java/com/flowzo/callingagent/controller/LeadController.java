package com.flowzo.callingagent.controller;

import com.flowzo.callingagent.dto.CreateLeadRequest;
import com.flowzo.callingagent.dto.LeadDetailResponse;
import com.flowzo.callingagent.dto.LeadResponse;
import com.flowzo.callingagent.dto.UpdateLeadRequest;
import com.flowzo.callingagent.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leads")
@Tag(name = "Leads")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a lead")
    public LeadResponse createLead(@Valid @RequestBody CreateLeadRequest request) {
        return leadService.createLead(request);
    }

    @GetMapping
    @Operation(summary = "List all leads")
    public List<LeadResponse> listLeads() {
        return leadService.listLeads();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one lead with its call history")
    public LeadDetailResponse getLead(@PathVariable Long id) {
        return leadService.getLead(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update lead details or status")
    public LeadResponse updateLead(@PathVariable Long id, @RequestBody UpdateLeadRequest request) {
        return leadService.updateLead(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a lead with its calls, conversation history, and audio files")
    public ResponseEntity<Void> deleteLead(@PathVariable Long id) {
        leadService.deleteLead(id);
        return ResponseEntity.noContent().build();
    }
}
