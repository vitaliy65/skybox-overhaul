package skybox.overhaul.client.cloud;

import skybox.overhaul.client.sky.GlowLayers;

/** Values packed into the std140 padding float of vanilla CloudInfo. */
public final class CloudLighting {
    private CloudLighting() {}

    /** World-X direction of the low sun: east at sunrise (+X), west at sunset (-X). */
    public static float sunSideX(float sunAngle) {
        return GlowLayers.sunInWest(sunAngle) ? -1.0F : 1.0F;
    }

    /** Packs sun side and warm-vs-afterglow phase into CloudOffset.w. */
    public static float packedSunDirection(float sunAngle) {
        float elevation = GlowLayers.sunElevationDeg(sunAngle);
        float warmth = smoothstep(-12.0F, 2.0F, elevation);
        if (sunSideX(sunAngle) > 0.0F) warmth *= 0.28F; // dawn stays rose-blue, not amber
        return sunSideX(sunAngle) * (1.0F + warmth * 0.5F);
    }

    /** Warm sunset light and a restrained rosy dawn afterglow. */
    public static float twilightStrength(float sunAngle, float rain) {
        float elevation = GlowLayers.sunElevationDeg(sunAngle);
        float gold = GlowLayers.weight(GlowLayers.ALL[0], elevation);
        float orange = GlowLayers.weight(GlowLayers.ALL[1], elevation);
        float pink = GlowLayers.weight(GlowLayers.ALL[2], elevation);
        float violet = GlowLayers.weight(GlowLayers.ALL[3], elevation);
        float strength = Math.max(gold,
                Math.max(orange * 0.90F, Math.max(pink * 0.84F, violet * 0.96F)));
        if (sunSideX(sunAngle) > 0.0F) strength *= 0.30F;
        strength *= 0.94F * (1.0F - clamp01(rain) * 0.78F);
        return clamp01(strength);
    }

    /** Faint blue moonlight comes in as the sun moves well below the horizon. */
    public static float moonlightStrength(float sunAngle, float rain) {
        float elevation = GlowLayers.sunElevationDeg(sunAngle);
        float night = smoothstep(8.0F, 28.0F, -elevation);
        return clamp01(night * 0.20F * (1.0F - clamp01(rain) * 0.88F));
    }

    /** A vanilla sky flash lasts only a few ticks; map it to a short 0..1 shader pulse. */
    public static float lightningStrength(int skyFlashTime) {
        return clamp01(skyFlashTime * 0.5F);
    }

    /**
     * Packs three 8-bit channels into one exactly representable float (24-bit integer range):
     * low byte = twilight, middle byte = moonlight, high byte = lightning.
     */
    public static float packLighting(float twilight, float moonlight, float lightning) {
        int packed = toByte(twilight) | (toByte(moonlight) << 8) | (toByte(lightning) << 16);
        return (float) packed;
    }

    private static int toByte(float value) {
        return Math.round(clamp01(value) * 255.0F);
    }

    /** Scales only the cloud colour alpha, preserving the original RGB tint. */
    public static int applyOpacity(int argb, float opacity) {
        int alpha = (argb >>> 24) & 0xFF;
        int adjustedAlpha = Math.round(alpha * clamp01(opacity));
        return (argb & 0x00FFFFFF) | (adjustedAlpha << 24);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public static void main(String[] args) {
        float sunrise = (float) (Math.PI * 1.5);
        float sunset = (float) (Math.PI * 0.5);
        check(sunSideX(sunrise) > 0.0F, "sunrise points east");
        check(sunSideX(sunset) < 0.0F, "sunset points west");
        check(twilightStrength(0.0F, 0.0F) == 0.0F, "noon is neutral");
        check(twilightStrength((float) Math.PI, 0.0F) == 0.0F, "midnight is neutral");
        check(twilightStrength(sunset, 0.0F) > 0.4F, "sunset has warm light");
        check(twilightStrength(sunrise, 0.0F) < twilightStrength(sunset, 0.0F), "dawn glow is cooler");
        check(moonlightStrength((float) Math.PI, 0.0F) > 0.19F, "moonlight at midnight");
        check(moonlightStrength(0.0F, 0.0F) == 0.0F, "no moonlight at noon");
        check(lightningStrength(0) == 0.0F && lightningStrength(2) == 1.0F, "short lightning pulse");
        float packed = packLighting(0.25F, 0.5F, 1.0F);
        check(packed >= 0.0F && packed <= 16777215.0F, "packed channels fit exact float integer range");
        check(Math.round(packed % 256.0F) == Math.round(0.25F * 255.0F), "twilight byte");
        check(Math.round((packed / 256.0F) % 256.0F) == Math.round(0.5F * 255.0F), "moon byte");
        check((int) (packed / 65536.0F) == 255, "lightning byte");
        check(packedSunDirection(sunrise) > 1.0F && packedSunDirection(sunset) < -1.0F, "sun side and warmth packing");
        check(twilightStrength(sunset, 1.0F) < twilightStrength(sunset, 0.0F), "rain softens warm light");
        System.out.println("CloudLighting OK");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
