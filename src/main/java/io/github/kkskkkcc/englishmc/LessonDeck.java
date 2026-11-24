package io.github.kkskkkcc.englishmc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LessonDeck {
    public record Bilingual(String english, String chinese) {
        public List<Bilingual> sentences() {
            String[] en = english.split("(?<=[.!?])\\s+");
            String[] zh = chinese.split("(?<=[。？！?!])\\s*");
            if (en.length != zh.length || en.length == 1) return List.of(this);
            var pairs = new ArrayList<Bilingual>(en.length);
            for (int i = 0; i < en.length; i++) pairs.add(new Bilingual(en[i].strip(), zh[i].strip()));
            return List.copyOf(pairs);
        }
    }

    public record Card(String id, String topic, Bilingual scene, Bilingual prompt, Bilingual example,
                       List<List<String>> cues, Bilingual vocabulary) {}

    private final List<Card> source;
    private final List<Card> remaining = new ArrayList<>();
    private String lastId;

    public LessonDeck(PracticeLevel level) {
        source = Library.BY_LEVEL.get(level);
    }

    public Card draw() {
        refill();
        if (remaining.size() > 1 && remaining.get(0).id().equals(lastId)) {
            Collections.swap(remaining, 0, 1);
        }
        return take(0);
    }

    public Card followUp(Card previous) {
        for (int index = 0; index < remaining.size(); index++) {
            if (remaining.get(index).topic().equals(previous.topic())) return take(index);
        }
        return null;
    }

    private Card take(int index) {
        Card next = remaining.remove(index);
        lastId = next.id();
        return next;
    }

    private void refill() {
        if (!remaining.isEmpty()) return;
        remaining.addAll(source);
        Collections.shuffle(remaining);
    }

    private static final class Library {
        private static final Map<PracticeLevel, List<Card>> BY_LEVEL = load();

        private static Map<PracticeLevel, List<Card>> load() {
            var byLevel = new EnumMap<PracticeLevel, List<Card>>(PracticeLevel.class);
            var ids = new EnumMap<PracticeLevel, Set<String>>(PracticeLevel.class);
            for (PracticeLevel level : PracticeLevel.values()) {
                byLevel.put(level, new ArrayList<>());
                ids.put(level, new HashSet<>());
            }

            try (var stream = LessonDeck.class.getResourceAsStream("/assets/englishmc/lessons/scenes.txt")) {
                if (stream == null) throw new IOException("Missing EnglishMC lesson bank");
                try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    int number = 0;
                    while ((line = reader.readLine()) != null) {
                        number++;
                        if (line.isBlank() || line.startsWith("#")) continue;
                        String[] fields = line.split("\\|", -1);
                        if (fields.length != 12 || List.of(fields).stream().anyMatch(String::isBlank)) {
                            throw new IOException("Invalid lesson bank row " + number);
                        }
                        PracticeLevel level = PracticeLevel.valueOf(fields[4]);
                        if (!ids.get(level).add(fields[0])) throw new IOException("Duplicate scene at row " + number);
                        List<List<String>> cues = new ArrayList<>();
                        for (String group : fields[9].split(";")) {
                            cues.add(List.of(group.split(",")));
                        }
                        byLevel.get(level).add(new Card(fields[0], fields[1], new Bilingual(fields[2], fields[3]),
                            new Bilingual(fields[5], fields[6]), new Bilingual(fields[7], fields[8]),
                            List.copyOf(cues), new Bilingual(fields[10], fields[11])));
                    }
                }
            } catch (IOException error) {
                throw new IllegalStateException("Cannot read EnglishMC lessons", error);
            }
            var baseline = ids.get(PracticeLevel.B1);
            if (baseline.isEmpty() || ids.values().stream().anyMatch(set -> !set.equals(baseline))) {
                throw new IllegalStateException("Each scene must be present at every EnglishMC difficulty");
            }
            byLevel.replaceAll((level, cards) -> List.copyOf(cards));
            return Map.copyOf(byLevel);
        }
    }
}
