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

    /**
     * Brightest an extra cloud layer may be in bad weather, as a share of white. Same numbers as vanilla's storm clouds:
     * full rain scales brightness by 0.62, full thunder by a further 0.24. A CAP, not another darkening on top: if the
     * colour vanilla handed us is already that dark (or it is night) nothing changes, so layers never get darker than vanilla's.
     */
    public static float stormCap(float rain, float thunder) {
        return (1.0F - 0.38F * clamp01(rain)) * (1.0F - 0.76F * clamp01(thunder));
    }

    /** Caps the brightness of an ARGB cloud colour for rain/thunder (hue kept, alpha untouched) and cools it slightly. */
    public static int stormShade(int argb, float rain, float thunder) {
        float red = (argb >>> 16) & 0xFF;
        float green = (argb >>> 8) & 0xFF;
        float blue = argb & 0xFF;
        float luminance = (0.30F * red + 0.59F * green + 0.11F * blue) / 255.0F;
        float cap = stormCap(rain, thunder);
        if (luminance > cap) {
            float scale = cap / luminance;
            red *= scale;
            green *= scale;
            blue *= scale;
            luminance = cap;
        }
        // blue-grey storm tint with the same brightness: pull the hue toward (105, 116, 132), keep the luminance
        float cool = 0.35F * clamp01(Math.max(rain, thunder));
        float targetLuminance = (0.30F * 105.0F + 0.59F * 116.0F + 0.11F * 132.0F) / 255.0F;
        float k = luminance / targetLuminance;
        red = red + (105.0F * k - red) * cool;
        green = green + (116.0F * k - green) * cool;
        blue = blue + (132.0F * k - blue) * cool;
        return (argb & 0xFF000000) | (to255(red) << 16) | (to255(green) << 8) | to255(blue);
    }

    private static int to255(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    /**
     * Opacity (0..1) of the two dense storm layers: [0] "overcast" fades in with rain, [1] "thunderhead" with thunder.
     * amount is the user's cover setting (0 = off). Smooth in rain/thunder, so the sky closes in gradually.
     */
    public static float[] stormCover(float rain, float thunder, float amount) {
        float a = clamp01(amount);
        return new float[] {
            a * 0.85F * smoothstep(0.10F, 0.85F, rain),
            a * 0.92F * smoothstep(0.05F, 0.85F, thunder)
        };
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
        check(stormCap(0.0F, 0.0F) == 1.0F, "clear sky: no cap");
        check(Math.abs(stormCap(1.0F, 0.0F) - 0.62F) < 1e-5F, "rain cap like vanilla");
        check(Math.abs(stormCap(1.0F, 1.0F) - 0.1488F) < 1e-4F, "thunder cap like vanilla");
        check(stormCap(1.0F, 1.0F) < stormCap(1.0F, 0.0F) && stormCap(1.0F, 0.0F) < stormCap(0.5F, 0.0F), "darker with weather");
        check(stormShade(0xB2FFFFFF, 0.0F, 0.0F) == 0xB2FFFFFF, "clear: colour untouched");
        check((stormShade(0xB2FFFFFF, 0.0F, 0.0F) >>> 24) == 0xB2, "alpha kept");
        int thunderWhite = stormShade(0xFFFFFFFF, 1.0F, 1.0F);
        check(lum(thunderWhite) < 0.17F && lum(thunderWhite) > 0.12F, "white cloud in thunder is dark: " + lum(thunderWhite));
        int rainWhite = stormShade(0xFFFFFFFF, 1.0F, 0.0F);
        check(lum(rainWhite) < 0.65F && lum(rainWhite) > 0.58F, "white cloud in rain is mid grey: " + lum(rainWhite));
        int already = 0xFF666666; // vanilla already darkened it: never darker than that by the cap alone
        check(Math.abs(lum(stormShade(already, 1.0F, 0.0F)) - lum(already)) < 0.01F, "no double darkening");
        int night = stormShade(0xFF202838, 1.0F, 1.0F);
        check(Math.abs(lum(night) - lum(0xFF202838)) < 0.02F, "dark night colour keeps its brightness");
        check(((thunderWhite >> 16) & 0xFF) < (thunderWhite & 0xFF), "storm tint leans blue");
        float[] dry = stormCover(0.0F, 0.0F, 1.0F);
        check(dry[0] == 0.0F && dry[1] == 0.0F, "clear sky: no storm layers");
        float[] rainy = stormCover(1.0F, 0.0F, 1.0F);
        check(rainy[0] > 0.8F && rainy[1] == 0.0F, "rain: overcast only");
        float[] thundery = stormCover(1.0F, 1.0F, 1.0F);
        check(thundery[0] > 0.8F && thundery[1] > 0.9F, "thunder: both layers");
        check(stormCover(1.0F, 1.0F, 0.0F)[0] == 0.0F && stormCover(1.0F, 1.0F, 0.0F)[1] == 0.0F, "amount 0 = off");
        float prevCover = 0.0F;
        for (float r = 0.0F; r <= 1.0F; r += 0.01F) {
            float c = stormCover(r, 0.0F, 1.0F)[0];
            check(c >= prevCover - 1e-6F && c - prevCover < 0.06F, "smooth, no jump");
            prevCover = c;
        }
        System.out.println("CloudLighting OK");
    }

    private static float lum(int argb) {
        return (0.30F * ((argb >>> 16) & 0xFF) + 0.59F * ((argb >>> 8) & 0xFF) + 0.11F * (argb & 0xFF)) / 255.0F;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
