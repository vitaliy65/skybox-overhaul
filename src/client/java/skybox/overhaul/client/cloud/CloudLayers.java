package skybox.overhaul.client.cloud;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.phys.Vec3;
import skybox.overhaul.client.SkyboxState;
import skybox.overhaul.client.config.SkyboxConfig;

/**
 * Two extra vanilla CloudRenderer instances with independent masks; rain and thunder darken them like the vanilla layer.
 * Vanilla geometry, depth, fog and fast/fancy modes are retained.
 */
public final class CloudLayers {
    private static final float MIDDLE_HEIGHT_OFFSET = 48.0F;
    private static final float UPPER_HEIGHT_OFFSET = 112.0F;
    private static final long MIDDLE_PHASE_OFFSET = 137L;
    private static final long UPPER_PHASE_OFFSET = 281L;
    private static final float MIDDLE_OPACITY = 0.58F;
    private static final float UPPER_OPACITY = 0.32F;
    // Dense storm layers (clouds_storm_1/2.png): fade in with rain / thunder so the sky closes over.
    // They sit just above the vanilla layer, so they fill its gaps when seen from below.
    private static final float STORM_LOW_HEIGHT_OFFSET = 16.0F;
    private static final float STORM_HIGH_HEIGHT_OFFSET = 32.0F;
    private static final long STORM_LOW_PHASE_OFFSET = 53L;
    private static final long STORM_HIGH_PHASE_OFFSET = 211L;

    private static final CloudLayerRenderer MIDDLE = new CloudLayerRenderer(
            "middle_layer",
            "textures/environment/clouds_layer_2.png"
    );
    private static final CloudLayerRenderer UPPER = new CloudLayerRenderer(
            "upper_layer",
            "textures/environment/clouds_layer_3.png"
    );

    private static final CloudLayerRenderer STORM_LOW = new CloudLayerRenderer(
            "storm_low",
            "textures/environment/clouds_storm_1.png"
    );
    private static final CloudLayerRenderer STORM_HIGH = new CloudLayerRenderer(
            "storm_high",
            "textures/environment/clouds_storm_2.png"
    );

    private static boolean registered;
    private static long lastWeatherTick = Long.MIN_VALUE;
    private static float previousRain;
    private static double middleWindZ;
    private static double upperWindX;
    private static double upperWindZ;

    public static void register() {
        if (registered) {
            return;
        }

        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(MIDDLE.listenerId(), MIDDLE);
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(UPPER.listenerId(), UPPER);
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(STORM_LOW.listenerId(), STORM_LOW);
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(STORM_HIGH.listenerId(), STORM_HIGH);
        registered = true;
    }

    public static boolean isAdditionalRenderer(CloudRenderer renderer) {
        return renderer == MIDDLE || renderer == UPPER || renderer == STORM_LOW || renderer == STORM_HIGH;
    }

    public static void renderAdditional(
            int color,
            CloudStatus cloudStatus,
            float bottomY,
            int range,
            Vec3 cameraPosition,
            long gameTime,
            float partialTicks,
            long worldGameTime
    ) {
        float rain = clamp01(SkyboxState.rainLevel);
        // Rain AND thunder darken the extra layers like vanilla darkens its own: a brightness cap by weather strength,
        // independent of what colour vanilla passed in (the old code only tinted by rain, never by thunder).
        int rainTintedColor = CloudLighting.stormShade(color, rain, clamp01(SkyboxState.thunderLevel));

        // Integrate wind over elapsed ticks instead of multiplying total world time by current
        // rain strength. That prevents a visible cloud-pattern jump when rain starts or stops.
        updateWeatherDrift(worldGameTime, rain);
        float thunder = clamp01(SkyboxState.thunderLevel);
        float[] cover = CloudLighting.stormCover(rain, thunder, SkyboxConfig.stormClouds);
        // Storm layers first (lowest to highest), the old layers keep their order after them.
        if (cover[0] > 0.01F) STORM_LOW.render(
                applyOpacity(rainTintedColor, cover[0]),
                cloudStatus,
                bottomY + STORM_LOW_HEIGHT_OFFSET + SkyboxConfig.globalCloudHeightOffset,
                range,
                cameraPosition,
                gameTime + STORM_LOW_PHASE_OFFSET,
                partialTicks
        );
        if (cover[1] > 0.01F) STORM_HIGH.render(
                applyOpacity(rainTintedColor, cover[1]),
                cloudStatus,
                bottomY + STORM_HIGH_HEIGHT_OFFSET + SkyboxConfig.globalCloudHeightOffset,
                range,
                cameraPosition,
                gameTime + STORM_HIGH_PHASE_OFFSET,
                partialTicks
        );
        if (SkyboxConfig.middleCloudLayerEnabled) MIDDLE.render(
                applyOpacity(rainTintedColor, MIDDLE_OPACITY + 0.16F * rain),
                cloudStatus,
                bottomY + MIDDLE_HEIGHT_OFFSET + SkyboxConfig.globalCloudHeightOffset + SkyboxConfig.middleCloudLayerOffset,
                range,
                cameraPosition.add(0.0D, 0.0D, middleWindZ),
                gameTime + MIDDLE_PHASE_OFFSET,
                partialTicks
        );
        if (SkyboxConfig.upperCloudLayerEnabled) UPPER.render(
                applyOpacity(rainTintedColor, UPPER_OPACITY + 0.07F * rain),
                cloudStatus,
                bottomY + UPPER_HEIGHT_OFFSET + SkyboxConfig.globalCloudHeightOffset + SkyboxConfig.upperCloudLayerOffset,
                range,
                cameraPosition.add(upperWindX, 0.0D, upperWindZ),
                gameTime + UPPER_PHASE_OFFSET,
                partialTicks
        );
    }

    private static void updateWeatherDrift(long gameTime, float rain) {
        if (lastWeatherTick == Long.MIN_VALUE || gameTime < lastWeatherTick) {
            lastWeatherTick = gameTime;
            previousRain = rain;
            middleWindZ = 0.0D;
            upperWindX = 0.0D;
            upperWindZ = 0.0D;
            return;
        }

        long elapsedTicks = gameTime - lastWeatherTick;
        float averageRain = (previousRain + rain) * 0.5F;
        double speed = SkyboxConfig.cloudSpeed;
        middleWindZ += elapsedTicks * 0.010D * averageRain * speed;
        upperWindX += elapsedTicks * 0.0035D * averageRain * speed;
        upperWindZ -= elapsedTicks * 0.006D * averageRain * speed;
        lastWeatherTick = gameTime;
        previousRain = rain;
    }

    private static int applyOpacity(int argb, float opacity) {
        int alpha = (argb >>> 24) & 0xFF;
        int adjustedAlpha = Math.round(alpha * clamp01(opacity));
        return (argb & 0x00FFFFFF) | (adjustedAlpha << 24);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public static void endFrame() {
        CloudMotion.advance();
        MIDDLE.endFrame();
        UPPER.endFrame();
        STORM_LOW.endFrame();
        STORM_HIGH.endFrame();
    }

    public static void close() {
        MIDDLE.close();
        UPPER.close();
        STORM_LOW.close();
        STORM_HIGH.close();
        lastWeatherTick = Long.MIN_VALUE;
        previousRain = 0.0F;
        middleWindZ = 0.0D;
        upperWindX = 0.0D;
        upperWindZ = 0.0D;
    }

    private CloudLayers() {
    }
}
