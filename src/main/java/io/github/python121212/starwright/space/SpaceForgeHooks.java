package io.github.python121212.starwright.space;

import com.mojang.logging.LogUtils;
import io.github.python121212.starwright.Starwright;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 * writing the velocity afterwards would be overwritten.</p>
 *
 * <p>Survival / Adventure support: those game modes have {@code mayfly = false},
 * so vanilla's anti-flying check would kick the player after hovering above the
 * build limit. While the ascent is armed we temporarily grant flight ability and
 * remember the original values; returning below Y=320 restores them exactly.
 * No game mode is ever changed.</p>
 */
@Mod.EventBusSubscriber(modid = Starwright.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpaceForgeHooks {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile SpaceStage lastLoggedStage = null;

    /** Original vanilla flight abilities, keyed by player, restored below Y=320. */
    private static final Map<UUID, OriginalFlight> ORIGINAL_FLIGHT = new ConcurrentHashMap<>();

    private record OriginalFlight(boolean mayfly, boolean flying) {}

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
        Abilities abilities = player.getAbilities();
        UUID id = player.getUUID();
        OriginalFlight original = ORIGINAL_FLIGHT.get(id);

        // Always reason about the ORIGINAL abilities, never the temporary grant.
        // A boosted Survival player must still be treated as non-creative.
        boolean originallyAllowed = original != null ? original.mayfly() : abilities.mayfly;
        boolean creativeFlying = original != null ? original.flying() : abilities.flying;

        applySurvivalFlightPermission(player, abilities, id, y, originallyAllowed, original);

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
     * Temporarily allows flight for Survival / Adventure above the build limit,
     * then restores the exact original abilities on the way back down. Creative
     * and Spectator already have flight and are left completely untouched.
     */
    private static void applySurvivalFlightPermission(Player player, Abilities abilities,
                                                      UUID id, double y,
                                                      boolean originallyAllowed,
                                                      OriginalFlight original) {
        if (SpaceAscentRules.needsTemporaryFlightPermission(y, originallyAllowed)) {
            if (original == null) {
                ORIGINAL_FLIGHT.put(id, new OriginalFlight(abilities.mayfly, abilities.flying));
                abilities.mayfly = true;
                abilities.flying = true;
                player.onUpdateAbilities();
            }
        } else if (original != null) {
            ORIGINAL_FLIGHT.remove(id);
            abilities.mayfly = original.mayfly();
            abilities.flying = original.flying();
            player.onUpdateAbilities();
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
