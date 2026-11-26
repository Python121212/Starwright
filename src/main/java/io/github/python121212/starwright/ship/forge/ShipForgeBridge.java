package io.github.python121212.starwright.ship.forge;

import io.github.python121212.starwright.ship.ShipBlockSource;
import io.github.python121212.starwright.ship.ShipInteractionRules;
import io.github.python121212.starwright.ship.ShipPhysics;
import io.github.python121212.starwright.ship.ShipStructureRules;
import io.github.python121212.starwright.ship.ShipTuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The thin bridge from the pure ship engine to a real Forge level. All
 * Minecraft-dependent work lives here, so the engine stays emulator-safe.
 */
public final class ShipForgeBridge {

    private ShipForgeBridge() {}

    /** Called when the Physics Assembler is placed. Only a genuinely floating hull assembles. */
    public static void tryAssemble(Level level, BlockPos corePos) {
        if (level.isClientSide) {
            return;
        }
        RelativeBlockSource source = new RelativeBlockSource(level, corePos);
        long[] blocks = ShipStructureRules.collect(0, 0, 0, source);
        if (!ShipPhysics.withinLimits(blocks.length)
                || !ShipStructureRules.isFloating(blocks, source)) {
            return;
        }
        ShipEntity ship = new ShipEntity(ShipRegistry.SHIP_ENTITY, level);
        ship.setStructure(blocks, corePos.getX(), corePos.getY(), corePos.getZ());
        level.addFreshEntity(ship);
    }

    /** Shift + wrench: remove one accidentally attached block and drop it. */
    public static boolean detach(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return false;
        }
        ShipEntity ship = findShip(level, pos);
        if (ship == null) {
            return false;
        }
        long relative = ship.structure().relativeOf(pos.getX(), pos.getY(), pos.getZ());
        boolean core = ship.isCore(pos.getX(), pos.getY(), pos.getZ());
        ShipBlockSource source = new RelativeBlockSource(level, new BlockPos(
                ship.structure().originX(),
                ship.structure().originY(),
                ship.structure().originZ()));
        if (!ShipStructureRules.canDetach(ship.structure().blocks(), relative, source)
                || !ShipInteractionRules.isAssemblerDetach(true, true, core)) {
            return false;
        }
        ship.detach(relative);
        level.destroyBlock(pos, true);
        return true;
    }

    /** Plain wrench click: one block of travel, with containers carried along. */
    public static boolean advance(Level level, BlockPos pos, Direction direction) {
        if (level.isClientSide) {
            return false;
        }
        ShipEntity ship = findShip(level, pos);
        if (ship == null) {
            return false;
        }
        int[] step = ShipPhysics.clampStep(
                direction.getStepX(), direction.getStepY(), direction.getStepZ());
        if (ShipPhysics.isIdle(step)) {
            return false;
        }

        long[] blocks = ship.structure().blocks();
        int ox = ship.structure().originX();
        int oy = ship.structure().originY();
        int oz = ship.structure().originZ();

        // Destination cells belonging to the ship itself are fine; any other
        // occupied cell blocks the move. No collision solver is needed.
        Set<Long> destinations = new HashSet<>();
        for (long block : blocks) {
            destinations.add(ShipStructureRules.pack(
                    ShipStructureRules.px(block) + step[0],
                    ShipStructureRules.py(block) + step[1],
                    ShipStructureRules.pz(block) + step[2]));
        }
        for (long block : blocks) {
            long moved = ShipStructureRules.pack(
                    ShipStructureRules.px(block) + step[0],
                    ShipStructureRules.py(block) + step[1],
                    ShipStructureRules.pz(block) + step[2]);
            if (!destinations.contains(block)
                    && !level.getBlockState(new BlockPos(
                            ox + ShipStructureRules.px(moved),
                            oy + ShipStructureRules.py(moved),
                            oz + ShipStructureRules.pz(moved))).isAir()) {
                return false;
            }
        }

        // Snapshot states and container NBT once, then relocate in one batch.
        Map<Long, BlockState> states = new HashMap<>();
        Map<Long, CompoundTag> containers = new HashMap<>();
        for (long block : blocks) {
            BlockPos from = new BlockPos(
                    ox + ShipStructureRules.px(block),
                    oy + ShipStructureRules.py(block),
                    oz + ShipStructureRules.pz(block));
            BlockState state = level.getBlockState(from);
            if (state.isAir()) {
                continue;
            }
            states.put(block, state);
            BlockEntity entity = level.getBlockEntity(from);
            if (entity != null) {
                containers.put(block, entity.saveWithFullMetadata());
            }
        }
        for (long block : blocks) {
            level.removeBlock(new BlockPos(
                    ox + ShipStructureRules.px(block),
                    oy + ShipStructureRules.py(block),
                    oz + ShipStructureRules.pz(block)), false);
        }
        for (long block : blocks) {
            BlockState state = states.get(block);
            if (state == null) {
                continue;
            }
            BlockPos to = new BlockPos(
                    ox + ShipStructureRules.px(block) + step[0],
                    oy + ShipStructureRules.py(block) + step[1],
                    oz + ShipStructureRules.pz(block) + step[2]);
            level.setBlock(to, state, 3);
            CompoundTag tag = containers.get(block);
            if (tag != null) {
                BlockEntity moved = BlockEntity.loadStatic(to, state, tag);
                if (moved != null) {
                    level.setBlockEntity(moved);
                }
            }
        }
        ship.shift(step);
        return true;
    }

    private static ShipEntity findShip(Level level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(ShipTuning.MAX_RADIUS + 1.0D);
        for (ShipEntity ship : level.getEntitiesOfClass(ShipEntity.class, area)) {
            if (ship.contains(pos.getX(), pos.getY(), pos.getZ())) {
                return ship;
            }
        }
        return null;
    }

    /** Packed relative coords -> real blocks, without ever faking a Level. */
    private static final class RelativeBlockSource implements ShipBlockSource {

        private final Level level;
        private final int ox;
        private final int oy;
        private final int oz;

        RelativeBlockSource(Level level, BlockPos origin) {
            this.level = level;
            this.ox = origin.getX();
            this.oy = origin.getY();
            this.oz = origin.getZ();
        }

        @Override
        public boolean isSolid(long packed) {
            int x = ShipStructureRules.px(packed);
            int y = ShipStructureRules.py(packed);
            int z = ShipStructureRules.pz(packed);
            if (!ShipStructureRules.inRange(x, y, z)) {
                return false;
            }
            return !level.getBlockState(new BlockPos(ox + x, oy + y, oz + z)).isAir();
        }

        @Override
        public boolean isCore(long packed) {
            return packed == ShipStructureRules.pack(0, 0, 0);
        }
    }
}
