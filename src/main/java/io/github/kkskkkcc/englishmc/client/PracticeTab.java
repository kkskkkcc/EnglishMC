package io.github.kkskkkcc.englishmc.client;

import io.github.kkskkkcc.englishmc.PracticeLevel;
import io.github.kkskkkcc.englishmc.WorldPractice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;

public final class PracticeTab extends GridLayoutTab {
    private final WorldCreationUiState state;
    private final CycleButton<Boolean> enabled;
    private final CycleButton<PracticeLevel> difficulty;
    private final CycleButton<Integer> interval;

    private static Component text(String key) {
        return Component.translatable("englishmc.create." + key);
    }

    public PracticeTab(WorldCreationUiState state) {
        super(text("tab"));
        this.state = state;
        layout.rowSpacing(8);

        difficulty = CycleButton.<PracticeLevel>builder(level -> text(level.name()))
            .withValues(PracticeLevel.values()).withInitialValue(PracticeLevel.B1)
            .create(0, 0, 210, 20, text("difficulty"), (button, level) -> setLevel(level.ruleValue()));
        difficulty.setTooltip(Tooltip.create(text("difficulty_help")));

        interval = CycleButton.<Integer>builder(value -> Component.translatable("englishmc.create.minutes", value))
            .withValues(1, 2, 3, 5, 8, 10, 15, 20, 30, 45, 60)
            .create(0, 0, 210, 20, text("interval"), (button, minutes) ->
                state.getGameRules().getRule(WorldPractice.INTERVAL).set(minutes, null));

        enabled = CycleButton.onOffBuilder(false)
            .create(0, 0, 210, 20, text("enabled"), (button, on) -> {
                setLevel(on ? difficulty.getValue().ruleValue() : 0);
                showSelectors(on);
            });

        layout.addChild(enabled, 0, 0);
        layout.addChild(difficulty, 1, 0);
        layout.addChild(interval, 2, 0);
        layout.addChild(new MultiLineTextWidget(text("help"), Minecraft.getInstance().font)
            .setMaxWidth(210).setCentered(true), 3, 0);
        refreshSettings();
    }

    @Override
    public void doLayout(ScreenRectangle area) {
        refreshSettings();
        super.doLayout(area);
    }

    private void refreshSettings() {
        var rules = state.getGameRules();
        var saved = PracticeLevel.fromRule(rules.getInt(WorldPractice.LEVEL));
        enabled.setValue(saved != null);
        if (saved != null) difficulty.setValue(saved);
        interval.setValue(nearestInterval(WorldPractice.minutes(rules)));
        showSelectors(saved != null);
    }

    private void setLevel(int value) {
        // The vanilla Game Rules screen replaces this object when its changes are saved.
        state.getGameRules().getRule(WorldPractice.LEVEL).set(value, null);
    }

    private void showSelectors(boolean visible) {
        difficulty.visible = difficulty.active = visible;
        interval.visible = interval.active = visible;
    }

    private static int nearestInterval(int minutes) {
        int nearest = 5;
        for (int option : new int[] {1, 2, 3, 5, 8, 10, 15, 20, 30, 45, 60}) {
            if (Math.abs(option - minutes) < Math.abs(nearest - minutes)) nearest = option;
        }
        return nearest;
    }
}
