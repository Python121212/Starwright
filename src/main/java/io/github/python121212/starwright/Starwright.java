package io.github.python121212.starwright;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Starwright - Forge mod for Minecraft 1.20.1.
 *
 * <p><b>Forge 1.20.1 contract:</b> {@code FMLModContainer#constructMod()} first
 * looks for a constructor taking {@code FMLJavaModLoadingContext}, then falls
 * back to {@code getDeclaredConstructor()} - a NO-ARG constructor. An
 * {@code IEventBus} parameter is NOT accepted and fails on a real client with
 * {@code NoSuchMethodException: Starwright.<init>()}.</p>
 *
 * <p>All blocks, items, entities and event subscribers register themselves via
 * {@code @Mod.EventBusSubscriber} + {@code RegisterEvent}, so this entry point
 * needs no constructor arguments at all. That also keeps it loadable in
 * complete isolation by the real-Forge / pseudo-Minecraft jar emulator.</p>
 */
@Mod(Starwright.MOD_ID)
public class Starwright {

    public static final String MOD_ID = "starwright";

    private static final Logger LOGGER = LogUtils.getLogger();

    public Starwright() {
        LOGGER.info("[Starwright] Loaded successfully (Minecraft 1.20.1 / Forge 47.x).");
    }
}
