package io.github.kkskkkcc.englishmc;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(EnglishMC.ID)
public final class EnglishMC {
    public static final String ID = "englishmc";
    public static final Logger LOG = LogUtils.getLogger();

    public EnglishMC() {
        WorldPractice.register();
    }
}
