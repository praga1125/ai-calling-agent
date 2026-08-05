package com.flowzo.callingagent.controller;

import com.flowzo.callingagent.exception.ResourceNotFoundException;
import com.flowzo.callingagent.service.AudioStorageService;
import com.flowzo.callingagent.service.audio.AudioMimeTypes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audio")
@Tag(name = "Audio")
public class AudioController {

    private final AudioStorageService audioStorageService;

    public AudioController(AudioStorageService audioStorageService) {
        this.audioStorageService = audioStorageService;
    }

    @GetMapping("/{filename}")
    @Operation(summary = "Stream a stored TTS or customer audio file")
    public ResponseEntity<Resource> getAudio(@PathVariable String filename) {
        Path path = audioStorageService.resolveStoredFile(filename);
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("Audio file not found: " + filename);
        }
        // Each clip is written once under a unique name, so replaying one never needs a re-download.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .contentType(MediaType.parseMediaType(AudioMimeTypes.forHttpResponse(filename)))
                .body(new FileSystemResource(path));
    }
}
