package io.github.python121212.starwright.ship;

/**
 * Decides what a wrench click means - and, just as importantly, what it does
 * NOT mean. Everything that is not an explicit wrench action falls through to
 * vanilla, which is why placing blocks, walking and opening chests keep working
 * after the structure becomes a ship.
 */
public final class ShipInteractionRules {

    private ShipInteractionRules() {}

    /** Shift + right click with the wrench removes an accidentally attached block. */
    public static boolean isWrenchDetach(boolean shiftHeld, boolean holdingWrench,
                                         boolean targetInShip, boolean targetIsCore) {
        return shiftHeld && holdingWrench && targetInShip && !targetIsCore;
    }

    /** Plain right click with the wrench nudges the ship one block. */
    public static boolean isWrenchAdvance(boolean shiftHeld, boolean holdingWrench,
                                          boolean targetInShip) {
        return !shiftHeld && holdingWrench && targetInShip;
    }

    /**
     * True when the click must be handed straight back to Minecraft. Chests,
     * doors, furnaces, block placement and walking all take this path - the
     * ship engine never reimplements a single interaction.
     */
    public static boolean isVanillaPassthrough(boolean holdingWrench, boolean targetInShip) {
        return !holdingWrench || !targetInShip;
    }

    public static boolean coreIsProtected(boolean targetIsCore) {
        return targetIsCore;
    }
}
