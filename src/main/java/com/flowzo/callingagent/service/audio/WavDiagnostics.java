package com.flowzo.callingagent.service.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads just enough of a WAV file to say, in one log line, whether a recording that failed
 * transcription held any voice. Without this, "That recording could not be read" and a genuinely
 * silent take look identical in the logs, and telling them apart meant re-running the analysis by
 * hand every time — the file itself is deleted by the time anyone asks.
 */
public final class WavDiagnostics {

    private WavDiagnostics() {
    }

    public record Summary(boolean parsed, double seconds, double rms, double peak) {
        @Override
        public String toString() {
            return parsed
                    ? String.format("%.2fs, rms %.4f, peak %.4f (0..1)", seconds, rms, peak)
                    : "not 16-bit PCM WAV, could not analyse";
        }
    }

    public static Summary describe(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (bytes.length < 44 || buffer.getInt(0) != riffTag() || buffer.getInt(8) != waveTag()) {
                return new Summary(false, 0, 0, 0);
            }
            int channels = 1;
            int bitsPerSample = 16;
            int sampleRate = 16000;
            int offset = 12;
            int dataOffset = -1;
            int dataLength = 0;
            // A recording written as it streams in (this app's own TTS output does) does not know its
            // final size up front and puts the 32-bit "unknown length" sentinel (all bits set, reads
            // as -1) in the chunk header. Treating that as a normal size sent dataLength negative and
            // silently zeroed every measurement — the exact bug that hid a real bug during testing.
            while (offset + 8 <= bytes.length) {
                int chunkId = buffer.getInt(offset);
                int chunkSize = buffer.getInt(offset + 4);
                if (chunkId == fmtTag()) {
                    channels = buffer.getShort(offset + 10) & 0xFFFF;
                    sampleRate = buffer.getInt(offset + 12);
                    bitsPerSample = buffer.getShort(offset + 22) & 0xFFFF;
                    offset += 8 + chunkSize + (chunkSize % 2);
                } else if (chunkId == dataTag()) {
                    dataOffset = offset + 8;
                    int available = bytes.length - dataOffset;
                    dataLength = (chunkSize < 0 || chunkSize > available) ? available : chunkSize;
                    break;
                } else if (chunkSize < 0) {
                    break;
                } else {
                    offset += 8 + chunkSize + (chunkSize % 2);
                }
            }
            if (dataOffset < 0 || bitsPerSample != 16 || channels < 1) {
                return new Summary(false, 0, 0, 0);
            }
            int sampleCount = dataLength / 2;
            double sumSquares = 0;
            int peak = 0;
            for (int i = 0; i < sampleCount; i++) {
                short sample = buffer.getShort(dataOffset + i * 2);
                sumSquares += (double) sample * sample;
                peak = Math.max(peak, Math.abs(sample));
            }
            double rms = sampleCount == 0 ? 0 : Math.sqrt(sumSquares / sampleCount) / 32768.0;
            double seconds = sampleRate == 0 ? 0 : (double) (sampleCount / channels) / sampleRate;
            return new Summary(true, seconds, rms, peak / 32768.0);
        } catch (IOException | RuntimeException ex) {
            return new Summary(false, 0, 0, 0);
        }
    }

    private static int riffTag() {
        return tag('R', 'I', 'F', 'F');
    }

    private static int waveTag() {
        return tag('W', 'A', 'V', 'E');
    }

    private static int fmtTag() {
        return tag('f', 'm', 't', ' ');
    }

    private static int dataTag() {
        return tag('d', 'a', 't', 'a');
    }

    /** The int a 4-byte ASCII tag becomes when read little-endian, in the same order it sits in the file. */
    private static int tag(char first, char second, char third, char fourth) {
        return first | (second << 8) | (third << 16) | (fourth << 24);
    }
}
