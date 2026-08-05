package com.flowzo.callingagent.config;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Application settings bound from the {@code app.*} block of {@code application.yml}. */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Storage storage = new Storage();
    private final OpenAi openai = new OpenAi();

    /**
     * Prefix for generated audio URLs. Blank keeps the URLs relative, which works on any host
     * or port; set an absolute URL only when clients run on a different origin.
     */
    private String publicBaseUrl = "";

    public Storage getStorage() {
        return storage;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public static class Storage {
        private String audioDir = "./storage/audio";

        public String getAudioDir() {
            return audioDir;
        }

        public void setAudioDir(String audioDir) {
            this.audioDir = audioDir;
        }
    }

    public static class OpenAi {
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1";

        /**
         * Model that turns the customer's recording into text. Two families exist and they live on
         * different endpoints: transcription models (`gpt-4o-mini-transcribe`, `whisper-1`) on
         * `/audio/transcriptions`, and chat audio models (`gpt-audio-mini`, `gpt-4o-audio-preview`)
         * on `/chat/completions`. The right endpoint is chosen from the model name.
         */
        private String sttModel = "gpt-audio-mini";

        /** Speech generation model for {@code /audio/speech}. */
        private String ttsModel = "gpt-4o-mini-tts";

        private String ttsVoice = "alloy";

        /**
         * Tone guidance for the generated voice. Supported by {@code gpt-4o-mini-tts}; leave blank
         * when using {@code tts-1} or {@code tts-1-hd}, which reject the parameter.
         */
        private String ttsInstructions =
                "Speak as a warm, professional customer-support agent: clear, friendly and unhurried.";

        /** ISO-639-1 hint that improves transcription accuracy. Blank lets the model detect it. */
        private String language = "en";

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        /**
         * True when {@code stt-model} is a chat audio model such as {@code gpt-audio-mini}, which
         * accepts audio on {@code /chat/completions} instead of {@code /audio/transcriptions}.
         */
        public boolean usesChatAudioModel() {
            return sttModel != null && sttModel.toLowerCase(Locale.ROOT).contains("audio");
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getSttModel() {
            return sttModel;
        }

        public void setSttModel(String sttModel) {
            this.sttModel = sttModel;
        }

        public String getTtsModel() {
            return ttsModel;
        }

        public void setTtsModel(String ttsModel) {
            this.ttsModel = ttsModel;
        }

        public String getTtsVoice() {
            return ttsVoice;
        }

        public void setTtsVoice(String ttsVoice) {
            this.ttsVoice = ttsVoice;
        }

        public String getTtsInstructions() {
            return ttsInstructions;
        }

        public void setTtsInstructions(String ttsInstructions) {
            this.ttsInstructions = ttsInstructions;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }
    }
}
