package com.flowzo.callingagent.service.tts;

import java.nio.file.Path;

/** Converts an AI message into a playable audio file. */
public interface TextToSpeechService {

    /**
     * @param filePrefix prefix for the generated file name, used to trace audio back to a call
     * @return path of the generated audio file inside the configured audio directory
     */
    Path synthesize(String text, String filePrefix);
}
