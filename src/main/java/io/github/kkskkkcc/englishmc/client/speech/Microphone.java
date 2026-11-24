package io.github.kkskkkcc.englishmc.client.speech;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

public final class Microphone {
    public static final int RATE = 16000;
    private volatile boolean finishing;
    private volatile boolean cancelled;

    public void finish() {
        finishing = true;
    }

    public void cancel() {
        cancelled = true;
        finishing = true;
    }

    public boolean isCancelled() { return cancelled; }

    public byte[] record(int seconds, Runnable opened) throws LineUnavailableException, InterruptedException {
        if (finishing) return new byte[0];
        try (TargetDataLine line = open()) {
            if (finishing) return new byte[0];
            line.start();
            opened.run();
            int sourceRate = (int) line.getFormat().getSampleRate();
            int frameSize = line.getFormat().getFrameSize();
            int byteLimit = seconds * sourceRate * frameSize;
            byte[] buffer = new byte[2048];
            var audio = new ByteArrayOutputStream(byteLimit / 4);
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            while (!finishing && System.nanoTime() < until && audio.size() < byteLimit) {
                int available = Math.min(buffer.length, line.available());
                available -= available % frameSize;
                int count = Math.min(available, byteLimit - audio.size());
                if (count > 0) {
                    audio.write(buffer, 0, line.read(buffer, 0, count));
                } else {
                    Thread.sleep(12);
                }
            }
            if (cancelled) return new byte[0];
            int buffered = Math.min(Math.min(line.available(), buffer.length), byteLimit - audio.size());
            buffered -= buffered % frameSize;
            if (buffered > 0) audio.write(buffer, 0, line.read(buffer, 0, buffered));
            return resample(toMono(audio.toByteArray(), frameSize), sourceRate);
        }
    }

    private static TargetDataLine open() throws LineUnavailableException {
        for (int rate : new int[] {RATE, 48000, 44100}) {
            for (int channels : new int[] {1, 2}) {
                AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
                try {
                    DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                    TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                    try {
                        line.open(format, rate * channels / 5 * 2);
                        return line;
                    } catch (LineUnavailableException | RuntimeException e) {
                        line.close();
                        throw e;
                    }
                } catch (IllegalArgumentException | LineUnavailableException ignored) {
                    // Fall through to the next format supported by the default input.
                }
            }
        }
        throw new LineUnavailableException("No available default recording input");
    }

    private static byte[] toMono(byte[] pcm, int frameSize) {
        if (frameSize == 2) return pcm;
        byte[] mono = new byte[pcm.length / 2];
        for (int i = 0; i < pcm.length; i += 4) {
            int left = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            int right = (short) ((pcm[i + 2] & 0xff) | (pcm[i + 3] << 8));
            int sample = (left + right) / 2;
            mono[i / 2] = (byte) sample;
            mono[i / 2 + 1] = (byte) (sample >> 8);
        }
        return mono;
    }

    static byte[] resample(byte[] pcm, int sourceRate) {
        if (sourceRate == RATE) return pcm;
        int samples = pcm.length / 2;
        byte[] output = new byte[(int) ((long) samples * RATE / sourceRate) * 2];
        for (int i = 0; i < output.length / 2; i++) {
            int from = (int) ((long) i * sourceRate / RATE);
            int to = (int) ((long) (i + 1) * sourceRate / RATE);
            int sum = 0;
            for (int j = from; j < to; j++) {
                sum += (short) ((pcm[j * 2] & 0xff) | (pcm[j * 2 + 1] << 8));
            }
            int sample = sum / (to - from);
            output[i * 2] = (byte) sample;
            output[i * 2 + 1] = (byte) (sample >> 8);
        }
        return output;
    }
}
