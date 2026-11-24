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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

public final class LocalVoice {
    public record Narration(List<Bilingual> lines, int rate) {}

    private static final String MODEL_DIR = "neural-voice-kokoro-v1.1-sherpa-1.12.39";
    private static final String BUNDLE = "/assets/englishmc/speech/kokoro.zip";
    private static final String WORKER = "io.github.kkskkkcc.englishmc.voice.KokoroWorker";
    private static final int MAX_AUDIO_BYTES = 10_000_000;
    static final int SAMPLE_RATE = 24000;
    public static final String PREPARING = "englishmc.status.voice_preparing";
    private static final String MUTED = "englishmc.status.voice_muted";
    private static final String FAILED = "englishmc.status.voice_failed";

    private final Minecraft minecraft;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "EnglishMC neural speech synthesis");
        thread.setDaemon(true);
        return thread;
    });

    private volatile int revision;
    private Process running;
    private NarrationSound playing;
    private Narration cachedNarration;
    private List<byte[]> cachedAudio;

    public LocalVoice(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void speak(Narration narration, Consumer<String> status) {
        stop();
        if (isMuted()) {
            status.accept(MUTED);
            return;
        }
        if (narration.equals(cachedNarration)) {
            var replay = new NarrationSound();
            cachedAudio.forEach(replay::queue);
            replay.finish();
            play(replay, status);
            return;
        }
        int request = revision;
        status.accept(PREPARING);
        worker.execute(() -> synthesize(request, narration, status));
    }

    public synchronized void stop() {
        revision++;
        if (running != null) {
            running.destroyForcibly();
            running = null;
        }
        if (playing != null) {
            playing.finish();
            minecraft.getSoundManager().stop(playing);
            playing = null;
        }
    }

    private void synthesize(int request, Narration narration, Consumer<String> status) {
        Process process = null;
        NarrationSound sound = new NarrationSound();
        try {
            if (request != revision) return;
            Path gameDirectory = minecraft.gameDirectory.toPath();
            Path directory = prepare(gameDirectory);
            if (request != revision) return;
            process = startWorker(directory, gameDirectory);
            synchronized (this) {
                if (request != revision) return;
                running = process;
            }
            send(process, narration);
            var segments = new ArrayList<byte[]>();
            try (var input = new DataInputStream(new BufferedInputStream(process.getInputStream()))) {
                if (input.readInt() != SAMPLE_RATE) throw new IOException("Unexpected Kokoro audio format");
                int remaining = MAX_AUDIO_BYTES;
                for (int i = 0; i < narration.lines().size() * 2 && request == revision; i++) {
                    byte[] segment = readSegment(input, remaining);
                    remaining -= segment.length;
                    segments.add(segment);
                    sound.queue(segment);
                    if (i == 0) {
                        minecraft.execute(() -> {
                            if (request == revision) play(sound, status);
                        });
                    }
                }
            }
            if (request != revision) return;
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IOException("Kokoro synthesis process did not finish cleanly");
            }
            minecraft.execute(() -> {
                if (request != revision) return;
                cachedNarration = narration;
                cachedAudio = List.copyOf(segments);
            });
        } catch (Exception | LinkageError error) {
            if (request == revision) {
                EnglishMC.LOG.warn("EnglishMC neural voice synthesis failed", error);
                minecraft.execute(() -> {
                    if (request == revision) status.accept(FAILED);
                });
            }
        } finally {
            sound.finish();
            if (process != null && process.isAlive()) process.destroyForcibly();
            synchronized (this) {
                if (running == process) running = null;
            }
        }
    }

    private void play(NarrationSound sound, Consumer<String> status) {
        if (isMuted()) {
            status.accept(MUTED);
            return;
        }
        var manager = minecraft.getSoundManager();
        manager.play(sound);
        if (manager.isActive(sound)) {
            playing = sound;
            status.accept(null);
        } else {
            EnglishMC.LOG.warn("Minecraft sound engine did not accept the EnglishMC narration");
            status.accept(FAILED);
        }
    }

    private boolean isMuted() {
        return minecraft.options.getSoundSourceVolume(SoundSource.MASTER) <= 0f
            || minecraft.options.getSoundSourceVolume(SoundSource.VOICE) <= 0f;
    }

    private static byte[] readSegment(DataInputStream input, int remaining) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length % 2 != 0 || length > remaining) {
            throw new IOException("Kokoro returned an invalid audio segment");
        }
        byte[] segment = new byte[length];
        input.readFully(segment);
        return segment;
    }

    static Process startWorker(Path directory, Path gameDirectory) throws IOException {
        Path temporary = SpeechFiles.instanceCache(gameDirectory).resolve("worker-temp");
        Files.createDirectories(temporary);
        String java = Path.of(System.getProperty("java.home"), "bin", "javaw.exe").toString();
        String classpath = String.join(File.pathSeparator, "englishmc-voice-worker.jar",
            "sherpa-api.jar", "sherpa-win-x64.jar");
        return new ProcessBuilder(java, "-Xmx128m", "-Djava.io.tmpdir=" + temporary, "-cp", classpath, WORKER)
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

    static Path prepare(Path gameDirectory) throws IOException {
        return SpeechFiles.install(SpeechFiles.modelDirectory(gameDirectory, MODEL_DIR), staging -> {
            var resource = LocalVoice.class.getResourceAsStream(BUNDLE);
            if (resource == null) throw new IOException("Missing EnglishMC Kokoro voice model");
            try (var zip = new ZipInputStream(resource)) {
                SpeechFiles.unpack(zip, staging, staging);
            }
        });
    }
}
