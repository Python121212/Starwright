package io.github.python121212.starwright.ship;

/**
 * The entire "movement engine": one axis, one block, one click.
 *
 * <p>No velocity, no acceleration, no angular momentum, no collision solver,
 * no per-tick integration. Create Aeronautics runs a physics world every tick;
 * Starwright just relocates real blocks once per control click.</p>
 */
public final class ShipPhysics {

    private ShipPhysics() {}

    /** Collapses any direction to a single +/-1 step on one axis. */
    public static int[] clampStep(int dx, int dy, int dz) {
        int step = ShipTuning.STEP_BLOCKS_PER_CLICK;
        if (dx != 0) {
            return new int[] {Integer.signum(dx) * step, 0, 0};
        }
        if (dy != 0) {
            return new int[] {0, Integer.signum(dy) * step, 0};
        }
        if (dz != 0) {
            return new int[] {0, 0, Integer.signum(dz) * step};
        }
        return new int[] {0, 0, 0};
    }

    public static boolean isIdle(int[] step) {
        return step.length != 3 || (step[0] == 0 && step[1] == 0 && step[2] == 0);
    }

    public static long shifted(long packed, int[] step) {
        return ShipStructureRules.pack(
                ShipStructureRules.px(packed) + step[0],
                ShipStructureRules.py(packed) + step[1],
                ShipStructureRules.pz(packed) + step[2]);
    }

    public static boolean withinLimits(int blockCount) {
        return blockCount >= ShipTuning.MIN_BLOCKS && blockCount <= ShipTuning.MAX_BLOCKS;
    }
}
