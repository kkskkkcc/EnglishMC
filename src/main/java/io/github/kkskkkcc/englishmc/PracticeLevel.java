package io.github.kkskkkcc.englishmc;

public enum PracticeLevel {
    A1(4, 12, 1, -3, false),
    A2(5, 16, 3, -2, false),
    B1(1, 18, 5, -1, false),
    B2(2, 24, 8, -1, false),
    CET4(6, 30, 9, -1, true),
    CET6(7, 45, 15, 0, true),
    IELTS_6_7(3, 55, 15, 0, true);

    private final int ruleValue;
    private final int recordingSeconds;
    private final int minimumWords;
    private final int narrationRate;
    private final boolean extendedReply;

    PracticeLevel(int ruleValue, int recordingSeconds, int minimumWords, int narrationRate, boolean extendedReply) {
        this.ruleValue = ruleValue;
        this.recordingSeconds = recordingSeconds;
        this.minimumWords = minimumWords;
        this.narrationRate = narrationRate;
        this.extendedReply = extendedReply;
    }

    public int ruleValue() {
        return ruleValue;
    }

    public int recordingSeconds() {
        return recordingSeconds;
    }

    public int minimumWords() {
        return minimumWords;
    }

    public int narrationRate() {
        return narrationRate;
    }

    public boolean extendedReply() {
        return extendedReply;
    }

    public static PracticeLevel fromRule(int value) {
        for (PracticeLevel level : values()) {
            if (level.ruleValue == value) return level;
        }
        return null;
    }
}
