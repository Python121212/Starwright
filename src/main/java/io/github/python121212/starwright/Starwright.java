package io.github.python121212.starwright;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Starwright - a load-only Forge mod for Minecraft 1.20.1.
 *
 * <p>This is intentionally the smallest possible mod: FML discovers the {@code @Mod}
 * annotation from {@code META-INF/mods.toml}, instantiates this class and that is all.
 * No items, blocks, events or registries are added yet.</p>
 */
@Mod(Starwright.MOD_ID)
public class Starwright {

    public static final String MOD_ID = "starwright";

    private static final Logger LOGGER = LogUtils.getLogger();

    public Starwright() {
        LOGGER.info("[Starwright] Loaded successfully (Minecraft 1.20.1 / Forge 47.x).");
    }
}
