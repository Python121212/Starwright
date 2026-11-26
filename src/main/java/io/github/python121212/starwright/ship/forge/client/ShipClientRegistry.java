package io.github.python121212.starwright.ship.forge.client;

import io.github.python121212.starwright.Starwright;
import io.github.python121212.starwright.ship.forge.ShipRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only: register the no-op renderer. The real blocks are drawn by vanilla. */
@Mod.EventBusSubscriber(modid = Starwright.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ShipClientRegistry {

    private ShipClientRegistry() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ShipRegistry.SHIP_ENTITY, NoopShipRenderer::new);
    }
}
