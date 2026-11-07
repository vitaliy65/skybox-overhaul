package skybox.overhaul.client.cloud;

import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.resources.Identifier;

/**
 * A vanilla cloud renderer instance that reads a layer-specific cloud mask.
 * Geometry, lighting, fog and graphics-mode handling remain vanilla.
 */
public final class CloudLayerRenderer extends CloudRenderer {
    private final Identifier listenerId;

    public CloudLayerRenderer(String layerName, String texturePath) {
        this.listenerId = Identifier.fromNamespaceAndPath("skybox-overhaul", "clouds/" + layerName);
        ((CloudTextureOverride) (Object) this).skybox$setTextureOverride(
                Identifier.fromNamespaceAndPath("skybox-overhaul", texturePath)
        );
    }

    public Identifier listenerId() {
        return this.listenerId;
    }
}
