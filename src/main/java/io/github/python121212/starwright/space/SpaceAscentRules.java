package io.github.python121212.starwright.space;

/**
 * Pure, Minecraft-independent rules for the CONTINUOUS ascent and descent that
 * carries a player from the vanilla build limit (Y=320) up to space (Y=5,000).
 *
 * <p>This class deliberately has no imports outside {@code java.lang}. It is
 * safe to load from the production (re-obfuscated) jar inside the pseudo
 * Minecraft emulator, because it never references a Minecraft class whose name
 * would be remapped by {@code reobfJar}.</p>
 *
 * <p>CONTINUITY GUARANTEE: this class only ever returns a <em>vertical
 * velocity</em>. It never returns a coordinate, never sets a position and never
 * asks for a dimension. The hook applies that velocity with
 * {@code Entity#setDeltaMovement} on every tick. Because the returned speed is
 * clamped to {@link SpaceTuning#ASCENT_MAX_SPEED}, the player's Y can change by
 * at most one metre per tick - there is no teleport, no {@code setPos}, no
 * {@code changeDimension} and no loading screen.</p>
 *
 * <p>No item, block, key binding, network packet or access transformer is added.
 * Once the player crosses the vanilla build limit the same Overworld simply
 * carries them upward; holding vanilla sneak descends. Creative flight keeps
 * working at every altitude (including inside space), so a creative player can
 * simply fly up through the sky.</p>
 */
public final class SpaceAscentRules {

    private SpaceAscentRules() {}

    /**
     * The continuous ascent is available only at/above the first Y outside the
     * vanilla build limit. Below that, vanilla behaviour is left completely
     * untouched - including ordinary creative flight.
     */
    public static boolean isArmed(double y) {
        return y >= SpaceTuning.ASCENT_ARM_Y;
    }

    /**
     * Desired vertical velocity in metres per tick.
     *
     * @param sneak          vanilla sneak input held (server-synced by the game)
     * @param fallFlying     true while elytra flight is active; never boosted
     * @param creativeFlying true while the player is in creative flight
     */
    public static double targetSpeed(double y, boolean sneak, boolean fallFlying,
                                     boolean creativeFlying) {
        if (!isArmed(y) || fallFlying) {
            return 0.0;
        }
        // Descending stays available even from inside space.
        if (sneak) {
            return -SpaceTuning.ASCENT_MAX_SPEED;
        }
        // Non-creative arrival settles into zero gravity. Creative flight keeps
        // flying: the player may keep climbing freely from inside space.
        if (isSpace(y) && !creativeFlying) {
            return 0.0;
        }
        return SpaceTuning.ASCENT_MAX_SPEED;
    }

    /**
     * Survival and Adventure players have {@code mayfly = false}, so vanilla's
     * anti-flying check would eventually disconnect them while they float above
     * the build limit. The server hook temporarily grants flight permission
     * while the continuous ascent is armed, and restores the original value as
     * soon as the player is back below Y=320.
     *
     * @param flightAllowed the player's ORIGINAL {@code mayfly} value
     */
    public static boolean needsTemporaryFlightPermission(double y, boolean flightAllowed) {
        return isArmed(y) && !flightAllowed;
    }

    /**
     * Moves {@code current} towards {@code target} by at most
     * {@link SpaceTuning#ASCENT_ACCELERATION} per tick and clamps the result.
     * Starting from a velocity inside the clamp range this makes both the
     * acceleration and the per-tick displacement bounded and smooth.
     */
    public static double stepTowards(double current, double target) {
        double goal = clampSpeed(target);
        double difference = goal - current;
        double acceleration = SpaceTuning.ASCENT_ACCELERATION;
        if (difference > acceleration) {
            return clampSpeed(current + acceleration);
        }
        if (difference < -acceleration) {
            return clampSpeed(current - acceleration);
        }
        return goal;
    }

    /** A velocity, never a position. NaN is treated as "no input". */
    public static double clampSpeed(double speed) {
        double limit = SpaceTuning.ASCENT_MAX_SPEED;
        if (Double.isNaN(speed)) return 0.0;
        if (speed > limit) return limit;
        if (speed < -limit) return -limit;
        return speed;
    }

    private static boolean isSpace(double y) {
        return y >= SpaceTuning.SPACE_START_Y;
    }
}
