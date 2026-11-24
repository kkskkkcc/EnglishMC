package io.github.kkskkkcc.englishmc.client.speech;

import io.github.kkskkkcc.englishmc.EnglishMC;
import io.github.kkskkkcc.englishmc.LessonDeck.Bilingual;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public final class LocalVoice {
    public record Narration(List<Bilingual> lines, int rate, int volume) {}

    private static final String MODEL_DIR = "neural-voice-kokoro-v1.1-sherpa-1.12.39";
    private static final String BUNDLE = "/assets/englishmc/speech/kokoro.zip";
    private static final String WORKER = "io.github.kkskkkcc.englishmc.voice.KokoroWorker";
    private static final int MAX_SEGMENT_BYTES = 8_000_000;
    private static final int SAMPLE_RATE = 24000;

    private final Path cache;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "EnglishMC neural speech playback");
        thread.setDaemon(true);
        return thread;
    });

    private volatile int revision;
    private Process running;
    private SourceDataLine playing;

    public LocalVoice(Path gameDirectory) {
        cache = SpeechFiles.root(gameDirectory).resolve(MODEL_DIR);
    }

    public synchronized void speak(Narration narration, Consumer<String> status) {
        stop();
        if (narration.volume() <= 0) return;
        int request = revision;
        worker.execute(() -> play(request, narration, status));
    }

    public synchronized void stop() {
        revision++;
        if (running != null) {
            running.destroyForcibly();
            running = null;
        }
        if (playing != null) {
            playing.stop();
            playing.flush();
            playing.close();
            playing = null;
        }
    }

    private void play(int request, Narration narration, Consumer<String> status) {
        Process process = null;
        try {
            if (request != revision) return;
            Path directory = prepare();
            synchronized (this) {
                if (request != revision) return;
                running = process = startWorker(directory);
            }
            send(process, narration);
            try (var input = new DataInputStream(new BufferedInputStream(process.getInputStream()))) {
                if (input.readInt() != SAMPLE_RATE) throw new IOException("Unexpected Kokoro audio format");
                int count = narration.lines().size() * 2;
                for (int segment = 0; segment < count && request == revision; segment++) {
                    var line = openOutput(request);
                    if (line == null) return;
                    playSegment(request, input, line, narration.volume());
                }
            }
            if (request != revision) return;
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IOException("Kokoro synthesis process did not finish cleanly");
            }
            SourceDataLine line;
            synchronized (this) {
                line = playing;
            }
            if (line != null) line.drain();
        } catch (Exception | LinkageError error) {
            if (request == revision) {
                EnglishMC.LOG.warn("EnglishMC neural voice failed", error);
                status.accept("englishmc.status.voice_failed");
            }
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            synchronized (this) {
                if (request == revision) stop();
            }
        }
    }

    private synchronized SourceDataLine openOutput(int request) throws Exception {
        if (request != revision) return null;
        if (playing != null) return playing;
        var format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        SourceDataLine line = AudioSystem.getSourceDataLine(format);
        try {
            line.open(format, SAMPLE_RATE / 2);
            line.start();
            return playing = line;
        } catch (Exception error) {
            line.close();
            throw error;
        }
    }

    private void playSegment(int request, DataInputStream input, SourceDataLine line, int volume) throws IOException {
        int bytes = input.readInt();
        if (bytes <= 0 || bytes > MAX_SEGMENT_BYTES || bytes % 2 != 0) {
            throw new IOException("Kokoro returned an invalid audio segment");
        }
        byte[] buffer = new byte[4096];
        while (bytes > 0 && request == revision) {
            int chunk = Math.min(bytes, buffer.length);
            input.readFully(buffer, 0, chunk);
            bytes -= chunk;
            if (volume < 100) applyVolume(buffer, chunk, volume);
            if (request == revision) line.write(buffer, 0, chunk);
        }
    }

    private static void applyVolume(byte[] pcm, int length, int volume) {
        for (int i = 0; i < length; i += 2) {
            int sample = (short) ((pcm[i] & 0xff) | pcm[i + 1] << 8);
            sample = sample * volume / 100;
            pcm[i] = (byte) sample;
            pcm[i + 1] = (byte) (sample >> 8);
        }
    }

    static Process startWorker(Path directory) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "javaw.exe").toString();
        String classpath = String.join(File.pathSeparator, "englishmc-voice-worker.jar",
            "sherpa-api.jar", "sherpa-win-x64.jar");
        return new ProcessBuilder(java, "-Xmx128m", "-cp", classpath, WORKER)
            .directory(directory.toFile())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    }

    static void send(Process process, Narration narration) throws IOException {
        try (var output = new DataOutputStream(process.getOutputStream())) {
            output.writeInt(narration.lines().size() * 2);
            for (Bilingual line : narration.lines()) {
                output.writeBoolean(true);
                output.writeFloat(1f + 0.09f * narration.rate());
                output.writeUTF(line.english());
                output.writeBoolean(false);
                output.writeFloat(0.96f);
                output.writeUTF(line.chinese());
            }
        }
    }

    Path prepare() throws IOException {
        return SpeechFiles.install(cache, staging -> {
            var resource = LocalVoice.class.getResourceAsStream(BUNDLE);
            if (resource == null) throw new IOException("Missing EnglishMC Kokoro voice model");
            try (var zip = new ZipInputStream(resource)) {
                SpeechFiles.unpack(zip, staging, staging);
            }
        });
    }
}
