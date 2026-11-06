package skybox.overhaul.client;

/** Values packed into the two std140 padding floats of vanilla CloudInfo. */
public final class CloudLighting {
    private CloudLighting() {}

    /** World-X direction of the low sun: east at sunrise (+X), west at sunset (-X). */
    public static float sunSideX(float sunAngle) {
        return GlowLayers.sunInWest(sunAngle) ? -1.0F : 1.0F;
    }

    /**
     * Packs the sun side into the sign and the warm-vs-afterglow phase into the magnitude.
     * The vanilla CloudInfo block has only two spare floats, so this preserves its 48-byte layout.
     * Magnitude 1.0 means cool afterglow; magnitude 1.5 means warm low-sun light.
     */
    public static float packedSunDirection(float sunAngle) {
        float elevation = GlowLayers.sunElevationDeg(sunAngle);
        float warmth = smoothstep(-12.0F, 2.0F, elevation);
        return sunSideX(sunAngle) * (1.0F + warmth * 0.5F);
    }

    /** Includes the violet afterglow below the horizon, not only the warm golden-hour bands. */
    public static float twilightStrength(float sunAngle, float rain) {
        float elevation = GlowLayers.sunElevationDeg(sunAngle);
        float gold = GlowLayers.weight(GlowLayers.ALL[0], elevation);
        float orange = GlowLayers.weight(GlowLayers.ALL[1], elevation);
        float pink = GlowLayers.weight(GlowLayers.ALL[2], elevation);
        float violet = GlowLayers.weight(GlowLayers.ALL[3], elevation);
        float strength = Math.max(gold,
                Math.max(orange * 0.90F, Math.max(pink * 0.84F, violet * 0.96F)));
        // Heavy rain suppresses direct sunset light while retaining a subdued cool afterglow.
        strength *= 0.94F * (1.0F - clamp01(rain) * 0.78F);
        return clamp01(strength);
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
        float lateSunset = sunset + (float) Math.toRadians(15.0);
        check(twilightStrength(lateSunset, 0.0F) > 0.25F, "violet afterglow remains visible below horizon");
        check(packedSunDirection(sunrise) > 1.0F, "sunrise packs warm phase with east direction");
        check(packedSunDirection(sunset) < -1.0F, "sunset packs warm phase with west direction");
        check(twilightStrength(sunset, 1.0F) < twilightStrength(sunset, 0.0F), "rain softens warm light");
        System.out.println("CloudLighting OK");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
