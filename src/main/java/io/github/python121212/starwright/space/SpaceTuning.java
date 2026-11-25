package io.github.python121212.starwright.space;

/**
 * Tunable constants for the seamless space layer.
 *
 * <p>Each {@code *_END_Y} is the altitude at which the NEXT atmospheric layer
 * begins. Keeping them here (instead of scattering magic numbers) makes it
 * possible to change the scale later without touching the rules.</p>
 */
public final class SpaceTuning {

    /** Altitude at which SPACE begins, exactly as specified. */
    public static final int SPACE_START_Y = 5_000;

    public static final int TROPOSPHERE_END_Y = 800;
    public static final int STRATOSPHERE_END_Y = 2_000;
    public static final int MESOSPHERE_END_Y = 3_200;
    public static final int THERMOSPHERE_END_Y = 4_200;
    public static final int EXOSPHERE_END_Y = SPACE_START_Y;

    /** Elytra flight is stopped at/above this altitude. */
    public static final int ELYTRA_CUTOFF_Y = 1_000;

    /** Above this altitude the ground is no longer visible from the client. */
    public static final int GROUND_RENDER_CUTOFF_Y = 4_200;

    /**
     * DECIDED: the vanilla build limit stays untouched. We ship NO
     * {@code data/minecraft/dimension_type/overworld.json} and no custom dimension.
     * {@link #SPACE_START_Y} is therefore ABOVE the build limit - an entity
     * position in a chunk-less region, not a place where blocks can be placed.
     */
    public static final int VANILLA_MIN_BUILD_Y = -64;
    public static final int VANILLA_MAX_BUILD_Y = 319;

    /**
     * Continuous ascent/descent. It becomes available exactly ONE block above
     * the vanilla build limit (the feet of a player standing on Y=319), so the
     * ground game below that is completely untouched.
     */
    public static final int ASCENT_ARM_Y = VANILLA_MAX_BUILD_Y + 1; // 320

    /** Vertical speed ceiling in metres per tick (1.0 m/tick = 20 m/s). */
    public static final double ASCENT_MAX_SPEED = 1.0;

    /** Largest velocity change allowed in one tick: no instantaneous jump. */
    public static final double ASCENT_ACCELERATION = 0.04;

    /**
     * Space is NOT a dimension: every altitude stays in the same Overworld.
     * No ServerLevel creation, no dimension transfer, no loading screen.
     */
    public static final String OVERWORLD = "minecraft:overworld";

    private SpaceTuning() {}
}
