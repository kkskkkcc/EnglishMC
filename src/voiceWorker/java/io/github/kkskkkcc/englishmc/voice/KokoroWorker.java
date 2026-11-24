package io.github.kkskkkcc.englishmc.voice;

import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

public final class KokoroWorker {
    private static final int AMERICAN_ENGLISH = 0; // af_maple
    private static final int SIMPLIFIED_CHINESE = 3; // zf_001

    public static void main(String[] args) throws Exception {
        var input = new DataInputStream(new BufferedInputStream(System.in));
        var output = new DataOutputStream(new BufferedOutputStream(System.out));
        int count = input.readInt();
        if (count < 1 || count > 40) throw new IllegalArgumentException("Invalid neural narration request");

        var tts = new OfflineTts(config());
        try {
            output.writeInt(tts.getSampleRate());
            output.flush();
            for (int segment = 0; segment < count; segment++) {
                boolean english = input.readBoolean();
                float speed = input.readFloat();
                String text = input.readUTF();
                float[] wave = tts.generate(text, english ? AMERICAN_ENGLISH : SIMPLIFIED_CHINESE, speed).getSamples();
                output.writeInt(wave.length * 2);
                var pcm = ByteBuffer.allocate(wave.length * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (float sample : wave) {
                    pcm.putShort((short) Math.round(Math.max(-1f, Math.min(1f, sample)) * 32767f));
                }
                output.write(pcm.array());
                output.flush();
            }
        } finally {
            tts.release();
        }
    }

    private static OfflineTtsConfig config() {
        Path root = Path.of("kokoro-int8-multi-lang-v1_1");
        var kokoro = OfflineTtsKokoroModelConfig.builder()
            .setModel(root.resolve("model.int8.onnx").toString())
            .setVoices(root.resolve("voices.bin").toString())
            .setTokens(root.resolve("tokens.txt").toString())
            .setDataDir(root.resolve("espeak-ng-data").toString())
            .setDictDir(root.resolve("dict").toString())
            .setLexicon(String.join(",", root.resolve("lexicon-us-en.txt").toString(),
                root.resolve("lexicon-zh.txt").toString()))
            .build();
        var model = OfflineTtsModelConfig.builder()
            .setKokoro(kokoro)
            .setNumThreads(2)
            .setProvider("cpu")
            .setDebug(false)
            .build();
        return OfflineTtsConfig.builder().setModel(model)
            .setRuleFsts(String.join(",", root.resolve("phone-zh.fst").toString(),
                root.resolve("date-zh.fst").toString(), root.resolve("number-zh.fst").toString()))
            .build();
    }
}
