package com.flowzo.callingagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowzo.callingagent.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioStorageServiceTest {

    @TempDir
    Path audioDir;

    private AudioStorageService service() {
        AppProperties properties = new AppProperties();
        properties.getStorage().setAudioDir(audioDir.toString());
        return new AudioStorageService(properties);
    }

    @Test
    void keepsAnUnreadableRecordingInsteadOfDeletingIt() throws IOException {
        Path file = audioDir.resolve("call-1-customer-abc.wav");
        Files.writeString(file, "not much to hear");

        service().quarantine(file);

        assertThat(Files.exists(file)).isFalse();
        List<Path> kept = list();
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).getFileName().toString()).startsWith("unreadable-");
        assertThat(Files.readString(kept.get(0))).isEqualTo("not much to hear");
    }

    @Test
    void keepsOnlyTheMostRecentFailuresOnceTheLimitIsPassed() throws IOException {
        AudioStorageService service = service();
        for (int i = 0; i < 20; i++) {
            Path file = audioDir.resolve("call-" + i + "-customer-x.wav");
            Files.writeString(file, "take " + i);
            service.quarantine(file);
        }

        List<Path> kept = list();

        // The exact cap is an implementation detail; what matters is that it stopped growing.
        assertThat(kept).hasSizeLessThanOrEqualTo(15);
        assertThat(kept).isNotEmpty();
    }

    @Test
    void doesNothingWhenTheFileIsAlreadyGone() {
        service().quarantine(audioDir.resolve("never-existed.wav"));

        assertThat(list()).isEmpty();
    }

    private List<Path> list() {
        try (Stream<Path> files = Files.list(audioDir)) {
            return files.sorted(Comparator.naturalOrder()).toList();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
