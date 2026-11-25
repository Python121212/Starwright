package io.github.python121212.starwright.ship;

/**
 * The ONLY bridge between the pure ship engine and a real Minecraft level.
 *
 * <p>{@code long} values are packed relative coordinates produced by
 * {@link ShipStructureRules#pack(int, int, int)}. Keeping the interface in this
 * package means the engine itself stays free of {@code net.minecraft.*} and can
 * be loaded and ticked by the jar emulator without a pseudo level.</p>
 */
public interface ShipBlockSource {

    /** True when this relative position holds a non-air block. */
    boolean isSolid(long packed);

    /** True only for the core block (relative 0,0,0). */
    boolean isCore(long packed);
}
