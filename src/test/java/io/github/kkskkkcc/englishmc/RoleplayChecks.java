package io.github.kkskkkcc.englishmc;

import io.github.kkskkkcc.englishmc.LessonDeck.Card;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class RoleplayChecks {
    private static final int EPISODES = 100;
    private static final int COVERED_TOPICS = 90;

    private RoleplayChecks() {}

    static void verify() {
        Set<Integer> topics = IntStream.range(0, COVERED_TOPICS).boxed().collect(Collectors.toSet());
        Map<String, List<Card>> previousLevel = null;
        int totalTurns = 0;
        int shortenedPrompts = 0;
        for (PracticeLevel level : PracticeLevel.values()) {
            var deck = new RoleplayDeck(level);
            var scenes = new HashMap<String, List<Card>>();
            Set<Integer> seenTopics = new HashSet<>();
            Set<String> rolePairs = new HashSet<>();
            int slangNotes = 0;
            String lastScene = null;
            for (int i = 0; i < EPISODES; i++) {
                List<Card> turns = deck.draw();
                require(turns.size() == 3, "Expected three connected turns");
                lastScene = turns.get(0).topic();
                require(scenes.put(lastScene, turns) == null, "Scene repeated before deck cleared: " + lastScene);
                seenTopics.add(Integer.parseInt(lastScene.substring(0, 2)));
                var context = turns.get(0).roleplay();
                rolePairs.add(context.playerRole().english() + " / " + context.partnerRole().english());
                if (context.americanSlang() != null) slangNotes++;
                require(!context.situation().english().isBlank() && !context.closing().chinese().isBlank(),
                    "Missing setting or closing: " + lastScene);
                for (int turnIndex = 0; turnIndex < turns.size(); turnIndex++) {
                    Card card = turns.get(turnIndex);
                    var step = card.roleplay();
                    require(card.topic().equals(lastScene) && step.turn() == turnIndex + 1 && step.total() == 3,
                        "Incorrect turn progression: " + card.id());
                    require(step.partnerRole().equals(context.partnerRole()) && step.closing().equals(context.closing()),
                        "Role or conclusion changed halfway: " + card.id());
                    require(ReplyCheck.check(level, card, false, card.example().english()).passed(),
                        "Demonstration was rejected: " + card.id());
                    require(!ReplyCheck.check(level, card, false, "").passed(), "Silence was accepted: " + card.id());
                    require(ReplyCheck.check(level, card, true, card.vocabulary().english()).passed(),
                        "Vocabulary example was rejected: " + card.id());
                    if (previousLevel != null) {
                        Card lower = previousLevel.get(lastScene).get(turnIndex);
                        require(!card.example().equals(lower.example()) && !step.task().equals(lower.roleplay().task()),
                            "Two neighboring levels have identical tasks or model answers: " + card.id());
                        if (level == PracticeLevel.A2 && lower.prompt().english().length() < card.prompt().english().length()) {
                            shortenedPrompts++;
                        }
                    }
                    totalTurns++;
                }
            }
            require(seenTopics.equals(topics), "Missing requested topic at " + level);
            require(rolePairs.size() == EPISODES && slangNotes == 68, "Role pairs or slang tags are missing");
            require(!deck.draw().get(0).topic().equals(lastScene), "Repeated across shuffled cycle");
            previousLevel = scenes;
        }
        require(shortenedPrompts > 50 && totalTurns == 2100, "Incomplete leveled bank");
        System.out.println("EnglishMC roleplays verified: 90 topics, 100 scenarios, 68 slang notes, " + totalTurns
            + " leveled turns");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
