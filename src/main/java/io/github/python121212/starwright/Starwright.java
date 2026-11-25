package io.github.python121212.starwright;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Starwright - Forge mod for Minecraft 1.20.1.
 *
 * <p>FML injects the mod event bus into the constructor. The space layer lives
 * in the {@code space} package and is auto-registered by
 * {@code @Mod.EventBusSubscriber}; this class stays intentionally thin so the
 * jar emulator can load it in complete isolation.</p>
 */
@Mod(Starwright.MOD_ID)
public class Starwright {

    public static final String MOD_ID = "starwright";

    private static final Logger LOGGER = LogUtils.getLogger();

    public Starwright(IEventBus modEventBus) {
        // modEventBus is where DeferredRegister instances will be hooked up next.
        LOGGER.info("[Starwright] Loaded successfully (Minecraft 1.20.1 / Forge 47.x).");
    }
}
