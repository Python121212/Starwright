package io.github.python121212.starwright.space;

/**
 * Pure, Minecraft-independent rules for the CLIENT-SIDE sky rendering.
 *
 * <p>No imports outside {@code java.lang}. This keeps the production
 * (re-obfuscated) jar loadable by the pseudo Minecraft emulator, and keeps the
 * renderer itself free of any computation besides reading these values.</p>
 *
 * <p>LIGHTWEIGHT BY DESIGN: the star geometry is generated ONCE at class load
 * (240 fixed-seed quads). Every frame the renderer simply draws the first
 * {@link #visibleStars(double)} quads - no geometry rebuild, no entity, no
 * texture, no resource pack. {@link #skyColor(double)} blends two colours and
 * {@link #fogFactor(double)} darkens the fog, both with plain integer maths.</p>
 */
public final class SpaceRenderRules {

    /** Fixed-seed star quads; increasing visibility reveals more of them. */
    public static final int STAR_COUNT = 240;

    private static final int DAY_SKY = 0x659be2;
    private static final int SPACE_SKY = 0x000000;

    private SpaceRenderRules() {}

    /** How many of the pre-built stars should be drawn at this visibility. */
    public static int visibleStars(double starVisibility) {
        return (int) Math.floor(STAR_COUNT * clamp01(starVisibility));
    }

    /** Sky colour for a given darkness (0 = vanilla blue, 1 = black space). */
    public static int skyColor(double darkness) {
        double t = clamp01(darkness);
        return (channel(16, t) << 16) | (channel(8, t) << 8) | channel(0, t);
    }

    /** Fog brightness multiplier; reaches 0 in space. */
    public static double fogFactor(double darkness) {
        double air = 1.0 - clamp01(darkness);
        return air * air;
    }

    /**
     * The ground-render cut-off. The LevelRenderer mixin consults this through
     * {@code SpaceClientState}; the server NEVER calls it.
     */
    public static boolean skipGround(double y) {
        return SpaceRules.shouldSkipGroundRendering(y);
    }

    private static int channel(int shift, double t) {
        int from = (DAY_SKY >> shift) & 255;
        int to = (SPACE_SKY >> shift) & 255;
        return (int) (from + (to - from) * t);
    }

    private static double clamp01(double value) {
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        if (Double.isNaN(value)) return 0.0;
        return value;
    }
}
