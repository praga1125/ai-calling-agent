package com.flowzo.callingagent.controller;

import com.flowzo.callingagent.config.AppProperties;
import com.flowzo.callingagent.dto.SpeechStatusResponse;
import com.flowzo.callingagent.dto.SpeechTestResponse;
import com.flowzo.callingagent.service.SpeechDiagnosticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/speech")
@Tag(name = "Speech")
public class SpeechController {

    private final AppProperties properties;
    private final SpeechDiagnosticsService diagnosticsService;

    public SpeechController(AppProperties properties, SpeechDiagnosticsService diagnosticsService) {
        this.properties = properties;
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping("/status")
    @Operation(summary = "Report the OpenAI speech configuration and whether the API key is present")
    public SpeechStatusResponse status() {
        AppProperties.OpenAi openai = properties.getOpenai();
        String note = openai.hasApiKey()
                ? "OpenAI speech is configured. Calls use OpenAI for both transcription and voice."
                : "OPENAI_API_KEY is not set, so calls will fail. Export the key and restart the app.";

        return new SpeechStatusResponse(
                "openai",
                openai.hasApiKey(),
                openai.getSttModel(),
                openai.usesChatAudioModel() ? "/chat/completions" : "/audio/transcriptions",
                openai.getTtsModel(),
                openai.getTtsVoice(),
                openai.getLanguage(),
                note);
    }

    @PostMapping("/test")
    @Operation(summary = "Synthesize a phrase then transcribe it back to verify the OpenAI API key")
    public SpeechTestResponse test(
            @RequestParam(defaultValue = "Yes, I am interested in a CRM for my sales team") String text) {
        return diagnosticsService.roundTrip(text);
    }
}
