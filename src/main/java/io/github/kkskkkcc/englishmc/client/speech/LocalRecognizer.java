package io.github.kkskkkcc.englishmc.client.speech;

import com.google.gson.JsonParser;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipInputStream;

public final class LocalRecognizer {
    private static final String MODEL = "vosk-model-small-en-us-0.15";
    private static final String ASSETS = "/assets/englishmc/speech/";
    private static final int BLOCK_BYTES = 6400;
    private final Path cache;
    private Vosk api;
    private Pointer model;

    public LocalRecognizer(Path gameDirectory) {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path local = localAppData == null ? gameDirectory : Path.of(localAppData);
        this.cache = local.resolve("EnglishMC").resolve("speech-vosk-0.3.45");
    }

    public static boolean supported() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return System.getProperty("os.name", "").startsWith("Windows")
            && (arch.equals("amd64") || arch.equals("x86_64"));
    }

    public synchronized void warmUp() throws IOException {
        if (model != null) return;
        if (!supported()) throw new IOException("EnglishMC speech needs 64-bit Windows on x86-64");
        Path runtime = prepareAssets();
        // Windows resolves dependent DLLs by module name when already loaded.
        for (String name : dependencies()) {
            NativeLibrary.getInstance(runtime.resolve(name).toString());
        }
        api = Native.load(runtime.resolve("libvosk.dll").toString(), Vosk.class,
            Map.of(Library.OPTION_STRING_ENCODING, StandardCharsets.UTF_8.name()));
        api.vosk_set_log_level(-1);
        model = api.vosk_model_new(runtime.resolve(MODEL).toString());
        if (model == null) throw new IOException("The EnglishMC speech model could not be loaded");
    }

    public synchronized String transcribe(byte[] pcm) throws IOException {
        if (!hasSignal(pcm)) return "";
        warmUp();
        Pointer recognizer = api.vosk_recognizer_new(model, Microphone.RATE);
        if (recognizer == null) throw new IOException("The EnglishMC recognizer could not be started");
        try {
            StringBuilder transcript = new StringBuilder();
            for (int position = 0; position < pcm.length; position += BLOCK_BYTES) {
                byte[] block = Arrays.copyOfRange(pcm, position, Math.min(position + BLOCK_BYTES, pcm.length));
                int result = api.vosk_recognizer_accept_waveform(recognizer, block, block.length);
                if (result < 0) throw new IOException("The EnglishMC recognizer rejected audio");
                if (result > 0) appendText(transcript, api.vosk_recognizer_result(recognizer));
            }
            appendText(transcript, api.vosk_recognizer_final_result(recognizer));
            return transcript.toString();
        } finally {
            api.vosk_recognizer_free(recognizer);
        }
    }

    static boolean hasSignal(byte[] pcm) {
        if (pcm.length < Microphone.RATE / 4 * 2) return false;
        var samples = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        int activeSamples = 0;
        while (samples.hasRemaining()) {
            if (Math.abs((int) samples.get()) > 96 && ++activeSamples >= 160) return true;
        }
        return false;
    }

    private static void appendText(StringBuilder output, String json) {
        String text = JsonParser.parseString(json).getAsJsonObject().get("text").getAsString().strip();
        if (text.isEmpty()) return;
        if (!output.isEmpty()) output.append(' ');
        output.append(text);
    }

    private Path prepareAssets() throws IOException {
        if (Files.isRegularFile(cache.resolve(".ready"))) return cache;
        Files.createDirectories(cache.getParent());
        Path staging = Files.createTempDirectory(cache.getParent(), "speech-install-");
        try {
            for (String dll : dependencies()) copyResource(staging, dll);
            copyResource(staging, "libvosk.dll");
            unpackModel(staging);
            Files.createFile(staging.resolve(".ready"));
            try {
                Files.move(staging, cache, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(staging, cache);
            } catch (IOException e) {
                if (!Files.isRegularFile(cache.resolve(".ready"))) throw e;
            }
        } finally {
            if (Files.exists(staging)) {
                try (var children = Files.walk(staging)) {
                    for (Path path : children.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
        return cache;
    }

    private static void copyResource(Path directory, String name) throws IOException {
        try (var stream = LocalRecognizer.class.getResourceAsStream(ASSETS + name)) {
            if (stream == null) throw new IOException("Missing EnglishMC speech resource " + name);
            Files.copy(stream, directory.resolve(name));
        }
    }

    private static void unpackModel(Path directory) throws IOException {
        var stream = LocalRecognizer.class.getResourceAsStream(ASSETS + MODEL + ".zip");
        if (stream == null) throw new IOException("Missing EnglishMC US English model");
        try (var zip = new ZipInputStream(stream)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                Path target = directory.resolve(entry.getName()).normalize();
                if (!target.startsWith(directory.resolve(MODEL))) {
                    throw new IOException("Unexpected EnglishMC model path");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target);
                }
            }
        }
    }

    private static List<String> dependencies() {
        return List.of("libwinpthread-1.dll", "libgcc_s_seh-1.dll", "libstdc++-6.dll");
    }

    public interface Vosk extends Library {
        void vosk_set_log_level(int level);
        Pointer vosk_model_new(String directory);
        Pointer vosk_recognizer_new(Pointer model, float sampleRate);
        int vosk_recognizer_accept_waveform(Pointer recognizer, byte[] data, int length);
        String vosk_recognizer_result(Pointer recognizer);
        String vosk_recognizer_final_result(Pointer recognizer);
        void vosk_recognizer_free(Pointer recognizer);
    }
}
