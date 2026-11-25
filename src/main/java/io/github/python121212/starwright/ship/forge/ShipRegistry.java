package io.github.python121212.starwright.ship.forge;

import io.github.python121212.starwright.Starwright;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Registers the core block, the wrench and the ship entity on the MOD bus.
 *
 * <p>{@code RegisterEvent} (not {@code DeferredRegister}) is used so that this
 * class is discovered by FML's {@code @Mod.EventBusSubscriber} scan and never
 * has to be referenced from the thin {@link Starwright} entry point. That keeps
 * the real-Forge / pseudo-Minecraft jar emulator completely untouched.</p>
 */
@Mod.EventBusSubscriber(modid = Starwright.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ShipRegistry {

    public static Block CORE_BLOCK;
    public static Item CORE_BLOCK_ITEM;
    public static Item WRENCH;
    public static EntityType<ShipEntity> SHIP_ENTITY;

    private ShipRegistry() {}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            CORE_BLOCK = new CoreBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK));
            helper.register("core_block", CORE_BLOCK);
        });

        event.register(ForgeRegistries.Keys.ITEMS, helper -> {
            CORE_BLOCK_ITEM = new BlockItem(CORE_BLOCK, new Item.Properties());
            WRENCH = new WrenchItem(new Item.Properties().stacksTo(1));
            helper.register("core_block", CORE_BLOCK_ITEM);
            helper.register("wrench", WRENCH);
        });

        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            SHIP_ENTITY = EntityType.Builder.<ShipEntity>of(ShipEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .build("ship");
            helper.register("ship", SHIP_ENTITY);
        });
    }
}
