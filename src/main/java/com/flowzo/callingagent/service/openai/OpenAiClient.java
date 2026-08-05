package com.flowzo.callingagent.service.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowzo.callingagent.config.AppProperties;
import com.flowzo.callingagent.exception.SpeechServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin wrapper over the OpenAI audio endpoints, shared by the speech-to-text and text-to-speech
 * services. It owns the two things both directions need: the bearer token, and turning an OpenAI
 * error response into an exception the API layer knows how to report.
 */
@Component
public class OpenAiClient {

    private static final int MAX_ERROR_BODY_CHARS = 500;

    /** OpenAI reports rate limits as "Please try again in 1.5s" (or "in 200ms") in the error body. */
    private static final Pattern RETRY_DELAY_PATTERN = Pattern.compile("try again in ([0-9.]+)(ms|s)");

    private final AppProperties properties;
    private final RestClient restClient;

    public OpenAiClient(AppProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    /** Posts a multipart form (used by {@code /audio/transcriptions}) and parses the JSON reply. */
    public JsonNode postMultipart(String path, MultiValueMap<String, Object> parts) {
        try {
            return authorized(path)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::fail)
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new SpeechServiceException("OpenAI API call failed: " + ex.getMessage(), ex);
        }
    }

    /** Posts a JSON body and parses the JSON reply (used by {@code /chat/completions}). */
    public JsonNode postJson(String path, Map<String, Object> body) {
        try {
            return authorized(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::fail)
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new SpeechServiceException("OpenAI API call failed: " + ex.getMessage(), ex);
        }
    }

    /** Posts a JSON body to an endpoint that answers with an audio file ({@code /audio/speech}). */
    public byte[] postForAudio(String path, Map<String, Object> body) {
        try {
            return authorized(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::fail)
                    .body(byte[].class);
        } catch (RestClientException ex) {
            throw new SpeechServiceException("OpenAI API call failed: " + ex.getMessage(), ex);
        }
    }

    private RestClient.RequestBodySpec authorized(String path) {
        AppProperties.OpenAi config = properties.getOpenai();
        if (!config.hasApiKey()) {
            throw new MissingApiKeyException(
                    "OpenAI API key is missing. Export OPENAI_API_KEY or set app.openai.api-key.");
        }
        return restClient.post()
                .uri(config.getBaseUrl() + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey());
    }

    private void fail(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String body = readBody(response.getBody());

        // Only waiting or topping up billing helps here, so callers must not retry.
        if (status == 429) {
            throw new QuotaExceededException(quotaMessage(body));
        }
        // A project without access to a model answers 403 with model_not_found, which reads like an
        // auth failure but is fixed by changing the model, not the key.
        if (isModelNotFound(status, body)) {
            throw new SpeechServiceException("The OpenAI project cannot use the configured model (HTTP "
                    + status + "). List the models this key may use with: curl "
                    + "https://api.openai.com/v1/models -H \"Authorization: Bearer $OPENAI_API_KEY\" — then set "
                    + "app.openai.stt-model / app.openai.tts-model to one of them, or grant the model to the "
                    + "project at https://platform.openai.com/settings. " + body);
        }
        if (status == 401 || status == 403) {
            throw new SpeechServiceException("OpenAI rejected the API key (HTTP " + status
                    + "). Check that OPENAI_API_KEY is a current key for an account with audio access. " + body);
        }
        throw new SpeechServiceException("OpenAI API call failed: HTTP " + status + " " + body);
    }

    /** On the audio endpoints a 404 always means the model id, and a 403 sometimes does. */
    private boolean isModelNotFound(int status, String body) {
        return status == 404
                || (status == 403
                && (body.contains("model_not_found") || body.contains("does not have access to model")));
    }

    /** Turns OpenAI's two very different 429s into one actionable sentence. */
    private String quotaMessage(String errorBody) {
        if (errorBody.contains("insufficient_quota")) {
            return "The OpenAI account is out of credit (insufficient_quota). Waiting will not help: add "
                    + "billing at https://platform.openai.com/settings/organization/billing and try again.";
        }
        Matcher retryAfter = RETRY_DELAY_PATTERN.matcher(errorBody);
        if (!retryAfter.find()) {
            return "OpenAI rate limit reached. Wait for the rate-limit window to reset and try again.";
        }
        long seconds = "ms".equals(retryAfter.group(2))
                ? 1
                : Math.max(1, Math.round(Double.parseDouble(retryAfter.group(1))));
        return "OpenAI rate limit reached. Wait about " + seconds + (seconds == 1 ? " second" : " seconds")
                + " and try again.";
    }

    private String readBody(InputStream bodyStream) {
        String body = "";
        try {
            body = new String(bodyStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // reading the error body is best effort; the status code is the important part
        }
        if (body.length() > MAX_ERROR_BODY_CHARS) {
            body = body.substring(0, MAX_ERROR_BODY_CHARS) + "...";
        }
        return body;
    }

    /** Signals a configuration problem: no request can succeed until a key is provided. */
    public static class MissingApiKeyException extends SpeechServiceException {
        MissingApiKeyException(String message) {
            super(message);
        }
    }

    /** Signals a rate limit or an empty balance; retrying immediately can only make it worse. */
    public static class QuotaExceededException extends SpeechServiceException {
        QuotaExceededException(String message) {
            super(message);
        }
    }
}
