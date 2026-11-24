package io.github.kkskkkcc.englishmc.client.speech;

import io.github.kkskkkcc.englishmc.LessonDeck.Bilingual;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public final class NeuralVoiceCheck {
    public static void main(String[] args) throws Exception {
        byte[] longResample = Microphone.resample(new byte[48000 * 2 * 10], 48000);
        if (longResample.length != Microphone.RATE * 2 * 10 || LocalRecognizer.prepareAudio(longResample).length != 0) {
            throw new IOException("Long 48 kHz capture or silence handling failed");
        }
        var process = LocalVoice.startWorker(LocalVoice.prepare(Path.of(".")), Path.of("."));
        Path output = Path.of(args[0]);
        Files.createDirectories(output.getParent());
        try {
            var narration = new LocalVoice.Narration(List.of(
                new Bilingual("Where are you right now?", "你现在在哪里？"),
                new Bilingual("I'm at the front entrance of the library.", "我在图书馆正门口。")
            ), -1);
            LocalVoice.send(process, narration);
            try (var input = new DataInputStream(process.getInputStream())) {
                int rate = input.readInt();
                if (rate != 24000) throw new IOException("Expected 24 kHz neural audio, got " + rate);
                var preview = new ByteArrayOutputStream();
                for (int segment = 0; segment < 4; segment++) {
                    int length = input.readInt();
                    byte[] pcm = input.readNBytes(length);
                    if (length != pcm.length || length < rate / 2 || !hasAudio(pcm)) {
                        throw new IOException("Silent or incomplete neural voice segment " + segment);
                    }
                    if (segment == 2) {
                        var recognizer = new LocalRecognizer(Path.of("."));
                        String heard = recognizer.transcribe(Microphone.resample(pcm, rate));
                        String folded = heard.toLowerCase(java.util.Locale.ROOT);
                        if (!folded.contains("front entrance") || !folded.contains("library")) {
                            throw new IOException("Could not confirm spoken English example: " + heard);
                        }
                        System.out.println("Kokoro English example recognized by Whisper: " + heard);
                    }
                    preview.write(pcm);
                    preview.write(new byte[rate / 3]);
                    System.out.println("Kokoro " + (segment % 2 == 0 ? "en-US" : "zh-CN")
                        + " segment " + (segment + 1) + ": " + length + " PCM bytes");
                }
                try (var audio = new AudioInputStream(new ByteArrayInputStream(preview.toByteArray()),
                    new AudioFormat(rate, 16, 1, true, false), preview.size() / 2)) {
                    AudioSystem.write(audio, AudioFileFormat.Type.WAVE, output.toFile());
                }
            }
            if (!process.waitFor(8, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IOException("Neural voice worker exited with an error");
            }
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
        System.out.println("Kokoro bilingual audio verified: " + output);
    }

    private static boolean hasAudio(byte[] pcm) {
        for (int i = 0; i < pcm.length; i += 2) {
            int sample = (short) ((pcm[i] & 0xff) | pcm[i + 1] << 8);
            if (Math.abs(sample) > 512) return true;
        }
        return false;
    }
}
