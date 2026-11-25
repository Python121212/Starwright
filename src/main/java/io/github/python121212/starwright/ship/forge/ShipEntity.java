package io.github.python121212.starwright.ship.forge;

import io.github.python121212.starwright.ship.ShipStructure;
import io.github.python121212.starwright.ship.ShipStructureRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * ONE entity owns the whole ship. It is deliberately almost empty: no model,
 * no velocity, no physics, no per-tick work. The blocks stay in the real
 * {@link Level}, so vanilla deals with walking, placing, containers, redstone
 * and rendering. This entity only remembers which blocks belong to the ship.
 */
public final class ShipEntity extends Entity {

    private ShipStructure structure = new ShipStructure(new long[0], 0, 0, 0);

    public ShipEntity(EntityType<? extends ShipEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        // No synched data: the real blocks are already replicated by the level.
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setStructure(tag.getLongArray("Blocks"),
                tag.getInt("OriginX"), tag.getInt("OriginY"), tag.getInt("OriginZ"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLongArray("Blocks", structure.blocks());
        tag.putInt("OriginX", structure.originX());
        tag.putInt("OriginY", structure.originY());
        tag.putInt("OriginZ", structure.originZ());
    }

    public void setStructure(long[] blocks, int originX, int originY, int originZ) {
        this.structure = new ShipStructure(blocks, originX, originY, originZ);
        refreshBounds();
        setPos(originX + 0.5D, originY + 0.5D, originZ + 0.5D);
    }

    public ShipStructure structure() {
        return structure;
    }

    public boolean contains(int x, int y, int z) {
        return structure.contains(x, y, z);
    }

    public boolean isCore(int x, int y, int z) {
        return structure.relativeOf(x, y, z) == structure.core();
    }

    public void detach(long relative) {
        this.structure = structure.without(relative);
        refreshBounds();
    }

    public void shift(int[] step) {
        this.structure = structure.shifted(step);
        refreshBounds();
        setPos(structure.originX() + 0.5D,
                structure.originY() + 0.5D,
                structure.originZ() + 0.5D);
    }

    private void refreshBounds() {
        long[] blocks = structure.blocks();
        if (blocks.length == 0) {
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long block : blocks) {
            int x = ShipStructureRules.px(block);
            int y = ShipStructureRules.py(block);
            int z = ShipStructureRules.pz(block);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        setBoundingBox(new AABB(
                structure.originX() + minX,
                structure.originY() + minY,
                structure.originZ() + minZ,
                structure.originX() + maxX + 1.0D,
                structure.originY() + maxY + 1.0D,
                structure.originZ() + maxZ + 1.0D));
    }
}
