package io.github.kkskkkcc.englishmc.client;

import io.github.kkskkkcc.englishmc.PracticeLevel;
import io.github.kkskkkcc.englishmc.WorldPractice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;

public final class PracticeTab extends GridLayoutTab {
    private static Component text(String key) {
        return Component.translatable("englishmc.create." + key);
    }

    public PracticeTab(WorldCreationUiState state) {
        super(text("tab"));
        layout.rowSpacing(8);
        var rules = state.getGameRules();
        PracticeLevel saved = PracticeLevel.fromRule(rules.getInt(WorldPractice.LEVEL));
        PracticeLevel[] selected = {saved == null ? PracticeLevel.B1 : saved};

        var difficulty = CycleButton.<PracticeLevel>builder(level -> text(level.name()))
            .withValues(PracticeLevel.values()).withInitialValue(selected[0])
            .create(0, 0, 210, 20, text("difficulty"), (button, level) -> {
                selected[0] = level;
                rules.getRule(WorldPractice.LEVEL).set(level.ruleValue(), null);
            });
        difficulty.setTooltip(Tooltip.create(text("difficulty_help")));

        var interval = CycleButton.<Integer>builder(value -> Component.translatable("englishmc.create.minutes", value))
            .withValues(1, 2, 3, 5, 8, 10, 15, 20, 30, 45, 60)
            .withInitialValue(nearestInterval(WorldPractice.minutes(rules)))
            .create(0, 0, 210, 20, text("interval"), (button, minutes) ->
                rules.getRule(WorldPractice.INTERVAL).set(minutes, null));

        var enabled = CycleButton.onOffBuilder(saved != null)
            .create(0, 0, 210, 20, text("enabled"), (button, on) -> {
                rules.getRule(WorldPractice.LEVEL).set(on ? selected[0].ruleValue() : 0, null);
                difficulty.visible = difficulty.active = on;
                interval.visible = interval.active = on;
            });
        difficulty.visible = difficulty.active = saved != null;
        interval.visible = interval.active = saved != null;

        layout.addChild(enabled, 0, 0);
        layout.addChild(difficulty, 1, 0);
        layout.addChild(interval, 2, 0);
        layout.addChild(new MultiLineTextWidget(text("help"), Minecraft.getInstance().font)
            .setMaxWidth(210).setCentered(true), 3, 0);
    }

    private static int nearestInterval(int minutes) {
        int nearest = 5;
        for (int option : new int[] {1, 2, 3, 5, 8, 10, 15, 20, 30, 45, 60}) {
            if (Math.abs(option - minutes) < Math.abs(nearest - minutes)) nearest = option;
        }
        return nearest;
    }
}
