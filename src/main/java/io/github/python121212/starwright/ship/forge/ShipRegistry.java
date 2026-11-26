package io.github.python121212.starwright.ship.forge;

import io.github.python121212.starwright.Starwright;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Registers the core block, the wrench, the ship entity and the dedicated
 * Starwright creative tab on the MOD bus.
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
    public static CreativeModeTab TAB;

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

        // CreativeModeTab lives in a VANILLA registry. Forge's
        // GameData#postRegisterEvents() also posts RegisterEvent for those keys,
        // so the dedicated tab can be registered right here - no extra bus,
        // no DeferredRegister, no dependency on registration order.
        event.register(Registries.CREATIVE_MODE_TAB, helper -> {
            TAB = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.starwright"))
                    .icon(() -> {
                        Item icon = registeredItem("wrench");
                        return icon == null ? ItemStack.EMPTY : new ItemStack(icon);
                    })
                    .displayItems((parameters, output) -> {
                        Item core = registeredItem("core_block");
                        Item wrench = registeredItem("wrench");
                        if (core != null) {
                            output.accept(core);
                        }
                        if (wrench != null) {
                            output.accept(wrench);
                        }
                    })
                    .build();
            helper.register("starwright", TAB);
        });
    }

    /**
     * Resolve items from the registry when the tab is actually built. This
     * avoids any dependency on whether ITEMS or CREATIVE_MODE_TAB was registered
     * first, and never dereferences a null field on a real client.
     */
    private static Item registeredItem(String name) {
        return ForgeRegistries.ITEMS.getValue(
                new ResourceLocation(Starwright.MOD_ID, name));
    }
}
