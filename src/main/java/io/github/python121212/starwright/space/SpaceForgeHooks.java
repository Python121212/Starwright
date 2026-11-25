package io.github.python121212.starwright.space;

import com.mojang.logging.LogUtils;
import io.github.python121212.starwright.Starwright;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Server-side space logic. Registered by FML through
 * {@code @Mod.EventBusSubscriber}, so the mod's main class never has to
 * reference these Minecraft-dependent classes (which keeps the jar emulator
 * isolated and happy).
 *
 * <p>Nothing here creates a dimension or teleports the player. Space is a
 * continuous state of the same Overworld, derived from Y every tick.</p>
 */
@Mod.EventBusSubscriber(modid = Starwright.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpaceForgeHooks {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile SpaceStage lastLoggedStage = null;

    private SpaceForgeHooks() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide) {
            return; // "ただしサーバーはまた別です" - client rendering is separate.
        }

        double y = player.getY();
        SpaceState state = SpaceRules.stateAt(y, true);

        // エリトラでは宇宙へ行かせない。
        if (!state.elytraAllowed() && player.isFallFlying()) {
            player.stopFallFlying();
        }

        // 「宇宙は見た目ではない」: actually change a physical state.
        player.setNoGravity(state.isSpace());

        if (state.stage() != lastLoggedStage) {
            lastLoggedStage = state.stage();
            LOGGER.info("[Starwright/Space] stage={} y={} darkness={} stars={} gravity={}",
                    state.stage(), (long) y,
                    String.format("%.2f", state.darkness()),
                    String.format("%.2f", state.starVisibility()),
                    String.format("%.2f", state.gravityScale()));
        }
    }
}
