package io.github.python121212.starwright.ship.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One class, three Create-Aeronautics-style ship controls. A fixed
 * {@link Direction} gives the movement axis; {@code null} means "the way the
 * player is facing" (the propeller bearing).
 *
 * <ul>
 *   <li>{@code propeller_bearing} - horizontal thrust</li>
 *   <li>{@code hot_air_burner} - lift (+Y)</li>
 *   <li>{@code steam_vent} - descent (-Y)</li>
 * </ul>
 *
 * No per-tick physics: a click moves the real blocks by exactly one block.
 */
public final class ShipControlBlock extends Block {

    private final Direction motion;

    public ShipControlBlock(BlockBehaviour.Properties properties, Direction motion) {
        super(properties);
        this.motion = motion;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Direction direction = motion == null ? player.getDirection() : motion;
        return ShipForgeBridge.advance(level, pos, direction)
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
