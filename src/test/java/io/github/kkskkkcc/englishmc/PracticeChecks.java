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
        require(PracticeLevel.fromRule(1) == PracticeLevel.B1
            && PracticeLevel.fromRule(2) == PracticeLevel.B2
            && PracticeLevel.fromRule(3) == PracticeLevel.IELTS_6_7, "Existing world mappings changed");
        var values = PracticeLevel.values();
        require(values.length == 7, "Missing difficulty choices");
        for (PracticeLevel level : values) {
            require(PracticeLevel.fromRule(level.ruleValue()) == level, "Difficulty value collision");
        }
        Map<String, Card> previous = null;
        Card morning = null;
        Card urgent = null;
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
                require(!ReplyCheck.check(level, card, false, "Purple obelisks orbit distant galaxies.").passed(),
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
            if (level == PracticeLevel.B1) {
                morning = cards.get("morning");
                urgent = cards.get("urgent");
            }
            previous = cards;
        }
        require(morning != null && urgent != null, "Missing regression scenes");
        require(urgent.prompt().sentences().size() == 1, "Expected the single location question");
        require(!ReplyCheck.check(PracticeLevel.B1, urgent, false, "i and the for ah answer on foot the library").passed(),
            "Incomplete sentence from live game was incorrectly accepted");
        require(ReplyCheck.check(PracticeLevel.B1, urgent, false, "I am waiting outside the library").outcome()
            == Outcome.PERSONAL_ANSWER, "A valid response to the location question was rejected");
        require(ReplyCheck.check(PracticeLevel.B1, morning, false, "I have breakfast and get dressed first").outcome()
            == Outcome.PERSONAL_ANSWER, "Valid personal answer was rejected");
        var retry = ReplyCheck.check(PracticeLevel.B1, morning, false, "I usually make foggy before I check my phone");
        require(retry.outcome() == Outcome.RETRY && retry.practiceWords().contains("coffee"), "Missing correction");
        require(ReplyCheck.check(PracticeLevel.B1, morning, false, "I usually make tea before I check my phone").outcome()
            == Outcome.PERSONAL_ANSWER, "Valid word substitution was rejected");
        require(ReplyCheck.check(PracticeLevel.B1, morning, true, "banana").outcome() == Outcome.RETRY,
            "Incorrect vocabulary passed");
        System.out.println("EnglishMC verified all " + SCENE_COUNT * values.length + " lessons and reply checks");

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
