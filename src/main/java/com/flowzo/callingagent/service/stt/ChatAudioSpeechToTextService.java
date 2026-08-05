package com.flowzo.callingagent.service.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowzo.callingagent.config.AppProperties;
import com.flowzo.callingagent.exception.SpeechServiceException;
import com.flowzo.callingagent.service.openai.OpenAiClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Speech-to-Text through a chat audio model such as {@code gpt-audio-mini}, which accepts the
 * recording inline on {@code /chat/completions} instead of on {@code /audio/transcriptions}.
 *
 * <p>Some OpenAI projects are granted the audio chat models but not the transcription models, and
 * this path keeps those keys working. The model is instructed to answer with the spoken words and
 * nothing else, so the result is used exactly like a transcription-endpoint transcript.
 */
public class ChatAudioSpeechToTextService implements SpeechToTextService {

    private static final Logger log = LoggerFactory.getLogger(ChatAudioSpeechToTextService.class);

    /** Chat audio input accepts these two containers only. */
    private static final Map<String, String> FORMATS = Map.of(
            ".wav", "wav",
            ".mp3", "mp3");

    private static final String NO_SPEECH = "NO_SPEECH";

    /**
     * Deliberately plain. Framing the job as analysing a "voice" or a "speaker" invites the model's
     * safety refusal ("I can't analyze or transcribe the audio based on a voice sample"), so the
     * instruction only ever asks for the words that were said.
     */
    private static final String SYSTEM_PROMPT = """
            Write out what is said in the attached clip, word for word, and nothing else: no quotation \
            marks, no translation, no summary, no comment, no apology, and no request for the clip — \
            it is already attached. If nothing is said, reply with %s and nothing else."""
            .formatted(NO_SPEECH);

    /**
     * A chat model that cannot make out the recording talks about the job instead of doing it:
     * "Sure, please go ahead and provide the audio recording", "I'm sorry, but I can't listen to or
     * transcribe audio directly". The wording varies endlessly, so rather than collecting phrases,
     * a reply counts as chatter when it mentions the transcription task itself *and* speaks as an
     * assistant about it. A customer discussing a CRM trips neither list on its own.
     */
    private static final List<String> TASK_WORDS = List.of(
            "transcribe", "transcription", "audio", "recording", "audio file");

    private static final List<String> ASSISTANT_WORDS = List.of(
            "i can't", "i cannot", "i can not", "i'm sorry", "i am sorry", "i'm unable", "i am unable",
            "i don't have", "i do not have", "i'm not able", "i am not able", "as an ai",
            "please provide", "please share", "please upload", "please send", "go ahead and",
            "let me know", "i can help", "i'd be happy", "i would be happy", "you'd like",
            "you would like", "provide a description", "i'll transcribe", "i will transcribe");

    /**
     * A last-resort framing, tried only after both worded prompts above have each been talked
     * around once. It drops the question entirely — the user turn is the audio and nothing else —
     * so there is no "What is said in this clip?" left for the model to chat back to instead of
     * answering it.
     */
    private static final String STRICT_SYSTEM_PROMPT = """
            You are a dictation engine, not a conversational assistant. The only message you will \
            ever receive is one audio clip and nothing else. Output exactly the words spoken in it, \
            verbatim, with no added punctuation, no comment, and no reply directed at the user. If \
            the clip has no speech, output %s and nothing else.""".formatted(NO_SPEECH);

    private final AppProperties properties;
    private final OpenAiClient openAiClient;

    public ChatAudioSpeechToTextService(AppProperties properties, OpenAiClient openAiClient) {
        this.properties = properties;
        this.openAiClient = openAiClient;
    }

    @Override
    public String transcribe(Path audioFile, String originalFilename) {
        AppProperties.OpenAi config = properties.getOpenai();
        String filename = originalFilename == null || originalFilename.isBlank()
                ? audioFile.getFileName().toString()
                : originalFilename;
        byte[] audio = read(audioFile);
        String encoded = Base64.getEncoder().encodeToString(audio);
        String format = formatOf(filename);

        List<Attempt> attempts = List.of(
                new Attempt(SYSTEM_PROMPT, instruction(config, true)),
                new Attempt(null, instruction(config, false)),
                new Attempt(STRICT_SYSTEM_PROMPT, null));

        String transcript = "";
        for (int i = 0; i < attempts.size(); i++) {
            Attempt attempt = attempts.get(i);
            transcript = ask(config, encoded, format, attempt);
            if (!isNotSpeech(transcript, attempt)) {
                break;
            }
            boolean lastAttempt = i == attempts.size() - 1;
            if (lastAttempt) {
                // The caller only needs to know the recording could not be used; which model said
                // what, and how to change it, belongs in the log, not in the user-facing error.
                log.warn("OpenAI chat audio STT ({}) refused {} times for {} and last answered \"{}\". Set "
                                + "OPENAI_STT_MODEL to a transcription model (whisper-1, gpt-4o-mini-transcribe) "
                                + "if the project is entitled to one.",
                        config.getSttModel(), attempts.size(), filename, shorten(transcript));
                return "";
            }
            log.warn("OpenAI chat audio STT ({}) did not transcribe {} on attempt {} of {} and answered \"{}\". "
                            + "Retrying with a different framing.",
                    config.getSttModel(), filename, i + 1, attempts.size(), shorten(transcript));
        }
        if (transcript.isEmpty()) {
            log.warn("OpenAI chat audio STT ({}) found no speech in {} bytes of {}",
                    config.getSttModel(), audio.length, filename);
            return "";
        }
        log.info("OpenAI chat audio STT ({}) transcribed {} characters",
                config.getSttModel(), transcript.length());
        return transcript;
    }

    /** One prompt variant to try: {@code userInstruction} is null when the user turn is audio only. */
    private record Attempt(String systemPrompt, String userInstruction) {
    }

    private String ask(AppProperties.OpenAi config, String encodedAudio, String format, Attempt attempt) {
        Map<String, Object> audioPart = Map.of("type", "input_audio", "input_audio", Map.of(
                "data", encodedAudio,
                "format", format));

        List<Object> userContent;
        if (attempt.userInstruction() == null) {
            userContent = List.of(audioPart);
        } else if (attempt.systemPrompt() == null) {
            // No system prompt to carry the framing, so the instruction leads with the audio itself.
            Map<String, Object> textPart = Map.of("type", "text", "text", attempt.userInstruction());
            userContent = List.of(audioPart, textPart);
        } else {
            Map<String, Object> textPart = Map.of("type", "text", "text", attempt.userInstruction());
            userContent = List.of(textPart, audioPart);
        }

        List<Object> messages = attempt.systemPrompt() == null
                ? List.of(Map.of("role", "user", "content", userContent))
                : List.of(
                Map.of("role", "system", "content", attempt.systemPrompt()),
                Map.of("role", "user", "content", userContent));

        JsonNode response = openAiClient.postJson("/chat/completions", Map.of(
                "model", config.getSttModel(),
                "modalities", List.of("text"),
                // Determinism, not creativity, is what turns a borderline reply into a usable one.
                "temperature", 0,
                "messages", messages));

        return clean(response.path("choices").path(0).path("message").path("content").asText(""));
    }

    /**
     * Phrased as a question on purpose. An imperative ("Repeat exactly what is said here") is
     * something a model that heard nothing will echo straight back, and an echo is indistinguishable
     * from a customer who happened to say those words.
     */
    private String instruction(AppProperties.OpenAi config, boolean framed) {
        if (!framed) {
            return "What is said in this clip?";
        }
        String language = config.getLanguage();
        return language == null || language.isBlank()
                ? "What is said in this clip? Answer with those words only."
                : "What is said in this clip? Answer with those words only. It is spoken in the "
                + "language with ISO code " + language + ".";
    }

    /** Strips the wrapper an instructed model still adds now and then. */
    private String clean(String content) {
        String transcript = content.replace('\n', ' ').trim();
        if (transcript.length() > 1 && transcript.startsWith("\"") && transcript.endsWith("\"")) {
            transcript = transcript.substring(1, transcript.length() - 1).trim();
        }
        return transcript.equalsIgnoreCase(NO_SPEECH) ? "" : transcript;
    }

    /** These replies run long, and the whole paragraph does not fit in a UI status line. */
    private String shorten(String transcript) {
        return transcript.length() <= 140 ? transcript : transcript.substring(0, 140) + "...";
    }

    /**
     * True when the reply is anything other than words the customer said. Silence is excluded: an
     * empty reply is a real answer to a quiet recording and must not cost a second API call.
     */
    private boolean isNotSpeech(String transcript, Attempt attempt) {
        if (transcript.isEmpty()) {
            return false;
        }
        if (isAssistantChatter(transcript)) {
            return true;
        }
        if (attempt.userInstruction() != null && isEcho(transcript, attempt.userInstruction())) {
            return true;
        }
        return attempt.systemPrompt() != null && isEcho(transcript, attempt.systemPrompt());
    }

    /**
     * A model that cannot hear the clip sometimes hands the prompt back instead of answering it.
     * Stored as a transcript, "What is said in this clip?" becomes the customer's turn, and the
     * agent then asks them to confirm an interest they already stated.
     */
    private boolean isEcho(String transcript, String prompt) {
        String spoken = words(transcript);
        String asked = words(prompt);
        if (spoken.equals(asked)) {
            return true;
        }
        // A fragment counts too, but only a long one: short answers share words with any question.
        return spoken.split(" ").length >= 4 && asked.contains(spoken);
    }

    private String words(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private boolean isAssistantChatter(String transcript) {
        // Models mix straight and typographic apostrophes, so "can’t" must match "can't".
        String lower = transcript.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        return TASK_WORDS.stream().anyMatch(lower::contains)
                && ASSISTANT_WORDS.stream().anyMatch(lower::contains);
    }

    private String formatOf(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        return FORMATS.entrySet().stream()
                .filter(entry -> name.endsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new SpeechServiceException(
                        "The " + properties.getOpenai().getSttModel() + " model accepts WAV or MP3 audio only, "
                                + "and " + filename + " is neither. Record with the browser page, which converts "
                                + "to WAV, or upload a .wav or .mp3 file."));
    }

    private byte[] read(Path audioFile) {
        byte[] audio;
        try {
            audio = Files.readAllBytes(audioFile);
        } catch (IOException ex) {
            throw new SpeechServiceException("Failed to read uploaded audio", ex);
        }
        if (audio.length == 0) {
            throw new SpeechServiceException("Uploaded audio file is empty");
        }
        return audio;
    }
}
