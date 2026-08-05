package com.flowzo.callingagent.controller;

import com.flowzo.callingagent.dto.CallSummaryResponse;
import com.flowzo.callingagent.dto.ConversationHistoryResponse;
import com.flowzo.callingagent.dto.CustomerResponseResult;
import com.flowzo.callingagent.dto.CustomerTextRequest;
import com.flowzo.callingagent.dto.StartCallResponse;
import com.flowzo.callingagent.service.CallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/calls")
@Tag(name = "Calls")
public class CallController {

    private final CallService callService;

    public CallController(CallService callService) {
        this.callService = callService;
    }

    @PostMapping("/start/{leadId}")
    @Operation(summary = "Start an AI voice conversation for a lead")
    public StartCallResponse startCall(@PathVariable Long leadId) {
        return callService.startCall(leadId);
    }

    @PostMapping(value = "/{callId}/response", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit the customer's recorded answer and receive the next AI message")
    public CustomerResponseResult submitResponse(
            @PathVariable Long callId,
            @RequestPart("audio") MultipartFile audio
    ) {
        return callService.submitCustomerResponse(callId, audio);
    }

    @PostMapping(value = "/{callId}/response/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit the customer's answer as text, for demos without a microphone",
            description = "Skips speech-to-text. Set speak=false to skip speech generation for the "
                    + "reply as well, which keeps a call moving while a rate limit resets.")
    public CustomerResponseResult submitTextResponse(
            @PathVariable Long callId,
            @Valid @RequestBody CustomerTextRequest request,
            @RequestParam(defaultValue = "true") boolean speak
    ) {
        return callService.submitCustomerText(callId, request.getText(), speak);
    }

    @GetMapping("/{callId}/conversation")
    @Operation(summary = "Retrieve complete conversation history")
    public ConversationHistoryResponse getConversation(@PathVariable Long callId) {
        return callService.getConversation(callId);
    }

    @GetMapping("/{callId}/summary")
    @Operation(summary = "Retrieve final call summary and outcome")
    public CallSummaryResponse getSummary(@PathVariable Long callId) {
        return callService.getSummary(callId);
    }
}
