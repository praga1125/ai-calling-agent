package com.flowzo.callingagent.controller;

import com.flowzo.callingagent.dto.FollowUpRequest;
import com.flowzo.callingagent.dto.FollowUpResponse;
import com.flowzo.callingagent.service.FollowUpService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/follow-ups")
@Tag(name = "Follow-ups")
public class FollowUpController {

    private final FollowUpService followUpService;

    public FollowUpController(FollowUpService followUpService) {
        this.followUpService = followUpService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Schedule a follow-up call for a lead")
    public FollowUpResponse create(@Valid @RequestBody FollowUpRequest request) {
        return followUpService.create(request);
    }

    @GetMapping
    @Operation(summary = "List follow-ups, optionally filtered by lead")
    public List<FollowUpResponse> list(@RequestParam(required = false) Long leadId) {
        if (leadId != null) {
            return followUpService.getByLeadId(leadId);
        }
        return followUpService.getAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one follow-up")
    public FollowUpResponse get(@PathVariable Long id) {
        return followUpService.getById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a pending follow-up")
    public FollowUpResponse update(@PathVariable Long id, @Valid @RequestBody FollowUpRequest request) {
        return followUpService.update(id, request);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Mark a pending follow-up as completed")
    public FollowUpResponse complete(@PathVariable Long id) {
        return followUpService.complete(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending follow-up")
    public FollowUpResponse cancel(@PathVariable Long id) {
        return followUpService.cancel(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a follow-up")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        followUpService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
