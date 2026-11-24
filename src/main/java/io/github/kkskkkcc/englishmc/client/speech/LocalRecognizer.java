package io.github.kkskkkcc.englishmc.client.speech;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipInputStream;

public final class LocalRecognizer {
    private static final String MODEL = "ggml-small.en-q5_1.bin";
    private static final String BUNDLE = "/assets/englishmc/speech/whisper.zip";
    private static final int TIMEOUT_SECONDS = 45;
    private static final int FRAME_SAMPLES = Microphone.RATE / 50;
    private final Path cache;
    private Process running;

    public LocalRecognizer(Path gameDirectory) {
        this.cache = SpeechFiles.modelDirectory(gameDirectory, "whisper-small-en-v1.7.6");
    }

    public static boolean supported() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return System.getProperty("os.name", "").startsWith("Windows")
            && (arch.equals("amd64") || arch.equals("x86_64"));
    }

    public void warmUp() throws IOException {
        if (!supported()) throw new IOException("EnglishMC recognition needs 64-bit Windows on x86-64");
        SpeechFiles.install(cache, staging -> {
            var resource = LocalRecognizer.class.getResourceAsStream(BUNDLE);
            if (resource == null) throw new IOException("Missing EnglishMC local recognition engine");
            try (var zip = new ZipInputStream(resource)) {
                SpeechFiles.unpack(zip, staging, staging);
            }
        });
    }

    public synchronized void cancel() {
        if (running != null) running.destroyForcibly();
    }

    public String transcribe(byte[] pcm) throws IOException {
        return transcribe(pcm, () -> false);
    }

    public String transcribe(byte[] pcm, BooleanSupplier cancelled) throws IOException {
        byte[] audio = prepareAudio(pcm);
        if (audio.length == 0 || cancelled.getAsBoolean()) return "";
        warmUp();
        Process process;
        synchronized (this) {
            if (cancelled.getAsBoolean()) return "";
            int threads = Math.min(4, Runtime.getRuntime().availableProcessors());
            // A named output base enables the transcript callback for stdin. It writes
            // to stdout because no file output format is selected.
            process = new ProcessBuilder(cache.resolve("whisper-cli.exe").toString(), "-m", MODEL,
                "-f", "-", "-of", "englishmc", "-l", "en", "-nt", "-np", "-ng", "-t", Integer.toString(threads))
                .directory(cache.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
            running = process;
        }
        CompletableFuture.delayedExecutor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .execute(() -> { if (process.isAlive()) process.destroyForcibly(); });
        try {
            try (var stdin = process.getOutputStream()) {
                stdin.write(waveHeader(audio.length));
                stdin.write(audio);
            }
            String heard = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) throw new IOException("The local Whisper recognizer did not finish normally");
            return heard.replaceAll("\\[[^\\]]*\\]|\\([^)]*\\)", " ").replaceAll("\\s+", " ").strip();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("EnglishMC recognition was interrupted", error);
        } catch (IOException error) {
            if (cancelled.getAsBoolean()) return "";
            throw error;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            synchronized (this) {
                if (running == process) running = null;
            }
        }
    }

    static byte[] prepareAudio(byte[] pcm) {
        int samples = pcm.length / 2;
        if (samples < Microphone.RATE * 18 / 100) return new byte[0];
        var source = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        int frameCount = (samples + FRAME_SAMPLES - 1) / FRAME_SAMPLES;
        double[] energies = new double[frameCount];
        double peakRms = 0;
        for (int frame = 0; frame < frameCount; frame++) {
            int start = frame * FRAME_SAMPLES;
            int end = Math.min(start + FRAME_SAMPLES, samples);
            long squared = 0;
            for (int i = start; i < end; i++) {
                int sample = source.get(i);
                squared += (long) sample * sample;
            }
            energies[frame] = Math.sqrt((double) squared / (end - start));
            peakRms = Math.max(peakRms, energies[frame]);
        }
        if (peakRms < 48) return new byte[0];

        double threshold = Math.max(40, Math.min(280, peakRms * 0.12));
        int first = 0;
        int last = frameCount - 1;
        while (energies[first] < threshold) first++;
        while (energies[last] < threshold) last--;
        if (last - first < 2) return new byte[0];

        // Keep quiet consonants adjacent to speech, and lift low mic levels without clipping.
        int padding = Microphone.RATE / 5;
        int from = Math.max(0, first * FRAME_SAMPLES - padding);
        int to = Math.min(samples, (last + 1) * FRAME_SAMPLES + padding);
        double gain = Math.min(3.0, Math.max(1.0, 1000 / peakRms));
        if (gain == 1.0) return Arrays.copyOfRange(pcm, from * 2, to * 2);
        var output = ByteBuffer.allocate((to - from) * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = from; i < to; i++) {
            int scaled = (int) Math.round(source.get(i) * gain);
            output.putShort((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled)));
        }
        return output.array();
    }

    private static byte[] waveHeader(int dataSize) {
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize + 36)
            .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
            .putShort((short) 1).putShort((short) 1).putInt(Microphone.RATE).putInt(Microphone.RATE * 2)
            .putShort((short) 2).putShort((short) 16)
            .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize).array();
    }
}
