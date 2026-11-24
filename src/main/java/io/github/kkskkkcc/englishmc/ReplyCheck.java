package io.github.kkskkkcc.englishmc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ReplyCheck {
    public enum Outcome { MATCHED, PERSONAL_ANSWER, RETRY, NO_SPEECH }

    public record Result(Outcome outcome, String practiceWords) {
        public boolean passed() {
            return outcome == Outcome.MATCHED || outcome == Outcome.PERSONAL_ANSWER;
        }
    }

    private static final Map<String, String> CONTRACTIONS = Map.ofEntries(
        Map.entry("i'm", "i am"), Map.entry("you're", "you are"), Map.entry("we're", "we are"),
        Map.entry("they're", "they are"), Map.entry("he's", "he is"), Map.entry("she's", "she is"),
        Map.entry("it's", "it is"), Map.entry("that's", "that is"), Map.entry("there's", "there is"),
        Map.entry("what's", "what is"), Map.entry("i've", "i have"), Map.entry("we've", "we have"),
        Map.entry("they've", "they have"), Map.entry("you've", "you have"),
        Map.entry("i'd", "i would"), Map.entry("you'd", "you would"), Map.entry("we'd", "we would"),
        Map.entry("i'll", "i will"), Map.entry("you'll", "you will"), Map.entry("we'll", "we will"),
        Map.entry("don't", "do not"), Map.entry("doesn't", "does not"), Map.entry("didn't", "did not"),
        Map.entry("can't", "can not"), Map.entry("cannot", "can not"), Map.entry("couldn't", "could not"),
        Map.entry("won't", "will not"), Map.entry("wouldn't", "would not"),
        Map.entry("isn't", "is not"), Map.entry("aren't", "are not"), Map.entry("wasn't", "was not"),
        Map.entry("weren't", "were not"), Map.entry("haven't", "have not"), Map.entry("hasn't", "has not"),
        Map.entry("shouldn't", "should not"), Map.entry("let's", "let us"),
        Map.entry("gonna", "going to"), Map.entry("wanna", "want to"));
    private static final Set<String> FILLERS = Set.of("uh", "um", "er", "hmm", "ah");
    private static final Set<String> DEVELOPMENT = Set.of("because", "since", "although", "though", "while",
        "when", "if", "unless", "so", "which", "but", "however", "therefore", "instead", "example",
        "especially", "despite", "otherwise", "rather", "whereas", "even", "reason", "first", "then", "also");

    private static final Set<String> SUBJECTS = Set.of("i", "we", "you", "they", "he", "she", "it", "there");
    private static final Set<String> ADVERBS = Set.of("usually", "often", "always", "sometimes", "rarely",
        "normally", "just", "still", "also", "really", "already", "probably", "generally", "never", "first");
    private static final Set<String> VERBS = Set.of(
        "am", "is", "are", "was", "were", "can", "could", "will", "would", "should", "may", "might",
        "do", "does", "did", "have", "has", "had", "need", "needs", "want", "wants", "go", "goes", "went",
        "get", "gets", "got", "like", "likes", "liked", "love", "loves", "make", "makes", "made", "eat",
        "eats", "ate", "take", "takes", "took", "buy", "bought", "meet", "met", "see", "saw", "use", "used",
        "call", "called", "text", "send", "sent", "live", "lives", "work", "works", "study", "studies",
        "look", "looks", "check", "checked", "ask", "asked", "think", "thinks", "thought", "know", "knows",
        "feel", "felt", "prefer", "try", "tried", "start", "started", "plan", "planned", "pay", "paid",
        "put", "find", "found", "say", "said", "leave", "left", "bring", "brought", "read", "write",
        "walk", "walked", "run", "ran", "play", "watch", "choose", "keep", "help", "sleep", "drink", "cook");
    private static final Set<String> SHORT_REPLY_OPENERS = Set.of("at", "by", "in", "on", "near", "next",
        "outside", "inside", "yes", "no", "sure", "one", "two", "three", "a", "some", "the");
    private static final Set<String> RESPONSE_OPENERS = Set.of("go", "take", "turn", "stay", "start", "put",
        "look", "head", "walk", "keep", "make", "try", "come", "read", "leave", "please", "thanks");

    private ReplyCheck() {}

    public static Result check(PracticeLevel level, LessonDeck.Card card, boolean vocabulary, String heard) {
        List<String> actual = words(heard);
        if (actual.isEmpty()) return new Result(Outcome.NO_SPEECH, "");

        List<String> expected = words(vocabulary ? card.vocabulary().english() : card.example().english());
        Alignment alignment = align(expected, actual);
        if (alignment.edits == 0) return new Result(Outcome.MATCHED, "");

        if (vocabulary) return new Result(Outcome.RETRY, alignment.focus);
        boolean closeToExample = alignment.matched >= Math.max(3, (expected.size() * 3 + 3) / 4);

        int minimumWords = level.minimumWords();
        String phrase = phrase(actual);
        boolean relevant = card.cues().stream().allMatch(group -> group.stream()
            .anyMatch(cue -> phrase.contains(phrase(words(cue)))));
        boolean developed = !level.extendedReply() || actual.stream().anyMatch(DEVELOPMENT::contains);
        boolean isResponse = hasResponseStructure(level, actual);
        // A near match can be an intentional substitution, such as tea for coffee.
        // Require an added topic cue in that case rather than accepting a garbled read-aloud.
        String newWords = phrase(alignment.added);
        boolean topicSubstitution = card.cues().stream().flatMap(List::stream)
            .anyMatch(cue -> newWords.contains(phrase(words(cue))));
        if (actual.size() >= minimumWords && relevant && developed && isResponse
            && (!closeToExample || topicSubstitution) && !actual.equals(words(card.prompt().english()))) {
            return new Result(Outcome.PERSONAL_ANSWER, "");
        }
        return new Result(Outcome.RETRY, closeToExample ? alignment.focus : "");
    }

    private static boolean hasResponseStructure(PracticeLevel level, List<String> actual) {
        String first = actual.get(0);
        if (level == PracticeLevel.A1 && actual.size() <= 3) return true;
        if (!level.extendedReply() && actual.size() <= 8 && SHORT_REPLY_OPENERS.contains(first)) return true;
        if (RESPONSE_OPENERS.contains(first) || first.equals("please") || actual.get(actual.size() - 1).equals("please")) {
            return true;
        }
        for (int i = 0; i + 1 < actual.size(); i++) {
            String word = actual.get(i);
            int next = SUBJECTS.contains(word) ? i + 1 : word.equals("my") ? i + 2 : actual.size();
            while (next < actual.size() && ADVERBS.contains(actual.get(next))) next++;
            if (next < actual.size() && VERBS.contains(actual.get(next))) return true;
        }
        return false;
    }

    private static String phrase(List<String> tokens) {
        return " " + String.join(" ", tokens) + " ";
    }

    static List<String> words(String line) {
        var tokens = new ArrayList<String>();
        String clean = line.toLowerCase(Locale.ROOT).replace('’', '\'')
            .replaceAll("[^a-z0-9']+", " ").strip();
        for (String token : clean.split(" +")) {
            if (!token.isEmpty() && !FILLERS.contains(token)) {
                tokens.addAll(List.of(CONTRACTIONS.getOrDefault(token, token.replace("'", "")).split(" ")));
            }
        }
        return tokens;
    }

    private static Alignment align(List<String> expected, List<String> actual) {
        int[][] edits = new int[expected.size() + 1][actual.size() + 1];
        for (int i = 0; i < edits.length; i++) edits[i][0] = i;
        for (int j = 0; j < edits[0].length; j++) edits[0][j] = j;
        for (int i = 1; i < edits.length; i++) {
            for (int j = 1; j < edits[i].length; j++) {
                int replacement = expected.get(i - 1).equals(actual.get(j - 1)) ? 0 : 1;
                edits[i][j] = Math.min(edits[i - 1][j - 1] + replacement,
                    1 + Math.min(edits[i - 1][j], edits[i][j - 1]));
            }
        }
        var focus = new ArrayList<String>();
        var added = new ArrayList<String>();
        int i = expected.size();
        int j = actual.size();
        int matched = 0;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && expected.get(i - 1).equals(actual.get(j - 1))) {
                i--;
                j--;
                matched++;
            } else if (i > 0 && edits[i][j] == edits[i - 1][j] + 1) {
                focus.add(0, expected.get(--i));
            } else if (j > 0 && edits[i][j] == edits[i][j - 1] + 1) {
                added.add(0, actual.get(--j));
            } else {
                focus.add(0, expected.get(--i));
                added.add(0, actual.get(--j));
            }
        }
        return new Alignment(edits[expected.size()][actual.size()], matched,
            String.join(" · ", focus.stream().distinct().limit(3).toList()), List.copyOf(added));
    }

    private record Alignment(int edits, int matched, String focus, List<String> added) {}
}
