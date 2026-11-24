package io.github.kkskkkcc.englishmc;

public enum PracticeLevel {
    B1(1, 18), B2(2, 24), IELTS7(3, 35);

    private final int ruleValue;
    private final int recordingSeconds;

    PracticeLevel(int ruleValue, int recordingSeconds) {
        this.ruleValue = ruleValue;
        this.recordingSeconds = recordingSeconds;
    }

    public int ruleValue() {
        return ruleValue;
    }

    public int recordingSeconds() {
        return recordingSeconds;
    }

    public static PracticeLevel fromRule(int value) {
        for (PracticeLevel level : values()) {
            if (level.ruleValue == value) return level;
        }
        return null;
    }
}
