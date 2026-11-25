package io.github.python121212.starwright.space;

import com.mojang.logging.LogUtils;
import io.github.python121212.starwright.Starwright;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
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
 * <p>Nothing here creates a dimension or moves the player by setting a
 * position. Space is a continuous state of the same Overworld, derived from Y
 * every tick. Altitude is gained by applying a bounded vertical velocity with
 * {@code setDeltaMovement} - never by a teleport.</p>
 *
 * <p>The hook runs at {@link TickEvent.Phase#START}, i.e. BEFORE
 * {@code Player#travel()}. That matters for creative flight: vanilla recomputes
 * its vertical velocity from the jump/sneak inputs inside {@code travel()}, so
 * writing the velocity afterwards would be overwritten. Writing it beforehand
 * makes the same continuous ascent work while the creative player is flying.</p>
 */
@Mod.EventBusSubscriber(modid = Starwright.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpaceForgeHooks {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile SpaceStage lastLoggedStage = null;

    private SpaceForgeHooks() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide) {
            return; // "ただしサーバーはまた別です" - client rendering is separate.
        }

        double y = player.getY();
        SpaceState state = SpaceRules.stateAt(y, true);
        boolean creativeFlying = player.getAbilities().flying;

        // エリトラでは宇宙へ行かせない。
        if (!state.elytraAllowed() && player.isFallFlying()) {
            player.stopFallFlying();
        }

        // 「宇宙は見た目ではない」: actually change a physical state.
        player.setNoGravity(state.isSpace());

        // Seamless, continuous ascent/descent. No teleport, no dimension load.
        applyContinuousAscent(player, y, creativeFlying);

        if (state.stage() != lastLoggedStage) {
            lastLoggedStage = state.stage();
            LOGGER.info("[Starwright/Space] stage={} y={} darkness={} stars={} gravity={}",
                    state.stage(), (long) y,
                    String.format("%.2f", state.darkness()),
                    String.format("%.2f", state.starVisibility()),
                    String.format("%.2f", state.gravityScale()));
        }
    }

    /**
     * Below the vanilla build limit this does ABSOLUTELY NOTHING: no velocity
     * write, no fall-distance reset, no input override. Creative flight below
     * Y=320 therefore stays exactly vanilla.
     */
    private static void applyContinuousAscent(Player player, double y, boolean creativeFlying) {
        if (!SpaceAscentRules.isArmed(y)) {
            return;
        }
        // Elytra keeps its own physics; it is stopped outright at the cutoff.
        if (player.isFallFlying()) {
            return;
        }

        Vec3 motion = player.getDeltaMovement();
        double target = SpaceAscentRules.targetSpeed(
                y, player.isShiftKeyDown(), player.isFallFlying(), creativeFlying);
        double vertical = SpaceAscentRules.stepTowards(motion.y, target);

        // Velocity only. Vanilla's travel()/move() performs the actual move.
        player.setDeltaMovement(motion.x, vertical, motion.z);
        player.fallDistance = 0.0f;
    }
}
