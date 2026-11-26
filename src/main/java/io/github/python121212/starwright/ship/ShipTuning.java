package io.github.python121212.starwright.ship;

/**
 * Knobs for Starwright's own ship engine. Pure Java: no Minecraft, no Forge.
 *
 * <p>The engine deliberately has no physics solver, no rotation, no collision
 * mesh and no virtual level. A ship is a set of real in-level blocks owned by
 * ONE entity, assembled by the Physics Assembler and moved one block per click
 * by the control blocks. That is the whole reason it is lighter than
 * Create Aeronautics, which needs a per-tick simulated physics world.</p>
 */
public final class ShipTuning {

    /** A ship must contain the assembler plus at least one other block. */
    public static final int MIN_BLOCKS = 2;

    /** Hard cap on the connected structure, so a whole base can never become a ship. */
    public static final int MAX_BLOCKS = 4096;

    /** Flood fill is bounded in each axis relative to the assembler. */
    public static final int MAX_RADIUS = 32;

    /** One click = one block of travel. Never a per-tick simulation. */
    public static final int STEP_BLOCKS_PER_CLICK = 1;

    /** Packed-coordinate bias; keeps every field positive and borrow-free. */
    public static final int COORD_BIAS = 1024;
    public static final int COORD_MASK = 2047;

    // Create-Aeronautics-inspired, but entirely Starwright's own objects.
    public static final String ASSEMBLER_ID = "starwright:physics_assembler";
    public static final String PROPELLER_BEARING_ID = "starwright:propeller_bearing";
    public static final String HOT_AIR_BURNER_ID = "starwright:hot_air_burner";
    public static final String STEAM_VENT_ID = "starwright:steam_vent";
    public static final String LEVITITE_ID = "starwright:levitite";
    public static final String GOGGLES_ID = "starwright:aviators_goggles";
    public static final String SHIP_ENTITY_ID = "starwright:ship";

    private ShipTuning() {}
}
