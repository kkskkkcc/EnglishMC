package io.github.kkskkkcc.englishmc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SpeechEquivalent {
    private static final Map<String, Integer> NUMBERS = Map.ofEntries(
        Map.entry("zero", 0), Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3),
        Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7),
        Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11),
        Map.entry("twelve", 12), Map.entry("thirteen", 13), Map.entry("fourteen", 14), Map.entry("fifteen", 15),
        Map.entry("sixteen", 16), Map.entry("seventeen", 17), Map.entry("eighteen", 18), Map.entry("nineteen", 19),
        Map.entry("twenty", 20), Map.entry("thirty", 30), Map.entry("forty", 40), Map.entry("fifty", 50),
        Map.entry("sixty", 60), Map.entry("seventy", 70), Map.entry("eighty", 80), Map.entry("ninety", 90));
    private static final Map<String, String> HOMOPHONES = Map.ofEntries(
        Map.entry("won", "1"), Map.entry("to", "2"), Map.entry("too", "2"),
        Map.entry("for", "4"), Map.entry("fore", "4"), Map.entry("ate", "8"),
        Map.entry("hear", "here"), Map.entry("their", "there"), Map.entry("write", "right"),
        Map.entry("rite", "right"), Map.entry("know", "no"), Map.entry("buy", "by"), Map.entry("bye", "by"),
        Map.entry("knew", "new"), Map.entry("wear", "where"), Map.entry("witch", "which"),
        Map.entry("piece", "peace"), Map.entry("week", "weak"), Map.entry("see", "sea"));

    private SpeechEquivalent() {}

    static boolean matches(String a, String b) {
        return HOMOPHONES.getOrDefault(a, a).equals(HOMOPHONES.getOrDefault(b, b));
    }

    static List<String> normalizeNumbers(List<String> tokens) {
        var normalized = new ArrayList<String>();
        for (int i = 0; i < tokens.size(); i++) {
            String word = tokens.get(i);
            int number = smallNumber(word);
            String next = i + 1 < tokens.size() ? tokens.get(i + 1) : "";
            if (next.equals("hundred") && (word.equals("a") || (number >= 1 && number <= 9))) {
                number = (word.equals("a") ? 1 : number) * 100;
                i++;
                int remainderIndex = i + 1;
                if (remainderIndex < tokens.size() && tokens.get(remainderIndex).equals("and")) remainderIndex++;
                if (remainderIndex < tokens.size()) {
                    int remainder = smallNumber(tokens.get(remainderIndex));
                    if (remainder >= 0) {
                        number += remainder;
                        i = remainderIndex;
                        if (isTens(remainder) && followingDigit(tokens, i) > 0) {
                            number += followingDigit(tokens, i);
                            i++;
                        }
                    }
                }
            } else if (isTens(number) && followingDigit(tokens, i) > 0) {
                number += followingDigit(tokens, i);
                i++;
            } else if (number >= 0 && number <= 9 && followingDigit(tokens, i) >= 0) {
                var digits = new StringBuilder().append(number);
                while (followingDigit(tokens, i) >= 0) digits.append(followingDigit(tokens, i++));
                normalized.add(digits.toString());
                continue;
            }
            normalized.add(number >= 0 ? Integer.toString(number) : word);
        }
        return normalized;
    }

    private static int smallNumber(String word) {
        if (word.length() == 1 && word.charAt(0) >= '0' && word.charAt(0) <= '9') {
            return word.charAt(0) - '0';
        }
        return NUMBERS.getOrDefault(word, -1);
    }

    private static int followingDigit(List<String> words, int index) {
        if (index + 1 >= words.size()) return -1;
        String word = words.get(index + 1);
        if (word.equals("oh") && index + 2 < words.size() && smallNumber(words.get(index + 2)) >= 0) return 0;
        int value = smallNumber(word);
        return value <= 9 ? value : -1;
    }

    private static boolean isTens(int number) {
        return number >= 20 && number % 10 == 0;
    }
}
