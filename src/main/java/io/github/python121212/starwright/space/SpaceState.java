package io.github.python121212.starwright.space;

/**
 * Immutable snapshot of "how much space" a given altitude is.
 *
 * @param y                     player altitude
 * @param stage                 atmospheric layer
 * @param darkness              0..1, how dark the sky should be
 * @param starVisibility        0..1, how many stars should be visible
 * @param gravityScale          1..0.05, physical gravity multiplier
 * @param elytraAllowed         false once elytra flight is cut off
 * @param skipGroundRendering  true when the client must not draw the ground
 * @param serverSide            true for server-side logic, false for the client
 * @param dimensionId           always the Overworld (seamless, no transfer)
 */
public record SpaceState(
        double y,
        SpaceStage stage,
        double darkness,
        double starVisibility,
        double gravityScale,
        boolean elytraAllowed,
        boolean skipGroundRendering,
        boolean serverSide,
        String dimensionId) {

    public boolean isSpace() {
        return stage == SpaceStage.SPACE;
    }
}
