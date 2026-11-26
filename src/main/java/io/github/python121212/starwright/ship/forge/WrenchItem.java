package io.github.python121212.starwright.ship.forge;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Right click a ship block: nudge the ship one block.
 * Shift + right click: detach an accidentally attached block and drop it.
 *
 * <p>Everything else is left to vanilla on purpose. Because the ship's blocks
 * remain real blocks in the {@link Level}, chests still open, blocks can still
 * be placed inside, and players can still walk around - with zero extra code.</p>
 */
public final class WrenchItem extends Item {

    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            return ShipForgeBridge.detach(level, context.getClickedPos())
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        Direction direction = player.getDirection();
        return ShipForgeBridge.advance(level, context.getClickedPos(), direction)
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
