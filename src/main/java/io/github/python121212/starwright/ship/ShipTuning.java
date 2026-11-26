package io.github.python121212.starwright.ship;

/**
 * Knobs for Starwright's own ship engine. Pure Java: no Minecraft, no Forge.
 *
 * <p>The engine deliberately has no physics solver, no rotation, no collision
 * mesh and no virtual level. A ship is a set of real in-level blocks owned by
 * ONE entity, and a wrench click moves it by a single block. That is the whole
 * reason it is lighter than Create Aeronautics.</p>
 */
public final class ShipTuning {

    /** A ship must contain the core plus at least one other block. */
    public static final int MIN_BLOCKS = 2;

    /** Hard cap on the connected structure, so a whole base can never become a ship. */
    public static final int MAX_BLOCKS = 4096;

    /** Flood fill is bounded in each axis relative to the core block. */
    public static final int MAX_RADIUS = 32;

    /** One wrench click = one block of travel. Never a per-tick simulation. */
    public static final int STEP_BLOCKS_PER_CLICK = 1;

    /** Packed-coordinate bias; keeps every field positive and borrow-free. */
    public static final int COORD_BIAS = 1024;
    public static final int COORD_MASK = 2047;

    public static final String CORE_BLOCK_ID = "starwright:core_block";
    public static final String WRENCH_ID = "starwright:wrench";
    public static final String SHIP_ENTITY_ID = "starwright:ship";

    private ShipTuning() {}
}
