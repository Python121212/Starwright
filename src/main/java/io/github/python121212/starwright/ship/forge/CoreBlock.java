package io.github.python121212.starwright.ship.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Place this anywhere inside a self-built structure that is floating in the
 * air. If - and only if - the flood fill finds no contact with the ground or
 * any other block, the structure becomes a {@link ShipEntity}. Placed on the
 * ground, it simply stays an ordinary block.
 */
public final class CoreBlock extends Block {

    public CoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ShipForgeBridge.tryAssemble(level, pos);
    }
}
