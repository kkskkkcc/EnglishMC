package io.github.kkskkkcc.englishmc.client;

import io.github.kkskkkcc.englishmc.EnglishMC;
import io.github.kkskkkcc.englishmc.LessonDeck;
import io.github.kkskkkcc.englishmc.LessonDeck.Bilingual;
import io.github.kkskkkcc.englishmc.LessonDeck.Card;
import io.github.kkskkkcc.englishmc.PracticeLevel;
import io.github.kkskkkcc.englishmc.ReplyCheck;
import io.github.kkskkkcc.englishmc.WorldPractice;
import io.github.kkskkkcc.englishmc.client.speech.LocalRecognizer;
import io.github.kkskkkcc.englishmc.client.speech.LocalVoice;
import io.github.kkskkkcc.englishmc.client.speech.Microphone;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.LineUnavailableException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

final class PracticeSession {
    private enum Phase { IDLE, WAITING, PREPARING, RECORDING, CHECKING, PASSED }
    private enum Next { SCENE, FOLLOW_UP, VOCABULARY }

    private static final int TICKS_PER_SECOND = 20;
    private static final Bilingual VOCABULARY = new Bilingual("Say this out loud.", "请大声念出下面的词。");
    private static final Bilingual TRY_AGAIN = new Bilingual("Listen and try once more.", "听一下，再试一次。");
    private static final Bilingual MODEL_MATCHED = new Bilingual("Good, that matched the example.", "很好，和示范一致。");
    private static final Bilingual PERSONAL_MATCHED = new Bilingual("Good, you addressed the topic.", "很好，你回应了这个话题。");

    private final Minecraft minecraft;
    private final LocalVoice voice;
    private final LocalRecognizer recognizer;
    private final ExecutorService speech = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "EnglishMC microphone and recognition");
        thread.setDaemon(true);
        return thread;
    });

    private IntegratedServer world;
    private PracticeLevel difficulty;
    private LessonDeck deck;
    private Card card;
    private boolean vocabulary;
    private boolean followUp;
    private Phase phase = Phase.IDLE;
    private Next next = Next.SCENE;
    private int delay;
    private int successfulDialogues;
    private int turnVersion;
    private Microphone microphone;
    private String transcript = "";
    private String feedback;
    private String voiceError;
    private String focusWords = "";
    private boolean unavailable;

    PracticeSession(Minecraft minecraft) {
        this.minecraft = minecraft;
        recognizer = new LocalRecognizer(minecraft.gameDirectory.toPath());
        voice = new LocalVoice(minecraft.gameDirectory.toPath());
    }

    void tick(boolean recordPressed, boolean replayPressed) {
        var current = minecraft.getSingleplayerServer();
        if (current == null || minecraft.player == null) {
            if (world != null) reset();
            return;
        }
        var rules = current.getWorldData().getGameRules();
        var selected = PracticeLevel.fromRule(rules.getInt(WorldPractice.LEVEL));
        if (selected == null) {
            if (world != null) reset();
            return;
        }
        if (world != current || difficulty != selected) {
            reset();
            world = current;
            difficulty = selected;
            deck = new LessonDeck(selected);
            delay = 7 * TICKS_PER_SECOND;
            unavailable = !LocalRecognizer.supported();
            if (!unavailable) {
                speech.execute(() -> {
                    try {
                        recognizer.warmUp();
                    } catch (Exception | LinkageError error) {
                        EnglishMC.LOG.warn("EnglishMC recognition warm-up failed", error);
                    }
                });
            }
        }
        if (minecraft.screen != null || minecraft.isPaused() || !minecraft.isWindowActive()) {
            if (phase == Phase.PREPARING || phase == Phase.RECORDING || phase == Phase.CHECKING) {
                cancelRecord();
                phase = Phase.WAITING;
                feedback = "cancelled";
            }
            return;
        }
        if (phase == Phase.IDLE || phase == Phase.PASSED) {
            if (recordPressed || --delay <= 0) showNext();
            return;
        }
        if (phase == Phase.WAITING) {
            if (recordPressed) capture();
            else if (replayPressed) narrate(List.of(prompt(), example()));
        } else if (phase == Phase.RECORDING && recordPressed) {
            microphone.finish();
            phase = Phase.CHECKING;
        }
    }

    private void showNext() {
        Card related = next == Next.FOLLOW_UP ? deck.followUp(card) : null;
        vocabulary = next == Next.VOCABULARY;
        if (!vocabulary) {
            followUp = related != null;
            card = followUp ? related : deck.draw();
        }
        phase = Phase.WAITING;
        turnVersion++;
        transcript = "";
        focusWords = "";
        feedback = null;
        narrate(List.of(prompt(), example()));
    }

    private void capture() {
        if (unavailable) return;
        voice.stop();
        var capture = new Microphone();
        microphone = capture;
        phase = Phase.PREPARING;
        feedback = null;
        focusWords = "";
        int recordingSeconds = difficulty.recordingSeconds();
        speech.execute(() -> {
            try {
                byte[] audio = capture.record(recordingSeconds, () -> minecraft.execute(() -> {
                    if (microphone == capture) phase = Phase.RECORDING;
                }));
                minecraft.execute(() -> {
                    if (microphone == capture) phase = Phase.CHECKING;
                });
                String heard = recognizer.transcribe(audio);
                minecraft.execute(() -> {
                    if (microphone == capture) submit(heard);
                });
            } catch (LineUnavailableException | SecurityException error) {
                minecraft.execute(() -> failedCapture(capture, "mic_unavailable"));
            } catch (Exception | LinkageError error) {
                EnglishMC.LOG.warn("EnglishMC could not recognize local microphone input", error);
                minecraft.execute(() -> failedCapture(capture, "recognition_failed"));
            }
        });
    }

    private void failedCapture(Microphone capture, String status) {
        if (microphone != capture) return;
        microphone = null;
        phase = Phase.WAITING;
        feedback = status;
    }

    private void submit(String heard) {
        microphone = null;
        transcript = heard;
        ReplyCheck.Result result = ReplyCheck.check(difficulty, card, vocabulary, heard);
        if (!result.passed()) {
            phase = Phase.WAITING;
            if (result.outcome() == ReplyCheck.Outcome.NO_SPEECH) {
                feedback = "no_speech";
                return;
            }
            focusWords = result.practiceWords();
            feedback = focusWords.isEmpty() ? "retry" : "retry_words";
            narrate(List.of(TRY_AGAIN, example()));
            return;
        }

        phase = Phase.PASSED;
        feedback = result.outcome() == ReplyCheck.Outcome.MATCHED ? "matched" : "personal";
        narrate(List.of(result.outcome() == ReplyCheck.Outcome.MATCHED ? MODEL_MATCHED : PERSONAL_MATCHED));
        if (!vocabulary) successfulDialogues++;
        if (!vocabulary && successfulDialogues % 4 == 0) {
            next = Next.VOCABULARY;
        } else if (!vocabulary && !followUp) {
            next = Next.FOLLOW_UP;
        } else {
            next = Next.SCENE;
        }
        delay = next == Next.SCENE ? WorldPractice.minutes(world.getWorldData().getGameRules()) * 60 * TICKS_PER_SECOND
            : 7 * TICKS_PER_SECOND;
    }

    private void narrate(List<Bilingual> lines) {
        if (unavailable) return;
        voiceError = null;
        int version = turnVersion;
        int volume = Math.round(100 * minecraft.options.getSoundSourceVolume(SoundSource.MASTER)
            * minecraft.options.getSoundSourceVolume(SoundSource.VOICE));
        var narration = new LocalVoice.Narration(lines.stream().flatMap(line -> line.sentences().stream()).toList(),
            difficulty.narrationRate(), volume);
        voice.speak(narration, key -> minecraft.execute(() -> {
            if (turnVersion == version) voiceError = key;
        }));
    }

    private void cancelRecord() {
        if (microphone != null) {
            microphone.cancel();
            microphone = null;
        }
    }

    void reset() {
        turnVersion++;
        cancelRecord();
        voice.stop();
        world = null;
        difficulty = null;
        deck = null;
        card = null;
        phase = Phase.IDLE;
        next = Next.SCENE;
        successfulDialogues = 0;
        transcript = "";
        feedback = null;
        voiceError = null;
        focusWords = "";
    }

    View view() {
        if (card == null) return null;
        Component status;
        if (unavailable) status = status("unsupported");
        else if (feedback != null) status = status(feedback, focusWords, ClientHooks.RECORD.getTranslatedKeyMessage());
        else status = switch (phase) {
            case PREPARING -> status("preparing");
            case RECORDING -> status("recording", ClientHooks.RECORD.getTranslatedKeyMessage());
            case CHECKING -> status("checking");
            default -> status("waiting", ClientHooks.RECORD.getTranslatedKeyMessage(),
                ClientHooks.REPLAY.getTranslatedKeyMessage());
        };
        boolean alert = phase == Phase.RECORDING || (feedback != null && phase != Phase.PASSED);
        return new View(card.scene(), prompt(), example(), vocabulary, transcript, status,
            voiceError, alert, phase == Phase.PASSED);
    }

    private Bilingual prompt() {
        return vocabulary ? VOCABULARY : card.prompt();
    }

    private Bilingual example() {
        return vocabulary ? card.vocabulary() : card.example();
    }

    private static Component status(String key, Object... args) {
        return Component.translatable("englishmc.status." + key, args);
    }

    record View(Bilingual scene, Bilingual prompt, Bilingual example, boolean vocabulary,
                String transcript, Component status, String voiceError, boolean alert, boolean passed) {}
}
