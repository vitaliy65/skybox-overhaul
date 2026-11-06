package skybox.overhaul.client;

import net.minecraft.resources.Identifier;

/**
 * Per-instance texture override for vanilla CloudRenderer instances used by extra cloud layers.
 * Implemented by CloudRendererMixin.
 */
public interface CloudTextureOverride {
    void skybox$setTextureOverride(Identifier texture);
    Identifier skybox$getTextureOverride();
}
