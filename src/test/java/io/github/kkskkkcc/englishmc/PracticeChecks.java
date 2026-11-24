package io.github.kkskkkcc.englishmc;

import io.github.kkskkkcc.englishmc.LessonDeck.Card;
import io.github.kkskkkcc.englishmc.ReplyCheck.Outcome;
import io.github.kkskkkcc.englishmc.client.speech.LocalRecognizer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.sound.sampled.AudioSystem;

public final class PracticeChecks {
    private static final int SCENE_COUNT = 60;

    public static void main(String[] args) throws Exception {
        Map<String, Card> previous = null;
        Card morning = null;
        for (PracticeLevel level : PracticeLevel.values()) {
            var deck = new LessonDeck(level);
            var cards = new HashMap<String, Card>();
            Card last = null;
            for (int count = 0; count < SCENE_COUNT; count++) {
                Card card = deck.draw();
                require(cards.put(card.id(), card) == null, "Repeated scene before the deck was exhausted");
                require(ReplyCheck.check(level, card, false, card.example().english()).outcome() == Outcome.MATCHED,
                    "Example did not pass: " + level + "/" + card.id());
                require(ReplyCheck.check(level, card, true, card.vocabulary().english()).outcome() == Outcome.MATCHED,
                    "Vocabulary did not pass: " + level + "/" + card.id());
                require(!ReplyCheck.check(level, card, false, "The banana moon danced a happy banana dance today.").passed(),
                    "Unrelated reply passed: " + level + "/" + card.id());
                require(ReplyCheck.check(level, card, false, "").outcome() == Outcome.NO_SPEECH, "Silence passed");
                if (previous != null) {
                    Card lower = previous.get(card.id());
                    require(lower != null && card.topic().equals(lower.topic())
                        && !card.prompt().equals(lower.prompt()) && !card.example().equals(lower.example()),
                        "Missing differentiated prompt or example: " + card.id());
                }
                last = card;
            }
            require(!deck.draw().id().equals(last.id()), "Immediate repeat across deck cycles");
            if (level == PracticeLevel.B1) morning = cards.get("morning");
            previous = cards;
        }
        require(morning != null, "Missing morning scene");
        require(ReplyCheck.check(PracticeLevel.B1, morning, false, "I have breakfast and get dressed first").outcome()
            == Outcome.PERSONAL_ANSWER, "Valid personal answer was rejected");
        var retry = ReplyCheck.check(PracticeLevel.B1, morning, false, "I usually make foggy before I check my phone");
        require(retry.outcome() == Outcome.RETRY && retry.practiceWords().contains("coffee"), "Missing correction");
        require(ReplyCheck.check(PracticeLevel.B1, morning, true, "banana").outcome() == Outcome.RETRY,
            "Incorrect vocabulary passed");
        System.out.println("EnglishMC verified all " + SCENE_COUNT * 3 + " lessons and reply checks");

        if (args.length > 0) {
            var recognizer = new LocalRecognizer(Path.of("."));
            try (var sample = AudioSystem.getAudioInputStream(Path.of(args[0]).toFile())) {
                var format = sample.getFormat();
                require(format.getSampleRate() == 16000f && format.getChannels() == 1
                    && format.getSampleSizeInBits() == 16 && !format.isBigEndian(), "Expected 16 kHz mono PCM");
                String heard = recognizer.transcribe(sample.readAllBytes());
                require(!heard.isBlank(), "Vosk returned no words from the sample");
                if (args.length > 1) require(heard.contains(args[1]), "Sample did not contain expected words: " + heard);
                System.out.println("EnglishMC recognized sample: " + heard);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
