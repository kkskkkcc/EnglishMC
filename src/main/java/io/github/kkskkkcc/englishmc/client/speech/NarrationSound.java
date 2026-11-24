package io.github.kkskkkcc.englishmc.client.speech;

import io.github.kkskkkcc.englishmc.EnglishMC;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

final class NarrationSound extends AbstractSoundInstance {
    private static final ResourceLocation ID = new ResourceLocation(EnglishMC.ID, "local_narration");
    private static final ConstantFloat ONE = ConstantFloat.of(1f);
    private static final Sound STREAMED = new Sound(ID.toString(), ONE, ONE, 1, Sound.Type.FILE, true, false, 0);
    private static final WeighedSoundEvents EVENT = new WeighedSoundEvents(ID, null);
    private final PcmStream stream = new PcmStream();

    NarrationSound() {
        super(ID, SoundSource.VOICE, SoundInstance.createUnseededRandom());
        sound = STREAMED;
        volume = pitch = 1f;
        relative = true;
        attenuation = Attenuation.NONE;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        return EVENT;
    }

    void queue(byte[] pcm) {
        stream.pending.add(pcm);
    }

    void finish() {
        stream.finished = true;
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound resource, boolean repeat) {
        return CompletableFuture.completedFuture(stream);
    }

    private static final class PcmStream implements AudioStream {
        private static final AudioFormat FORMAT = new AudioFormat(LocalVoice.SAMPLE_RATE, 16, 1, true, false);
        private static final int GAP_BYTES = LocalVoice.SAMPLE_RATE * 2 / 5;
        private final ConcurrentLinkedQueue<byte[]> pending = new ConcurrentLinkedQueue<>();
        private volatile boolean finished;
        private byte[] current;
        private int cursor;

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        @Override
        public ByteBuffer read(int bytes) {
            if (current == null) {
                current = pending.poll();
                if (current == null) {
                    if (finished) {
                        current = pending.poll();
                        if (current == null) return null;
                    } else {
                        // Never block Minecraft's audio thread while the next phrase is synthesized.
                        return ByteBuffer.allocateDirect(Math.min(bytes & ~1, GAP_BYTES));
                    }
                }
                cursor = 0;
            }
            int length = Math.min(bytes & ~1, current.length - cursor);
            var buffer = ByteBuffer.allocateDirect(length);
            buffer.put(current, cursor, length).flip();
            cursor += length;
            if (cursor == current.length) current = null;
            return buffer;
        }

        @Override
        public void close() {
            pending.clear();
            current = null;
            finished = true;
        }
    }
}
