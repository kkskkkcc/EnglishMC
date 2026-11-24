package io.github.kkskkkcc.englishmc.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.kkskkkcc.englishmc.EnglishMC;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = EnglishMC.ID, value = Dist.CLIENT)
public final class ClientHooks {
    static final KeyMapping RECORD = key("record", GLFW.GLFW_KEY_V);
    static final KeyMapping REPLAY = key("replay", GLFW.GLFW_KEY_J);
    private static PracticeSession session;

    private ClientHooks() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.englishmc." + name, KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, code, "key.categories.englishmc");
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (session == null) {
            if (minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
            session = new PracticeSession(minecraft);
        }
        boolean record = false;
        boolean replay = false;
        while (RECORD.consumeClick()) record = true;
        while (REPLAY.consumeClick()) replay = true;
        session.tick(record, replay);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (session != null) session.reset();
    }

    @SubscribeEvent
    public static void chat(ClientChatReceivedEvent event) {
        PracticeHud.noteChat();
    }

    @Mod.EventBusSubscriber(modid = EnglishMC.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(RECORD);
            event.register(REPLAY);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.ITEM_NAME.id(), "practice",
                (gui, graphics, partialTick, width, height) -> {
                    if (session != null) PracticeHud.render(session, gui, graphics, width, height);
                });
        }
    }
}
