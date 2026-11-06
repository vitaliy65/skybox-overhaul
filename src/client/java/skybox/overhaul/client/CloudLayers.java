package skybox.overhaul.client;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.phys.Vec3;

/**
 * Adds two independently textured cloud layers on top of the vanilla cloud layer.
 *
 * The actual geometry and shader pipeline are still vanilla CloudRenderer. Each extra
 * renderer owns its own texture data, mesh cache and GPU buffers.
 */
public final class CloudLayers {
    private static final float MIDDLE_HEIGHT_OFFSET = 48.0F;
    private static final float UPPER_HEIGHT_OFFSET = 112.0F;
    private static final long MIDDLE_PHASE_OFFSET = 137L;
    private static final long UPPER_PHASE_OFFSET = 281L;
    private static final float MIDDLE_OPACITY = 0.58F;
    private static final float UPPER_OPACITY = 0.32F;

    private static final CloudLayerRenderer MIDDLE = new CloudLayerRenderer(
            "middle_layer",
            "textures/environment/clouds_layer_2.png"
    );
    private static final CloudLayerRenderer UPPER = new CloudLayerRenderer(
            "upper_layer",
            "textures/environment/clouds_layer_3.png"
    );

    private static boolean registered;

    public static void register() {
        if (registered) {
            return;
        }

        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(MIDDLE.listenerId(), MIDDLE);
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(UPPER.listenerId(), UPPER);
        registered = true;
    }

    public static boolean isAdditionalRenderer(CloudRenderer renderer) {
        return renderer == MIDDLE || renderer == UPPER;
    }

    public static void renderAdditional(
            int color,
            CloudStatus cloudStatus,
            float bottomY,
            int range,
            Vec3 cameraPosition,
            long gameTime,
            float partialTicks
    ) {
        MIDDLE.render(
                applyOpacity(color, MIDDLE_OPACITY),
                cloudStatus,
                bottomY + MIDDLE_HEIGHT_OFFSET,
                range,
                cameraPosition,
                gameTime + MIDDLE_PHASE_OFFSET,
                partialTicks
        );

        UPPER.render(
                applyOpacity(color, UPPER_OPACITY),
                cloudStatus,
                bottomY + UPPER_HEIGHT_OFFSET,
                range,
                cameraPosition,
                gameTime + UPPER_PHASE_OFFSET,
                partialTicks
        );
    }

    private static int applyOpacity(int argb, float opacity) {
        int alpha = (argb >>> 24) & 0xFF;
        int adjustedAlpha = Math.round(alpha * opacity);
        return (argb & 0x00FFFFFF) | (adjustedAlpha << 24);
    }

    public static void endFrame() {
        MIDDLE.endFrame();
        UPPER.endFrame();
    }

    public static void close() {
        MIDDLE.close();
        UPPER.close();
    }

    private CloudLayers() {
    }
}
