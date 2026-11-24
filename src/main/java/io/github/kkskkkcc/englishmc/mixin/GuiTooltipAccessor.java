package io.github.kkskkkcc.englishmc.mixin;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiTooltipAccessor {
    @Accessor("toolHighlightTimer")
    int englishmc$itemNameTicks();
}
