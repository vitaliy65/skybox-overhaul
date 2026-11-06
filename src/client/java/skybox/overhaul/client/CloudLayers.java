package skybox.overhaul.client;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.phys.Vec3;

/**
 * Two extra vanilla CloudRenderer instances with independent masks and subtle weather response.
 * Vanilla geometry, depth, fog and fast/fancy modes are retained.
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
        float rain = clamp01(SkyboxState.rainLevel);
        int rainTintedColor = tintForRain(color, rain);

        // Integrate wind over elapsed ticks instead of multiplying total world time by current
        // rain strength. That prevents a visible cloud-pattern jump when rain starts or stops.
        updateWeatherDrift(gameTime, rain);

        MIDDLE.render(
                applyOpacity(rainTintedColor, MIDDLE_OPACITY + 0.16F * rain),
                cloudStatus,
                bottomY + MIDDLE_HEIGHT_OFFSET,
                range,
                cameraPosition.add(0.0D, 0.0D, middleWindZ),
                gameTime + MIDDLE_PHASE_OFFSET,
                partialTicks
        );

        UPPER.render(
                applyOpacity(rainTintedColor, UPPER_OPACITY + 0.07F * rain),
                cloudStatus,
                bottomY + UPPER_HEIGHT_OFFSET,
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
        middleWindZ += elapsedTicks * 0.010D * averageRain;
        upperWindX += elapsedTicks * 0.0035D * averageRain;
        upperWindZ -= elapsedTicks * 0.006D * averageRain;
        lastWeatherTick = gameTime;
        previousRain = rain;
    }

    /** Rain reduces the white daylight look and pushes the extra layers toward cool storm gray. */
    private static int tintForRain(int argb, float rain) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        float amount = rain * 0.48F;
        red = lerp(red, 105, amount);
        green = lerp(green, 116, amount);
        blue = lerp(blue, 132, amount);
        float dim = 1.0F - rain * 0.20F;
        red = Math.round(red * dim);
        green = Math.round(green * dim);
        blue = Math.round(blue * dim);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int lerp(int from, int to, float amount) {
        return Math.max(0, Math.min(255, Math.round(from + (to - from) * amount)));
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
        MIDDLE.endFrame();
        UPPER.endFrame();
    }

    public static void close() {
        MIDDLE.close();
        UPPER.close();
        lastWeatherTick = Long.MIN_VALUE;
        previousRain = 0.0F;
        middleWindZ = 0.0D;
        upperWindX = 0.0D;
        upperWindZ = 0.0D;
    }

    private CloudLayers() {
    }
}
