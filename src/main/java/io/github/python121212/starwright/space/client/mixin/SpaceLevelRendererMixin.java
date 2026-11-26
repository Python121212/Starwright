package io.github.python121212.starwright.space.client.mixin;

import io.github.python121212.starwright.space.client.SpaceClientState;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Step 4: "the ground is not drawn AT ALL" once it is invisible.
 *
 * <p>This is the ONLY mixin in the mod - one class, one injection, no refmap,
 * no MixinGradle plugin, no extra runtime dependency. It cancels
 * {@code LevelRenderer#renderChunkLayer} (SRG {@code m_172993_}) at HEAD, so
 * terrain chunk rendering is skipped entirely while the sky/entities are still
 * rendered normally by {@code SpaceSkyRenderer}.</p>
 *
 * <p>The SRG name is written literally with {@code remap = false}, which is why
 * no refmap is needed. {@code require = 0} keeps a dev workspace (official
 * names) from crashing when the SRG-named method is absent; the production
 * re-obfuscated jar always has it.</p>
 *
 * <p>Client only: {@link SpaceClientState#skipGroundRendering()} is explicitly
 * client-side. The server keeps ticking every ground chunk.</p>
 */
@Mixin(LevelRenderer.class)
public final class SpaceLevelRendererMixin {

    @Inject(
            method = "m_172993_",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void starwrightSkipGroundRendering(CallbackInfo info) {
        if (SpaceClientState.skipGroundRendering()) {
            info.cancel();
        }
    }
}
