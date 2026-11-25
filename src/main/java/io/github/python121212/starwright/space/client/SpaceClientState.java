package io.github.python121212.starwright.space.client;

import io.github.python121212.starwright.Starwright;
import io.github.python121212.starwright.space.SpaceRules;
import io.github.python121212.starwright.space.SpaceState;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side snapshot of the space state. This is deliberately separate from
 * {@link io.github.python121212.starwright.space.SpaceForgeHooks}: the server
 * keeps ticking the ground normally while the client may stop DRAWING it.
 *
 * <p>Renderers read {@link #current()} - sky darkening, star count and the
 * ground-render cut-off are applied from here in the next implementation steps
 * (see docs/SPACE.md).</p>
 */
@Mod.EventBusSubscriber(
        modid = Starwright.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpaceClientState {

    private static volatile SpaceState current = SpaceRules.stateAt(0.0, false);

    private SpaceClientState() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        current = SpaceRules.stateAt(mc.player.getY(), false);
    }

    public static SpaceState current() {
        return current;
    }

    public static double darkness() {
        return current.darkness();
    }

    public static double starVisibility() {
        return current.starVisibility();
    }

    /** Client only. The server must NEVER call this to skip its own logic. */
    public static boolean skipGroundRendering() {
        return current.skipGroundRendering();
    }
}
