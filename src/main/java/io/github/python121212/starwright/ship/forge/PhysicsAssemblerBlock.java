package io.github.python121212.starwright.ship.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Starwright's own take on Create: Aeronautics' Physics Assembler.
 *
 * <p>Place it anywhere inside a self-built structure that is floating in the
 * air: the connected blocks become ONE {@link ShipEntity}. No wrench and no
 * separate core block are needed - this block is both the assembly point and
 * the control point.</p>
 *
 * <ul>
 *   <li>Shift + right click: detach an accidentally attached block.</li>
 *   <li>Right click: nudge the ship one block forward.</li>
 *   <li>Everything else falls through to vanilla (chests, placing, walking).</li>
 * </ul>
 */
public final class PhysicsAssemblerBlock extends Block {

    public PhysicsAssemblerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ShipForgeBridge.tryAssemble(level, pos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            return ShipForgeBridge.detach(level, pos)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return ShipForgeBridge.advance(level, pos, player.getDirection())
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
