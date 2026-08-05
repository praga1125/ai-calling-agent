package com.flowzo.callingagent.service.stt;

import java.nio.file.Path;

/** Converts a stored customer recording into text. */
public interface SpeechToTextService {

    /**
     * @param audioFile        recording saved on disk
     * @param originalFilename client supplied name, used to derive the audio mime type
     * @return the transcript, or an empty string when the clip contains no intelligible speech
     */
    String transcribe(Path audioFile, String originalFilename);
}
