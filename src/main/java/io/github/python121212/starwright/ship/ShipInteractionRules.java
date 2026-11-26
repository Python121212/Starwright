package io.github.python121212.starwright.ship;

/**
 * Decides what a click on a ship block means - and, just as importantly, what
 * it does NOT mean. There is no wrench any more: the Physics Assembler and the
 * three control blocks handle everything. Everything else falls through to
 * vanilla, which is why placing blocks, walking and opening chests keep working.
 */
public final class ShipInteractionRules {

    private ShipInteractionRules() {}

    /** Shift + right click on the Physics Assembler removes an extra block. */
    public static boolean isAssemblerDetach(boolean shiftHeld,
                                            boolean targetInShip,
                                            boolean targetIsCore) {
        return shiftHeld && targetInShip && !targetIsCore;
    }

    /** Plain right click on the Physics Assembler nudges the ship forward. */
    public static boolean isAssemblerAdvance(boolean shiftHeld, boolean targetInShip) {
        return !shiftHeld && targetInShip;
    }

    /** A control block always advances on its own axis when clicked. */
    public static boolean isControlAdvance(boolean targetInShip) {
        return targetInShip;
    }

    /** True when the click must be handed straight back to Minecraft. */
    public static boolean isVanillaPassthrough(boolean targetInShip) {
        return !targetInShip;
    }

    public static boolean coreIsProtected(boolean targetIsCore) {
        return targetIsCore;
    }
}
