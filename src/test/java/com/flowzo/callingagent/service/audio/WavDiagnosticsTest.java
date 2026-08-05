package com.flowzo.callingagent.service.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WavDiagnosticsTest {

    @TempDir
    Path tempDir;

    @Test
    void reportsDurationAndLoudnessOfANormalRecording() throws IOException {
        // One second at full-scale amplitude: rms of a sine wave is amplitude / sqrt(2) =~ 0.6.
        Path file = tempDir.resolve("loud.wav");
        Files.write(file, wav(16000, tone(16000, 440, 20000, 16000)));

        WavDiagnostics.Summary summary = WavDiagnostics.describe(file);

        assertThat(summary.parsed()).isTrue();
        assertThat(summary.seconds()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(summary.rms()).isGreaterThan(0.4);
        assertThat(summary.toString()).contains("rms");
    }

    @Test
    void reportsNearZeroLoudnessForSilence() throws IOException {
        Path file = tempDir.resolve("silence.wav");
        Files.write(file, wav(16000, new short[16000]));

        WavDiagnostics.Summary summary = WavDiagnostics.describe(file);

        assertThat(summary.parsed()).isTrue();
        assertThat(summary.rms()).isLessThan(0.001);
        assertThat(summary.peak()).isLessThan(0.001);
    }

    /**
     * The app's own streamed TTS output writes the RIFF/data sizes as the 32-bit "unknown length"
     * sentinel (all bits set) instead of the real byte count, because it does not know the final
     * size until the stream ends. A parser that treats that sentinel as a literal size reads a huge
     * negative chunk length and silently reports every recording as empty — which is exactly the bug
     * this test caught during development, on a real file that plainly contained speech.
     */
    @Test
    void handlesTheUnknownLengthSentinelAStreamedWavUses() throws IOException {
        Path file = tempDir.resolve("streamed.wav");
        short[] samples = tone(16000, 440, 20000, 16000);
        byte[] normal = wav(16000, samples);
        ByteBuffer buffer = ByteBuffer.wrap(normal.clone()).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(4, -1);
        buffer.putInt(40, -1);
        Files.write(file, buffer.array());

        WavDiagnostics.Summary summary = WavDiagnostics.describe(file);

        assertThat(summary.parsed()).isTrue();
        assertThat(summary.seconds()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(summary.rms()).isGreaterThan(0.4);
    }

    @Test
    void describesAFileThatIsNotAWavAtAllWithoutThrowing() throws IOException {
        Path file = tempDir.resolve("not-audio.wav");
        Files.write(file, "definitely not a wav file".getBytes());

        WavDiagnostics.Summary summary = WavDiagnostics.describe(file);

        assertThat(summary.parsed()).isFalse();
        assertThat(summary.toString()).contains("could not");
    }

    @Test
    void describesAMissingFileWithoutThrowing() {
        WavDiagnostics.Summary summary = WavDiagnostics.describe(tempDir.resolve("missing.wav"));

        assertThat(summary.parsed()).isFalse();
    }

    private short[] tone(int sampleRate, double frequency, int amplitude, int count) {
        short[] samples = new short[count];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) (amplitude * Math.sin(2 * Math.PI * frequency * i / sampleRate));
        }
        return samples;
    }

    private byte[] wav(int sampleRate, short[] samples) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int dataSize = samples.length * 2;
        writeAscii(out, "RIFF");
        writeInt(out, 36 + dataSize);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeInt(out, 16);
        writeShort(out, 1);
        writeShort(out, 1);
        writeInt(out, sampleRate);
        writeInt(out, sampleRate * 2);
        writeShort(out, 2);
        writeShort(out, 16);
        writeAscii(out, "data");
        writeInt(out, dataSize);
        for (short sample : samples) {
            writeShort(out, sample);
        }
        return out.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes());
    }

    private void writeInt(ByteArrayOutputStream out, int value) {
        out.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }

    private void writeShort(ByteArrayOutputStream out, int value) {
        out.writeBytes(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value).array());
    }
}
