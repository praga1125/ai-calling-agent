package com.flowzo.callingagent.service.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowzo.callingagent.config.AppProperties;
import com.flowzo.callingagent.exception.SpeechServiceException;
import com.flowzo.callingagent.service.audio.AudioMimeTypes;
import com.flowzo.callingagent.service.openai.OpenAiClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Speech-to-Text through the OpenAI {@code /audio/transcriptions} endpoint: the recording is
 * uploaded as multipart form data and the model answers with the transcript as JSON.
 *
 * <p>Registered by {@code SpeechConfig} when {@code app.openai.stt-model} is a transcription model.
 */
public class OpenAiSpeechToTextService implements SpeechToTextService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiSpeechToTextService.class);

    private final AppProperties properties;
    private final OpenAiClient openAiClient;

    public OpenAiSpeechToTextService(AppProperties properties, OpenAiClient openAiClient) {
        this.properties = properties;
        this.openAiClient = openAiClient;
    }

    @Override
    public String transcribe(Path audioFile, String originalFilename) {
        AppProperties.OpenAi config = properties.getOpenai();
        String filename = originalFilename == null || originalFilename.isBlank()
                ? audioFile.getFileName().toString()
                : originalFilename;
        long bytes = sizeOf(audioFile);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", filePart(audioFile, filename));
        form.add("model", config.getSttModel());
        form.add("response_format", "json");
        if (config.getLanguage() != null && !config.getLanguage().isBlank()) {
            form.add("language", config.getLanguage());
        }

        JsonNode response = openAiClient.postMultipart("/audio/transcriptions", form);
        String transcript = response.path("text").asText("").replace('\n', ' ').trim();

        if (transcript.isEmpty()) {
            log.warn("OpenAI STT ({}) found no speech in {} bytes of {}",
                    config.getSttModel(), bytes, filename);
            return "";
        }
        log.info("OpenAI STT ({}) transcribed {} characters", config.getSttModel(), transcript.length());
        return transcript;
    }

    /**
     * OpenAI decides how to decode the upload from the filename, so the caller's name and extension
     * are preserved rather than the random one the file was stored under.
     */
    private HttpEntity<Resource> filePart(Path audioFile, String filename) {
        Resource resource = new FileSystemResource(audioFile) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(AudioMimeTypes.forFilename(filename)));
        return new HttpEntity<>(resource, headers);
    }

    private long sizeOf(Path audioFile) {
        long bytes;
        try {
            bytes = Files.size(audioFile);
        } catch (IOException ex) {
            throw new SpeechServiceException("Failed to read uploaded audio", ex);
        }
        if (bytes == 0) {
            throw new SpeechServiceException("Uploaded audio file is empty");
        }
        return bytes;
    }
}
