package skybox.overhaul.client.cloud;

import skybox.overhaul.client.sky.GlowLayers;

/** Lighting values packed into the existing 44-byte CloudInfo block without extending its UBO. */
public final class CloudLighting {
    private CloudLighting() {}

    /** World-X direction of the low sun: east at sunrise (+X), west at sunset (-X). */
    public static float sunSideX(float sunAngle) {
        return GlowLayers.sunInWest(sunAngle) ? -1.0F : 1.0F;
    }

    /** Packs sun side into the sign and the warm-vs-afterglow phase into the magnitude. */
    public static float packedSunDirection(float sunAngle) {
        float elevation = GlowLayers.sunElevationDeg(sunAngle);
        float warmth = smoothstep(-12.0F, 2.0F, elevation);
        if (sunSideX(sunAngle) > 0.0F) warmth *= 0.28F; // dawn stays rose-blue, not amber
        return sunSideX(sunAngle) * (1.0F + warmth * 0.5F);
    }

    /** Evening glow is stronger and warmer; morning retains only a restrained pink-blue afterglow. */
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

    /** Subtle moon fill at night, strongest on clear nights. */
    public static float moonlightStrength(float sunAngle, float rain) {
        float elevation = GlowLayers.sunElevationDeg(sunAngle);
        return clamp01(smoothstep(8.0F, 28.0F, -elevation) * 0.20F * (1.0F - clamp01(rain) * 0.88F));
    }

    /** Minecraft's short sky flash timer is 0 when inactive and 2 at the brightest frame. */
    public static float lightningStrength(int skyFlashTime) {
        return clamp01(skyFlashTime * 0.5F);
    }

    /**
     * CloudInfo is 44 bytes in 26.3: CloudColor (0..15), CloudOffset (16..27), CellSize (32..43).
     * Offset 28 is the only spare float. The CloudColor alpha word at offset 12 stores four quantized
     * channels inside a finite float's mantissa: alpha(7), twilight(6), moon(5), lightning(5) bits.
     * The exponent remains 127, so the packed word is always a normal finite float in [1, 2).
     */
    public static int packAlphaAndLightingBits(int argb, float twilight, float moonlight, float lightning) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * (127.0F / 255.0F));
        int t = Math.round(clamp01(twilight) * 63.0F);
        int moon = Math.round(clamp01(moonlight) * 31.0F);
        int flash = Math.round(clamp01(lightning) * 31.0F);
        int mantissa = (alpha << 16) | (t << 10) | (moon << 5) | flash;
        return 0x3F800000 | mantissa;
    }

    public static float packAlphaAndLighting(int argb, float twilight, float moonlight, float lightning) {
        return Float.intBitsToFloat(packAlphaAndLightingBits(argb, twilight, moonlight, lightning));
    }

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
        check(twilightStrength(sunset, 0.0F) > twilightStrength(sunrise, 0.0F), "sunset glow exceeds dawn");
        float lateSunset = sunset + (float) Math.toRadians(15.0);
        check(twilightStrength(lateSunset, 0.0F) > 0.25F, "violet afterglow remains visible below horizon");
        check(twilightStrength(sunset, 1.0F) < twilightStrength(sunset, 0.0F), "rain softens sunset light");
        check(Math.abs(packedSunDirection(sunrise)) < Math.abs(packedSunDirection(sunset)), "dawn is less warm");
        check(moonlightStrength((float) Math.PI, 0.0F) > 0.19F, "moonlight at midnight");
        check(moonlightStrength(0.0F, 0.0F) == 0.0F, "no moonlight at noon");
        check(moonlightStrength((float) Math.PI, 1.0F) < moonlightStrength((float) Math.PI, 0.0F), "rain dims moonlight");
        check(lightningStrength(0) == 0.0F && lightningStrength(2) == 1.0F, "short lightning pulse");
        int bits = packAlphaAndLightingBits(0x80ABCDEF, 0.25F, 0.5F, 1.0F);
        check(Float.floatToRawIntBits(packAlphaAndLighting(0x80ABCDEF, 0.25F, 0.5F, 1.0F)) == bits,
                "packed channels survive float storage");
        check((bits & 0x7F800000) == 0x3F800000, "packed alpha word remains finite");
        check(((bits >>> 16) & 0x7F) == Math.round(128.0F * 127.0F / 255.0F), "packed alpha channel");
        check(((bits >>> 10) & 0x3F) == Math.round(0.25F * 63.0F), "packed twilight channel");
        check(((bits >>> 5) & 0x1F) == Math.round(0.5F * 31.0F), "packed moon channel");
        check((bits & 0x1F) == 31, "packed lightning channel");
        check(applyOpacity(0x80ABCDEF, 0.5F) == 0x40ABCDEF, "opacity preserves RGB");
        System.out.println("CloudLighting OK");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
