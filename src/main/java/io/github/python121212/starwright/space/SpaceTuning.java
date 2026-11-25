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
     * Space is NOT a dimension: every altitude stays in the same Overworld.
     * No ServerLevel creation, no dimension transfer, no loading screen.
     */
    public static final String OVERWORLD = "minecraft:overworld";

    private SpaceTuning() {}
}
