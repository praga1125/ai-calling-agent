package com.flowzo.callingagent.service;

import com.flowzo.callingagent.config.AppProperties;
import com.flowzo.callingagent.exception.SpeechServiceException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AudioStorageService {

    private static final Logger log = LoggerFactory.getLogger(AudioStorageService.class);

    /** Recordings that failed transcription are kept under this prefix, not deleted, up to this many. */
    private static final String QUARANTINE_PREFIX = "unreadable-";
    private static final int QUARANTINE_LIMIT = 15;

    private final AppProperties properties;

    public AudioStorageService(AppProperties properties) {
        this.properties = properties;
    }

    public Path saveUpload(MultipartFile file, String prefix) {
        try {
            String original = file.getOriginalFilename() == null ? "audio.wav" : file.getOriginalFilename();
            int dot = original.lastIndexOf('.');
            String extension = dot >= 0 ? original.substring(dot) : "";
            Path target = newFile(prefix, extension);
            file.transferTo(target);
            return target;
        } catch (IOException ex) {
            throw new SpeechServiceException("Failed to store uploaded audio", ex);
        }
    }

    /** Stores generated audio (for example a synthesized AI message) under a unique name. */
    public Path saveBytes(byte[] content, String prefix, String extension) {
        try {
            Path target = newFile(prefix, extension);
            Files.write(target, content);
            return target;
        } catch (IOException ex) {
            throw new SpeechServiceException("Failed to store generated audio", ex);
        }
    }

    private Path newFile(String prefix, String extension) throws IOException {
        Path dir = Paths.get(properties.getStorage().getAudioDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        return dir.resolve(prefix + "-" + UUID.randomUUID() + extension);
    }

    /**
     * Builds the URL clients use to fetch an audio file. Leaving {@code app.public-base-url} blank
     * yields a relative URL, which keeps links valid on any host or port.
     */
    public String toPublicUrl(Path audioPath) {
        if (audioPath == null) {
            return null;
        }
        String base = properties.getPublicBaseUrl() == null ? "" : properties.getPublicBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/audio/" + audioPath.getFileName();
    }

    /** Best-effort cleanup: a missing or locked audio file must not fail the surrounding operation. */
    public void deleteQuietly(Path audioPath) {
        if (audioPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(audioPath);
        } catch (IOException ex) {
            log.warn("Could not delete audio file {}: {}", audioPath.getFileName(), ex.getMessage());
        }
    }

    /**
     * A recording that failed transcription is evidence, not clutter: without it, a report of
     * "could not be read" cannot be told apart from a genuinely silent take. Kept under a
     * recognisable prefix instead of its call ID, since by the time anyone looks the call may have
     * moved on or been deleted, and capped so a run of bad takes cannot fill the disk.
     */
    public void quarantine(Path audioPath) {
        if (audioPath == null || !Files.exists(audioPath)) {
            return;
        }
        try {
            Path renamed = audioPath.resolveSibling(QUARANTINE_PREFIX + audioPath.getFileName());
            Files.move(audioPath, renamed);
            log.warn("Kept unreadable recording for inspection: {}", renamed.getFileName());
            pruneQuarantine(audioPath.getParent());
        } catch (IOException ex) {
            log.warn("Could not quarantine unreadable audio file {}: {}", audioPath.getFileName(), ex.getMessage());
            deleteQuietly(audioPath);
        }
    }

    private void pruneQuarantine(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> quarantined = files
                    .filter(path -> path.getFileName().toString().startsWith(QUARANTINE_PREFIX))
                    .sorted(Comparator.comparing(this::lastModifiedOrEpoch).reversed())
                    .toList();
            quarantined.stream().skip(QUARANTINE_LIMIT).forEach(this::deleteQuietly);
        } catch (IOException ex) {
            log.warn("Could not list audio directory to prune quarantined files: {}", ex.getMessage());
        }
    }

    private java.time.Instant lastModifiedOrEpoch(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ex) {
            return java.time.Instant.EPOCH;
        }
    }

    public Path resolveStoredFile(String filename) {
        Path dir = Paths.get(properties.getStorage().getAudioDir()).toAbsolutePath().normalize();
        Path resolved = dir.resolve(filename).normalize();
        if (!resolved.startsWith(dir)) {
            throw new SpeechServiceException("Invalid audio path");
        }
        return resolved;
    }
}
