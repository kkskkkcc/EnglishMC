package io.github.kkskkkcc.englishmc.mixin;

import io.github.kkskkkcc.englishmc.client.PracticeTab;
import java.util.Arrays;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(CreateWorldScreen.class)
abstract class CreateWorldScreenMixin {
    @ModifyArg(method = "init", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;addTabs([Lnet/minecraft/client/gui/components/tabs/Tab;)Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;"))
    private Tab[] englishmc$addPracticeTab(Tab[] original) {
        Tab[] tabs = Arrays.copyOf(original, original.length + 1);
        CreateWorldScreen screen = (CreateWorldScreen) (Object) this;
        tabs[original.length] = new PracticeTab(screen.getUiState());
        return tabs;
    }
}
