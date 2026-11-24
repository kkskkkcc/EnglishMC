package io.github.kkskkkcc.englishmc.client;

import io.github.kkskkkcc.englishmc.mixin.GuiTooltipAccessor;
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
        int stackHeight = Math.max(gui.leftHeight, gui.rightHeight);
        // Forge reserves an armor row even when the armor bar is empty.
        int leftStack = gui.leftHeight;
        if (minecraft.gameMode.canHurtPlayer() && minecraft.player.getArmorValue() == 0) leftStack -= 10;
        int clearance = Math.max(35, Math.max(leftStack, gui.rightHeight) - 5);
        if (((GuiTooltipAccessor) gui).englishmc$itemNameTicks() > 0
            && !minecraft.player.getMainHandItem().isEmpty()) {
            int itemNameY = Math.max(59, stackHeight) - (minecraft.gameMode.canHurtPlayer() ? 0 : 14);
            clearance = Math.max(clearance, itemNameY + 6);
        }
        int bottom = height - clearance;
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
        boolean chinese = minecraft.getLanguageManager().getSelected().startsWith("zh");
        String scene = chinese ? view.scene().chinese() : view.scene().english();
        var roleplay = view.roleplay();
        Component title = roleplay == null || view.vocabulary() ? Component.literal(scene)
            : Component.translatable("englishmc.roleplay.title", scene, roleplay.turn(), roleplay.total());
        add(output, font, width, title, 0xff9ee1fa, 0);
        if (roleplay != null && !view.vocabulary()) {
            add(output, font, width, Component.translatable("englishmc.roleplay.roles",
                chinese ? roleplay.playerRole().chinese() : roleplay.playerRole().english(),
                chinese ? roleplay.partnerRole().chinese() : roleplay.partnerRole().english()), 0xffc2cbd4, 2);
            if (roleplay.turn() == 1) {
                add(output, font, width, Component.literal(chinese ? roleplay.situation().chinese()
                    : roleplay.situation().english()), 0xffc2cbd4, 1);
            }
            add(output, font, width, Component.translatable(roleplay.task()), 0xffa7bed8, 2);
        }
        for (var sentence : view.prompt().sentences()) {
            add(output, font, width, Component.literal(sentence.english()), 0xffffffff, 3);
            add(output, font, width, Component.literal(sentence.chinese()), 0xffc2cbd4, 0);
        }
        String exampleKey = view.vocabulary() ? "englishmc.hud.word" : "englishmc.hud.example";
        for (var sentence : view.example().sentences()) {
            Component example = exampleKey == null ? Component.literal(sentence.english())
                : Component.translatable(exampleKey, sentence.english());
            add(output, font, width, example, 0xfff9db9c, 3);
            add(output, font, width, Component.literal(sentence.chinese()), 0xffc2cbd4, 0);
            exampleKey = null;
        }
        if (roleplay != null && roleplay.americanSlang() != null && !view.vocabulary()) {
            var term = roleplay.americanSlang();
            add(output, font, width, Component.translatable("englishmc.roleplay.slang", term.english(),
                term.chinese()), 0xffd5b4ef, 3);
        }
        if (!view.transcript().isEmpty()) {
            add(output, font, width, Component.translatable("englishmc.hud.heard", view.transcript()), 0xffa4e6ca, 3);
        }
        if (roleplay != null && view.passed() && !view.vocabulary() && roleplay.turn() == roleplay.total()) {
            for (var sentence : roleplay.closing().sentences()) {
                add(output, font, width, Component.literal(sentence.english()), 0xffffffff, 2);
                add(output, font, width, Component.literal(sentence.chinese()), 0xffc2cbd4, 0);
            }
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
