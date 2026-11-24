package io.github.kkskkkcc.englishmc;

import net.minecraft.world.level.GameRules;

public final class WorldPractice {
    public static final GameRules.Key<GameRules.IntegerValue> LEVEL = GameRules.register(
        "englishMcLevel", GameRules.Category.MISC, GameRules.IntegerValue.create(0));
    public static final GameRules.Key<GameRules.IntegerValue> INTERVAL = GameRules.register(
        "englishMcInterval", GameRules.Category.MISC, GameRules.IntegerValue.create(5));

    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 60;

    private WorldPractice() {}

    static void register() {}

    public static int minutes(GameRules rules) {
        return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, rules.getInt(INTERVAL)));
    }
}
