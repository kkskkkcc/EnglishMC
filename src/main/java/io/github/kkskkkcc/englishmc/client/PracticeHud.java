package io.github.kkskkkcc.englishmc.client;

import io.github.kkskkkcc.englishmc.mixin.GuiTooltipAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.client.gui.overlay.ForgeGui;

final class PracticeHud {
    private static final int TRANSLATION_COLOR = 0xffe2e5e9;
    private static final int COLUMN_GAP = 16;
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
        int maxWidth = view.roleplay() == null ? 398 : 454;
        int panelWidth = Math.min(maxWidth, Math.max(175, Math.min(space, (int) (width * 0.84))));
        Font font = minecraft.font;
        if (!view.equals(lastView) || lastWidth != panelWidth) {
            lastView = view;
            lastWidth = panelWidth;
            lastLines = lines(font, panelWidth - 20, view, minecraft);
        }
        int panelHeight = 6 + lastLines.stream().mapToInt(line -> line.gap() + 10).sum();
        float verticalScale = Math.max(0.66f, (float) (bottom - height / 2 - 8) / panelHeight);
        float scale = Math.min(1f, Math.min((float) Math.max(40, space) / panelWidth,
            Math.min(verticalScale, (float) Math.max(42, bottom - 38) / panelHeight)));
        float scaledWidth = panelWidth * scale;
        float x = Math.max(left, Math.min((width - scaledWidth) / 2f, right - scaledWidth));
        float y = bottom - panelHeight * scale;

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);
        int lineY = 3;
        int translationX = 10 + englishWidth(panelWidth - 20) + COLUMN_GAP;
        for (Line line : lastLines) {
            lineY += line.gap();
            if (line.translation() == null) {
                graphics.drawString(font, line.text(), (panelWidth - font.width(line.text())) / 2, lineY,
                    line.color(), true);
            } else {
                graphics.drawString(font, line.text(), 10, lineY, line.color(), true);
                graphics.drawString(font, line.translation(), translationX, lineY, TRANSLATION_COLOR, true);
            }
            lineY += 10;
        }
        pose.popPose();
    }

    private static List<Line> lines(Font font, int width, PracticeSession.View view, Minecraft minecraft) {
        var output = new ArrayList<Line>();
        boolean chinese = minecraft.getLanguageManager().getSelected().startsWith("zh");
        String scene = chinese ? view.scene().chinese() : view.scene().english();
        var roleplay = view.roleplay();
        boolean inRole = roleplay != null && !view.vocabulary();
        boolean columns = inRole && width >= 270;
        boolean closing = inRole && view.passed() && roleplay.turn() == roleplay.total();
        Component title = inRole
            ? Component.translatable("englishmc.roleplay.title", scene, roleplay.turn(), roleplay.total())
            : Component.literal(scene);
        add(output, font, width, title, 0xffa5ebff, 0);
        if (inRole) {
            String roles = I18n.get("englishmc.roleplay.roles",
                chinese ? roleplay.playerRole().chinese() : roleplay.playerRole().english(),
                chinese ? roleplay.partnerRole().chinese() : roleplay.partnerRole().english());
            add(output, font, width, Component.literal(roles), TRANSLATION_COLOR, 1);
            if (roleplay.turn() == 1) {
                add(output, font, width, Component.literal(chinese ? roleplay.situation().chinese()
                    : roleplay.situation().english()), TRANSLATION_COLOR, 0);
            }
            if (!closing) add(output, font, width, Component.translatable(roleplay.task()), 0xffc0d7f1, 1);
        }
        for (var sentence : view.prompt().sentences()) {
            addPair(output, font, width, Component.literal(sentence.english()), sentence.chinese(),
                0xffffffff, columns);
        }
        if (!closing) {
            String exampleKey = view.vocabulary() ? "englishmc.hud.word" : "englishmc.hud.example";
            for (var sentence : view.example().sentences()) {
                Component example = exampleKey == null ? Component.literal(sentence.english())
                    : Component.translatable(exampleKey, sentence.english());
                addPair(output, font, width, example, sentence.chinese(), 0xffffe0a2, columns);
                exampleKey = null;
            }
        }
        if (inRole && !closing && roleplay.americanSlang() != null) {
            var term = roleplay.americanSlang();
            add(output, font, width, Component.translatable("englishmc.roleplay.slang", term.english(),
                term.chinese()), 0xffe2c3ff, 2);
        }
        if (!view.transcript().isEmpty()) {
            add(output, font, width, Component.translatable("englishmc.hud.heard", view.transcript()), 0xffb4f5d8, 3);
        }
        if (closing) {
            for (var sentence : roleplay.closing().sentences()) {
                addPair(output, font, width, Component.literal(sentence.english()), sentence.chinese(),
                    0xffffffff, columns);
            }
        }
        int statusColor = view.alert() ? 0xffffb8a7 : view.passed() ? 0xffadf0b9 : 0xffc0d7f1;
        add(output, font, width, view.status(), statusColor, 3);
        if (view.voiceError() != null) {
            add(output, font, width, Component.translatable(view.voiceError()), 0xffffb8a7, 2);
        }
        return List.copyOf(output);
    }

    private static void addPair(List<Line> output, Font font, int width, Component english, String chinese,
                                int color, boolean columns) {
        Component translation = Component.literal(chinese);
        if (!columns) {
            add(output, font, width, english, color, 3);
            add(output, font, width, translation, TRANSLATION_COLOR, 0);
            return;
        }
        int englishWidth = englishWidth(width);
        var left = font.split(english, englishWidth);
        var right = font.split(translation, width - englishWidth - COLUMN_GAP);
        for (int index = 0; index < Math.max(left.size(), right.size()); index++) {
            output.add(new Line(index < left.size() ? left.get(index) : FormattedCharSequence.EMPTY,
                index < right.size() ? right.get(index) : FormattedCharSequence.EMPTY, color, index == 0 ? 2 : 0));
        }
    }

    private static int englishWidth(int width) {
        return (int) ((width - COLUMN_GAP) * 0.57);
    }

    private static void add(List<Line> output, Font font, int width, Component text, int color, int gap) {
        for (FormattedCharSequence line : font.split(text, width)) {
            output.add(new Line(line, null, color, gap));
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

    private record Line(FormattedCharSequence text, FormattedCharSequence translation, int color, int gap) {}
}
