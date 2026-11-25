package io.github.python121212.starwright.space.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.python121212.starwright.Starwright;
import io.github.python121212.starwright.space.SpaceRenderRules;
import io.github.python121212.starwright.space.SpaceState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Client-only sky: gradually darkens the sky (Step 2) and reveals more stars
 * (Step 3). It is the ONLY renderer added by this mod.
 *
 * <p>LIGHTWEIGHT (learned from AstroDock's SkyLayer/AtmosphereClient, collapsed
 * into one class):</p>
 * <ul>
 *   <li>the 240 star quads are built ONCE from a fixed seed at class load;</li>
 *   <li>each frame draws only the first {@code visibleStars} quads - no
 *       geometry rebuild, no star entity, no texture, no resource pack;</li>
 *   <li>one {@link BufferBuilder} / one {@link BufferUploader} call for both
 *       the sky cube and the stars;</li>
 *   <li>at {@code darkness == 0} (the ground) it returns immediately, so
 *       vanilla rendering is untouched;</li>
 *   <li>all colour/count maths lives in the pure {@link SpaceRenderRules}.</li>
 * </ul>
 *
 * <p>Step 4: when {@link SpaceState#skipGroundRendering()} is true this class
 * deliberately issues NO ground-related draw calls at all. The matching
 * {@code SpaceLevelRendererMixin} cancels {@code LevelRenderer#renderChunkLayer}
 * at HEAD, so terrain chunk rendering is completely skipped while the sky is
 * still rendered here. The server is never involved.</p>
 */
@Mod.EventBusSubscriber(
        modid = Starwright.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpaceSkyRenderer {

    /** 6 cube faces, 4 corner indices each. */
    private static final int[][] CUBE_FACES = {
            {0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4},
            {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}
    };

    /** Fixed-seed geometry, generated once. 240 quads × 4 vertices × xyz. */
    private static final float[] STAR_QUADS = buildStars();

    private SpaceSkyRenderer() {}

    private static float[] buildStars() {
        float[] data = new float[SpaceRenderRules.STAR_COUNT * 12];
        Random random = new Random(7442);
        Vec3 worldUp = new Vec3(0, 1, 0);
        for (int i = 0; i < SpaceRenderRules.STAR_COUNT; i++) {
            double z = random.nextDouble() * 2 - 1;
            double phi = random.nextDouble() * 2 * Math.PI;
            double radius = Math.sqrt(1 - z * z);
            Vec3 normal = new Vec3(radius * Math.cos(phi), z, radius * Math.sin(phi));
            Vec3 side = normal.cross(worldUp).normalize();
            Vec3 vertical = side.cross(normal);
            double size = 0.007 + random.nextDouble() * 0.018;
            Vec3 center = normal.scale(12);

            int base = i * 12;
            for (int corner = 0; corner < 4; corner++) {
                Vec3 point = center
                        .add(side.scale(corner == 0 || corner == 3 ? -size : size))
                        .add(vertical.scale(corner < 2 ? -size : size));
                data[base + corner * 3] = (float) point.x;
                data[base + corner * 3 + 1] = (float) point.y;
                data[base + corner * 3 + 2] = (float) point.z;
            }
        }
        return data;
    }

    /** Step 2 + 3: one draw call for the darkened sky and the visible stars. */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        SpaceState state = SpaceClientState.current();
        if (state.darkness() <= 0.0) {
            return; // ground: leave vanilla rendering completely alone
        }

        int sky = SpaceRenderRules.skyColor(state.darkness());
        int red = (sky >> 16) & 255;
        int green = (sky >> 8) & 255;
        int blue = sky & 255;
        int stars = SpaceRenderRules.visibleStars(state.starVisibility());
        Matrix4f matrix = event.getPoseStack().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            for (int[] face : CUBE_FACES) {
                for (int corner : face) {
                    float height = (corner & 2) == 0 ? -16.0f : 16.0f;
                    float light = height < 0.0f ? 1.0f : 0.64f;
                    builder.vertex(matrix,
                                    (corner & 1) == 0 ? -16.0f : 16.0f,
                                    height,
                                    (corner & 4) == 0 ? -16.0f : 16.0f)
                            .color((int) (red * light), (int) (green * light),
                                    (int) (blue * light), 210)
                            .endVertex();
                }
            }
            // Only the first `stars` quads: visibility grows with altitude.
            for (int i = 0; i < stars; i++) {
                int base = i * 12;
                for (int j = 0; j < 12; j += 3) {
                    builder.vertex(matrix, STAR_QUADS[base + j],
                                    STAR_QUADS[base + j + 1],
                                    STAR_QUADS[base + j + 2])
                            .color(215, 226, 250, 235)
                            .endVertex();
                }
            }
            BufferUploader.drawWithShader(builder.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
        // Step 4: no ground-related draw call is issued here. The mixin cancels
        // renderChunkLayer, and no chunk exists above the build limit anyway.
    }

    /** Step 2: darken the fog with altitude so the ground fades out smoothly. */
    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        double darkness = SpaceClientState.darkness();
        if (darkness <= 0.0) {
            return;
        }
        float fog = (float) SpaceRenderRules.fogFactor(darkness);
        float air = (float) Math.sqrt(fog);
        event.setRed(event.getRed() * fog);
        event.setGreen(event.getGreen() * fog);
        event.setBlue(event.getBlue() * air);
    }
}
