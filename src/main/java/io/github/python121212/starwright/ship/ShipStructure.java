package io.github.python121212.starwright.ship;

/**
 * Immutable snapshot of a ship: packed relative blocks plus the absolute
 * origin. The {@code ShipEntity} owns one of these; the blocks themselves stay
 * in the real {@code Level}, so no fake level and no mesh is ever needed.
 */
public final class ShipStructure {

    private final long[] blocks;
    private final long core;
    private final int originX;
    private final int originY;
    private final int originZ;

    public ShipStructure(long[] blocks, int originX, int originY, int originZ) {
        this.blocks = blocks.clone();
        this.core = ShipStructureRules.pack(0, 0, 0);
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    public long[] blocks() {
        return blocks.clone();
    }

    public int size() {
        return blocks.length;
    }

    public long core() {
        return core;
    }

    public int originX() { return originX; }
    public int originY() { return originY; }
    public int originZ() { return originZ; }

    public long relativeOf(int absoluteX, int absoluteY, int absoluteZ) {
        return ShipStructureRules.pack(
                absoluteX - originX, absoluteY - originY, absoluteZ - originZ);
    }

    public boolean contains(int absoluteX, int absoluteY, int absoluteZ) {
        return ShipStructureRules.contains(blocks,
                relativeOf(absoluteX, absoluteY, absoluteZ));
    }

    public ShipStructure without(long relative) {
        return new ShipStructure(ShipStructureRules.without(blocks, relative),
                originX, originY, originZ);
    }

    public ShipStructure shifted(int[] step) {
        return new ShipStructure(blocks,
                originX + step[0], originY + step[1], originZ + step[2]);
    }

    public int visibleFaces() {
        return ShipStructureRules.visibleFaces(blocks);
    }
}
