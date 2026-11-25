package io.github.python121212.starwright.ship.forge.client;

import io.github.python121212.starwright.ship.forge.ShipEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws nothing, allocates nothing, submits zero draw calls. A Starwright ship
 * is made of real in-level blocks, so the normal terrain renderer already drew
 * it before this renderer is ever asked to run. This is the single biggest
 * saving compared with a contraption-style baked ship mesh.
 */
public final class NoopShipRenderer extends EntityRenderer<ShipEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/block/iron_block.png");

    public NoopShipRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ShipEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(ShipEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Intentionally empty: no mesh rebuild, no per-frame geometry.
    }
}
