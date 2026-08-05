package com.flowzo.callingagent.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.springframework.mock.web.MockMultipartFile;

/** Builds the multipart audio part used by the call response endpoint in tests. */
public final class Recordings {

    private Recordings() {
    }

    public static MockMultipartFile saying(String spokenWords) {
        return new MockMultipartFile("audio", "reply.wav", "audio/wav", spokenWords.getBytes(UTF_8));
    }
}
