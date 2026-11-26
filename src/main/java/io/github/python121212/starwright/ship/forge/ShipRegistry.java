package io.github.python121212.starwright.ship.forge;

import io.github.python121212.starwright.Starwright;
import net.minecraft.core.Direction;
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
 * Registers the Create-Aeronautics-inspired (but entirely standalone) ship
 * blocks, items, entity and dedicated creative tab on the MOD bus.
 *
 * <p>The wrench and the old core block are GONE. A Physics Assembler assembles
 * the structure, and Propeller Bearing / Hot Air Burner / Steam Vent move it.
 * Levitite and Aviator's Goggles are the two standalone items.</p>
 *
 * <p>{@code RegisterEvent} (not {@code DeferredRegister}) keeps this class out
 * of the thin {@link Starwright} entry point, so the real-Forge / pseudo-Minecraft
 * jar emulator remains completely isolated.</p>
 */
@Mod.EventBusSubscriber(modid = Starwright.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ShipRegistry {

    public static Block PHYSICS_ASSEMBLER;
    public static Block PROPELLER_BEARING;
    public static Block HOT_AIR_BURNER;
    public static Block STEAM_VENT;

    public static Item ASSEMBLER_ITEM;
    public static Item BEARING_ITEM;
    public static Item BURNER_ITEM;
    public static Item VENT_ITEM;
    public static Item LEVITITE;
    public static Item AVIATORS_GOGGLES;

    public static EntityType<ShipEntity> SHIP_ENTITY;
    public static CreativeModeTab TAB;

    /** Order shown in the dedicated Starwright creative tab. */
    private static final String[] TAB_ITEMS = {
            "physics_assembler", "propeller_bearing", "hot_air_burner",
            "steam_vent", "levitite", "aviators_goggles"
    };

    private ShipRegistry() {}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            BlockBehaviour.Properties props = BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK);
            PHYSICS_ASSEMBLER = new PhysicsAssemblerBlock(props);
            PROPELLER_BEARING = new ShipControlBlock(props, null);          // player facing
            HOT_AIR_BURNER = new ShipControlBlock(props, Direction.UP);     // lift
            STEAM_VENT = new ShipControlBlock(props, Direction.DOWN);       // descent
            helper.register("physics_assembler", PHYSICS_ASSEMBLER);
            helper.register("propeller_bearing", PROPELLER_BEARING);
            helper.register("hot_air_burner", HOT_AIR_BURNER);
            helper.register("steam_vent", STEAM_VENT);
        });

        event.register(ForgeRegistries.Keys.ITEMS, helper -> {
            ASSEMBLER_ITEM = new BlockItem(PHYSICS_ASSEMBLER, new Item.Properties());
            BEARING_ITEM = new BlockItem(PROPELLER_BEARING, new Item.Properties());
            BURNER_ITEM = new BlockItem(HOT_AIR_BURNER, new Item.Properties());
            VENT_ITEM = new BlockItem(STEAM_VENT, new Item.Properties());
            LEVITITE = new LevititeItem(new Item.Properties());
            AVIATORS_GOGGLES = new AviatorsGogglesItem(new Item.Properties().stacksTo(1));
            helper.register("physics_assembler", ASSEMBLER_ITEM);
            helper.register("propeller_bearing", BEARING_ITEM);
            helper.register("hot_air_burner", BURNER_ITEM);
            helper.register("steam_vent", VENT_ITEM);
            helper.register("levitite", LEVITITE);
            helper.register("aviators_goggles", AVIATORS_GOGGLES);
        });

        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            SHIP_ENTITY = EntityType.Builder.<ShipEntity>of(ShipEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .build("ship");
            helper.register("ship", SHIP_ENTITY);
        });

        // CreativeModeTab lives in a VANILLA registry. Forge's
        // GameData#postRegisterEvents() also posts RegisterEvent for those keys.
        event.register(Registries.CREATIVE_MODE_TAB, helper -> {
            TAB = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.starwright"))
                    .icon(() -> {
                        Item icon = registeredItem("physics_assembler");
                        return icon == null ? ItemStack.EMPTY : new ItemStack(icon);
                    })
                    .displayItems((parameters, output) -> {
                        for (String name : TAB_ITEMS) {
                            Item item = registeredItem(name);
                            if (item != null) {
                                output.accept(item);
                            }
                        }
                    })
                    .build();
            helper.register("starwright", TAB);
        });
    }

    /** Resolve items from the registry when the tab is actually built. */
    private static Item registeredItem(String name) {
        return ForgeRegistries.ITEMS.getValue(
                new ResourceLocation(Starwright.MOD_ID, name));
    }
}
