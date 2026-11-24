package io.github.kkskkkcc.englishmc.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.client.gui.overlay.ForgeGui;

final class PracticeHud {
    private static long chatUntil;
    private static PracticeSession.View lastView;
    private static List<Line> lastLines = List.of();
    private static int lastWidth;

    private PracticeHud() {}

    static void noteChat() {
        chatUntil = System.nanoTime() + 10_000_000_000L;
    }

    static void render(PracticeSession session, ForgeGui gui, GuiGraphics graphics, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) return;
        var view = session.view();
        if (view == null) return;
        int right = width - 10 - sidebarWidth(minecraft);
        int left = 10;
        int bottom = height - Math.max(65, Math.max(gui.leftHeight, gui.rightHeight) + 27);
        if (System.nanoTime() < chatUntil
            && minecraft.options.chatVisibility().get() != net.minecraft.world.entity.player.ChatVisiblity.HIDDEN) {
            int chatRight = gui.getChat().getWidth() + 10;
            if (right - chatRight >= 185) left = chatRight;
            else bottom = Math.min(bottom, height - gui.getChat().getHeight() - 48);
        }

        int space = right - left;
        int panelWidth = Math.min(398, Math.max(175, Math.min(space, (int) (width * 0.82))));
        Font font = minecraft.font;
        if (!view.equals(lastView) || lastWidth != panelWidth) {
            lastView = view;
            lastWidth = panelWidth;
            lastLines = lines(font, panelWidth - 20, view, minecraft);
        }
        int panelHeight = 13 + lastLines.stream().mapToInt(line -> line.gap() + 10).sum();
        float scale = Math.min(1f, Math.min((float) Math.max(40, space) / panelWidth,
            (float) Math.max(42, bottom - 38) / panelHeight));
        float scaledWidth = panelWidth * scale;
        float x = Math.max(left, Math.min((width - scaledWidth) / 2f, right - scaledWidth));
        float y = bottom - panelHeight * scale;

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);
        graphics.fill(0, 0, panelWidth, panelHeight, 0xe51b2027);
        graphics.fill(0, 0, panelWidth, 1, 0xff78c9e8);
        int lineY = 7;
        for (Line line : lastLines) {
            lineY += line.gap();
            graphics.drawString(font, line.text(), (panelWidth - font.width(line.text())) / 2, lineY,
                line.color(), false);
            lineY += 10;
        }
        pose.popPose();
    }

    private static List<Line> lines(Font font, int width, PracticeSession.View view, Minecraft minecraft) {
        var output = new ArrayList<Line>();
        String scene = minecraft.getLanguageManager().getSelected().startsWith("zh")
            ? view.scene().chinese() : view.scene().english();
        add(output, font, width, Component.literal(scene), 0xff9ee1fa, 0);
        add(output, font, width, Component.literal(view.prompt().english()), 0xffffffff, 3);
        add(output, font, width, Component.literal(view.prompt().chinese()), 0xffc2cbd4, 0);
        Component example = Component.translatable(view.vocabulary() ? "englishmc.hud.word" : "englishmc.hud.example",
            view.example().english());
        add(output, font, width, example, 0xfff9db9c, 3);
        add(output, font, width, Component.literal(view.example().chinese()), 0xffc2cbd4, 0);
        if (!view.transcript().isEmpty()) {
            add(output, font, width, Component.translatable("englishmc.hud.heard", view.transcript()), 0xffa4e6ca, 3);
        }
        int statusColor = view.alert() ? 0xffffb8a7 : view.passed() ? 0xffadf0b9 : 0xffa7bed8;
        add(output, font, width, view.status(), statusColor, 4);
        if (view.voiceError() != null) {
            add(output, font, width, Component.translatable(view.voiceError()), 0xffffb8a7, 2);
        }
        return List.copyOf(output);
    }

    private static void add(List<Line> output, Font font, int width, Component text, int color, int gap) {
        for (FormattedCharSequence line : font.split(text, width)) {
            output.add(new Line(line, color, gap));
            gap = 0;
        }
    }

    private static int sidebarWidth(Minecraft minecraft) {
        var scoreboard = minecraft.player.getScoreboard();
        var team = scoreboard.getPlayersTeam(minecraft.player.getScoreboardName());
        int slot = team == null ? -1 : team.getColor().getId();
        var objective = slot < 0 ? null : scoreboard.getDisplayObjective(3 + slot);
        if (objective == null) objective = scoreboard.getDisplayObjective(1);
        if (objective == null) return 0;
        Font font = minecraft.font;
        int width = font.width(objective.getDisplayName());
        for (var score : scoreboard.getPlayerScores(objective)) {
            String owner = score.getOwner();
            if (owner == null || owner.startsWith("#")) continue;
            var name = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(owner), Component.literal(owner));
            width = Math.max(width, font.width(name) + font.width(": " + score.getScore()));
        }
        return width + 6;
    }

    private record Line(FormattedCharSequence text, int color, int gap) {}
}
