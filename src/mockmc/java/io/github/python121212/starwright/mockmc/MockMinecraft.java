package io.github.python121212.starwright.mockmc;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The "pseudo Minecraft" runtime: a tiny fake game environment that records what
 * happened while Forge discovered and loaded the mod.
 *
 * <p>Everything under {@code net.minecraft.*}, {@code com.mojang.*} and
 * {@code org.slf4j.*} in this source set is a fake. Forge itself is NOT faked:
 * all {@code net.minecraftforge.*} classes (the {@code @Mod} annotation, the mod
 * event bus, registries, ...) are loaded from the real Forge jar on the test
 * classpath by {@code EmulatedForgeLoader}.</p>
 */
public final class MockMinecraft {

    private static boolean bootstrapped;
    private static final List<String> EVENTS = new ArrayList<>();
    private static final List<String> LOADED_MODS = new ArrayList<>();

    private MockMinecraft() {}

    /** Emulates the game booting up. */
    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        EVENTS.add("bootstrap:" + SharedConstants.getCurrentVersion());
        System.out.println("[MockMC] Booting pseudo Minecraft " + SharedConstants.getCurrentVersion());
    }

    public static boolean isBootstrapped() {
        return bootstrapped;
    }

    public static void recordLog(String message) {
        EVENTS.add("log:" + message);
    }

    /** Records a call made against the injected Forge mod event bus. */
    public static void recordEventBusCall(String methodName) {
        EVENTS.add("eventbus:" + methodName);
    }

    /**
     * Records ONE emulated server tick of the continuous ascent. The pseudo game
     * advances the player by the velocity returned from the production jar's
     * SpaceAscentRules, so the whole climb to Y=5,000 is really simulated here.
     */
    public static void recordAscent(double y, double velocity) {
        EVENTS.add(String.format("ascent:y=%.2f,vy=%.2f", y, velocity));
    }

    /**
     * Records one emulated ship-engine decision made by the production jar's
     * pure rules (flood fill, floating check, wrench action).
     */
    public static void recordShip(String decision) {
        EVENTS.add("ship:" + decision);
    }

    /** Called by the emulated FML after it instantiates a mod. */
    public static void recordMod(String modId) {
        if (!ResourceLocation.isValidNamespace(modId)) {
            throw new IllegalArgumentException("invalid mod id: " + modId);
        }
        LOADED_MODS.add(modId);
        EVENTS.add("mod:" + modId);
        System.out.println("[MockMC] FML registered mod " + modId);
    }

    public static List<String> events() {
        return Collections.unmodifiableList(EVENTS);
    }

    public static List<String> loadedMods() {
        return Collections.unmodifiableList(LOADED_MODS);
    }
}
