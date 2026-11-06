package skybox.overhaul.client;

/** Values packed into the two std140 padding floats of vanilla CloudInfo. */
public final class CloudLighting {
    private CloudLighting() {}

    /** World-X direction of the low sun: east at sunrise (+X), west at sunset (-X). */
    public static float sunSideX(float sunAngle) {
        return GlowLayers.sunInWest(sunAngle) ? -1.0F : 1.0F;
    }

    /** 0 at midday/night, strongest around golden hour and twilight; rain softens direct colour. */
    public static float twilightStrength(float sunAngle, float rain) {
        float elevation = GlowLayers.sunElevationDeg(sunAngle);
        float gold = GlowLayers.weight(GlowLayers.ALL[0], elevation);
        float orange = GlowLayers.weight(GlowLayers.ALL[1], elevation);
        float pink = GlowLayers.weight(GlowLayers.ALL[2], elevation);
        float strength = Math.max(gold, Math.max(orange * 0.82F, pink * 0.58F));
        strength *= 0.62F * (1.0F - clamp01(rain) * 0.72F);
        return clamp01(strength);
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
        check(twilightStrength(sunset, 1.0F) < twilightStrength(sunset, 0.0F), "rain softens warm light");
        System.out.println("CloudLighting OK");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
