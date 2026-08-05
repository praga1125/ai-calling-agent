package com.flowzo.callingagent.service.audio;

import java.nio.file.Path;
import java.util.Locale;

public final class AudioMimeTypes {

    private AudioMimeTypes() {
    }

    /** Maps a filename to a mime type accepted by the speech providers. */
    public static String forFilename(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".wav")) {
            return "audio/wav";
        }
        if (name.endsWith(".mp3")) {
            return "audio/mp3";
        }
        if (name.endsWith(".m4a") || name.endsWith(".aac")) {
            return "audio/aac";
        }
        if (name.endsWith(".ogg") || name.endsWith(".oga") || name.endsWith(".opus")) {
            return "audio/ogg";
        }
        if (name.endsWith(".flac")) {
            return "audio/flac";
        }
        if (name.endsWith(".aiff") || name.endsWith(".aif")) {
            return "audio/aiff";
        }
        if (name.endsWith(".webm")) {
            return "audio/webm";
        }
        return "audio/wav";
    }

    public static String forPath(Path path) {
        return forFilename(path == null ? null : path.getFileName().toString());
    }

    /** Browser-friendly content type used when streaming stored audio back to the client. */
    public static String forHttpResponse(String filename) {
        String mime = forFilename(filename);
        return "audio/mp3".equals(mime) ? "audio/mpeg" : mime;
    }
}
