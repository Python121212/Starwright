package io.github.python121212.starwright.space;

/**
 * Pure, Minecraft-independent rules for the seamless space layer.
 *
 * <p>This class deliberately has no imports outside {@code java.lang}: it can be
 * unit-tested directly and is also safe for the jar emulator. It never creates a
 * dimension, never teleports anything, and never reads a {@code Level} - space is
 * derived purely from the player's Y coordinate in the existing Overworld.</p>
 */
public final class SpaceRules {

    private SpaceRules() {}

    public static SpaceStage stageForY(double y) {
        if (y >= SpaceTuning.SPACE_START_Y) return SpaceStage.SPACE;
        if (y >= SpaceTuning.THERMOSPHERE_END_Y) return SpaceStage.EXOSPHERE;
        if (y >= SpaceTuning.MESOSPHERE_END_Y) return SpaceStage.THERMOSPHERE;
        if (y >= SpaceTuning.STRATOSPHERE_END_Y) return SpaceStage.MESOSPHERE;
        if (y >= SpaceTuning.TROPOSPHERE_END_Y) return SpaceStage.STRATOSPHERE;
        if (y > 0) return SpaceStage.TROPOSPHERE;
        return SpaceStage.GROUND;
    }

    /** 0..1 progress towards space. */
    public static double progress(double y) {
        return clamp01(y / SpaceTuning.SPACE_START_Y);
    }

    /** The sky darkens early (sqrt curve), then keeps deepening. */
    public static double darkness(double y) {
        return Math.sqrt(progress(y));
    }

    /** Stars appear later than the darkness (power curve). */
    public static double starVisibility(double y) {
        return Math.pow(progress(y), 1.8);
    }

    /** Gravity weakens towards 0.05 but never becomes exactly zero. */
    public static double gravityScale(double y) {
        return 1.0 - 0.95 * progress(y);
    }

    public static boolean isSpace(double y) {
        return y >= SpaceTuning.SPACE_START_Y;
    }

    /** "エリトラでは行けない": elytra must not be the way into space. */
    public static boolean isElytraAllowed(double y) {
        return y < SpaceTuning.ELYTRA_CUTOFF_Y;
    }

    /**
     * "宇宙にいる、または地上が見えなくなったら" - client-side ground rendering
     * must be skipped. The server is handled separately and NEVER uses this.
     */
    public static boolean shouldSkipGroundRendering(double y) {
        return isSpace(y) || y >= SpaceTuning.GROUND_RENDER_CUTOFF_Y;
    }

    public static SpaceState stateAt(double y, boolean serverSide) {
        return new SpaceState(
                y,
                stageForY(y),
                darkness(y),
                starVisibility(y),
                gravityScale(y),
                isElytraAllowed(y),
                shouldSkipGroundRendering(y),
                serverSide,
                SpaceTuning.OVERWORLD);
    }

    private static double clamp01(double value) {
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }
}
