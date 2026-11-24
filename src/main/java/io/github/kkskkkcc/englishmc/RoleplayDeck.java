package io.github.kkskkkcc.englishmc;

import io.github.kkskkkcc.englishmc.LessonDeck.Bilingual;
import io.github.kkskkkcc.englishmc.LessonDeck.Card;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RoleplayDeck {
    public record Context(Bilingual situation, Bilingual playerRole, Bilingual partnerRole, Bilingual americanSlang,
                          Bilingual closing, String task, int turn, int total) {}

    private record Turn(Bilingual line, Bilingual shortReply, Bilingual detail, Bilingual furtherReply,
                        List<List<String>> cues, Bilingual vocabulary) {}

    private record Episode(String id, Bilingual topic, Bilingual setting, Bilingual player, Bilingual partner,
                           Bilingual slang, Bilingual closing, List<Turn> turns) {}

    private final PracticeLevel level;
    private final List<Episode> remaining = new ArrayList<>();
    private String previous;

    public RoleplayDeck(PracticeLevel level) {
        this.level = level;
    }

    public List<Card> draw() {
        if (remaining.isEmpty()) {
            remaining.addAll(Library.EPISODES);
            Collections.shuffle(remaining);
            if (remaining.size() > 1 && remaining.get(0).id().equals(previous)) {
                Collections.swap(remaining, 0, 1);
            }
        }
        Episode scene = remaining.remove(0);
        previous = scene.id();
        List<Card> cards = new ArrayList<>(scene.turns().size());
        for (int i = 0; i < scene.turns().size(); i++) {
            Turn turn = scene.turns().get(i);
            Context context = new Context(scene.setting(), scene.player(), scene.partner(), scene.slang(), scene.closing(),
                "englishmc.roleplay.task." + level.name() + "." + (i + 1), i + 1, scene.turns().size());
            cards.add(new Card(scene.id() + "." + (i + 1), scene.id(), scene.topic(), prompt(turn),
                example(turn), turn.cues(), turn.vocabulary(), context));
        }
        return List.copyOf(cards);
    }

    private Bilingual prompt(Turn turn) {
        if (level != PracticeLevel.A1) return turn.line();
        List<Bilingual> lines = turn.line().sentences();
        return lines.get(lines.size() - 1);
    }

    private Bilingual example(Turn turn) {
        return switch (level) {
            case A1 -> turn.shortReply();
            case A2 -> join(turn.shortReply(), turn.detail());
            case B1 -> join(turn.shortReply(), turn.furtherReply());
            case B2 -> join(turn.shortReply(), turn.detail(), turn.furtherReply());
            case CET4 -> join(turn.detail(), turn.shortReply());
            case CET6 -> join(turn.detail(), turn.shortReply(), turn.furtherReply());
            case IELTS_6_7 -> join(turn.shortReply(), turn.furtherReply(), turn.detail());
        };
    }

    private static Bilingual join(Bilingual lead, Bilingual next, Bilingual... more) {
        StringBuilder en = new StringBuilder(lead.english()).append(' ').append(next.english());
        StringBuilder zh = new StringBuilder(lead.chinese()).append(next.chinese());
        for (Bilingual line : more) {
            en.append(' ').append(line.english());
            zh.append(line.chinese());
        }
        return new Bilingual(en.toString(), zh.toString());
    }

    private static final class Library {
        private static final List<Episode> EPISODES = read();

        private static List<Episode> read() {
            var episodes = new ArrayList<Episode>();
            var unique = new HashSet<String>();
            String[] files = {"social", "food-shopping", "travel-home", "services-work", "community", "communication",
                "alternate-roles"};
            try {
                for (String name : files) load(name, episodes, unique);
            } catch (IOException error) {
                throw new IllegalStateException("Cannot load EnglishMC roleplays", error);
            }
            if (episodes.isEmpty()) throw new IllegalStateException("EnglishMC roleplay bank is empty");
            return List.copyOf(episodes);
        }

        private static void load(String name, List<Episode> episodes, Set<String> unique) throws IOException {
            String file = "/assets/englishmc/roleplays/" + name + ".txt";
            var stream = RoleplayDeck.class.getResourceAsStream(file);
            if (stream == null) throw new IOException("Missing " + file);
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String[] header = null;
                List<Turn> turns = new ArrayList<>();
                String line;
                int number = 0;
                while ((line = reader.readLine()) != null) {
                    number++;
                    if (line.isBlank() || line.startsWith("#")) continue;
                    boolean heading = line.startsWith("@");
                    String[] cells = line.substring(1).split("\\|", -1);
                    if ((!heading && !line.startsWith(">")) || cells.length != (heading ? 13 : 11)
                        || List.of(cells).stream().anyMatch(String::isBlank)) {
                        throw new IOException("Invalid roleplay row " + file + ":" + number);
                    }
                    if (heading) {
                        if (header != null) finish(header, turns, episodes, unique);
                        header = cells;
                        turns = new ArrayList<>(3);
                    } else {
                        if (header == null) throw new IOException("Roleplay turn without a scene");
                        turns.add(new Turn(pair(cells, 0), pair(cells, 2), pair(cells, 4), pair(cells, 6),
                            LessonDeck.cues(cells[8]), pair(cells, 9)));
                    }
                }
                if (header != null) finish(header, turns, episodes, unique);
            }
        }

        private static void finish(String[] header, List<Turn> turns, List<Episode> episodes, Set<String> unique)
            throws IOException {
            if (turns.size() != 3 || !unique.add(header[0])) {
                throw new IOException("Expected three turns and a unique roleplay ID for " + header[0]);
            }
            Bilingual slang = header[9].equals("-") ? null : pair(header, 9);
            episodes.add(new Episode(header[0], pair(header, 1), pair(header, 3), pair(header, 5),
                pair(header, 7), slang, pair(header, 11), List.copyOf(turns)));
        }

        private static Bilingual pair(String[] fields, int offset) {
            return new Bilingual(fields[offset], fields[offset + 1]);
        }
    }
}
