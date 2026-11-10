package skybox.overhaul.client.sky;

import skybox.overhaul.client.config.SkyboxConfig;

/**
 * Sun height -> sky colours. s = sin(sun elevation) = cos(vanilla sun angle):
 * 1 noon, 0 horizon, -1 midnight. Horizon colour feeds fog (so terrain and sky meet),
 * zenith colour feeds the sky disc; vanilla fog blends one into the other = the gradient.
 * Pure Java on purpose: self-check with  java -ea SkyPalette.java
 */
public final class SkyPalette {
	// descending s: noon, day, afternoon, golden hour, sunset, horizon, rose, purple dusk, indigo, night
	private static final float[] S       = {1.00F,    0.50F,    0.30F,    0.15F,    0.05F,    -0.02F,   -0.09F,   -0.18F,   -0.32F,   -1.00F};
	private static final int[] HORIZON   = {0xBFDDFF, 0xCFE3FA, 0xFFDCA0, 0xFFC25A, 0xFF9E2A, 0xFF6A1E, 0xE0455E, 0x9A3A8C, 0x3F3A86, 0x16224C};
	// Dawn stays cooler: pale blue above a restrained pink/lilac horizon instead of sunset amber.
	private static final int[] DAWN_HORIZON = {0xBFDDFF, 0xCFE3FA, 0xDDE9FA, 0xE9D2E8, 0xF1BBD3, 0xDCA2C9, 0xAE91C2, 0x7184B5, 0x3F5A91, 0x16224C};
	// Sunset (s ~ 0.05 .. -0.09): saturated yellow-orange horizon under a crimson -> magenta top, like a stylised
	// "sunset of seven suns" sky; then purple -> indigo -> night. Daytime and night keys are unchanged.
	private static final int[] ZENITH    = {0x3F86EE, 0x4F90EE, 0x6B93E0, 0xA874C0, 0xB93A5E, 0x92306A, 0x5A2B7A, 0x2C2468, 0x0E1233, 0x050818};
	private static final int[] DAWN_ZENITH = {0x3F86EE, 0x4F90EE, 0x6D93D9, 0x849BD7, 0x9CA4DF, 0x8F93CD, 0x6A75B1, 0x43578F, 0x202F63, 0x050818};

	private SkyPalette() {}

	/** Fog colour: vanilla blended toward the palette horizon colour, then the biome tint on top. */
	public static int fog(int vanilla, float sunAngleRad, float rain) {
		int palette = mix(vanilla, sampleForTime(HORIZON, DAWN_HORIZON, sunAngleRad), SkyboxConfig.paletteStrength * (1.0F - rain));
		return BiomeSky.tint(palette, true, sunAngleRad, rain);
	}

	/** Sky disc colour: vanilla blended toward the palette zenith colour, then the biome tint on top. */
	public static int sky(int vanilla, float sunAngleRad, float rain) {
		int palette = mix(vanilla, sampleForTime(ZENITH, DAWN_ZENITH, sunAngleRad), SkyboxConfig.paletteStrength * (1.0F - rain));
		return BiomeSky.tint(palette, false, sunAngleRad, rain);
	}

	private static int sampleForTime(int[] eveningColors, int[] dawnColors, float sunAngleRad) {
		float s = Math.max(-1.0F, Math.min(1.0F, (float) Math.cos(sunAngleRad)));
		if (Math.sin(sunAngleRad) > 0.0F) {
			// Evening holds its warm colours a little longer as the sun approaches the horizon.
			if (s < 0.50F) s *= 0.90F;
			return sampleAtElevation(eveningColors, s);
		}
		int daytime = sampleAtElevation(eveningColors, s);
		int dawn = sampleAtElevation(dawnColors, s);
		return mix(daytime, dawn, dawnStrength(sunAngleRad));
	}

	private static int sample(int[] colors, float sunAngleRad) {
		return sampleAtElevation(colors, Math.max(-1.0F, Math.min(1.0F, (float) Math.cos(sunAngleRad))));
	}

	private static int sampleAtElevation(int[] colors, float s) {
		int i = 0;
		while (s < S[i + 1]) i++; // s >= -1 == S[last], never overruns
		return mix(colors[i + 1], colors[i], (s - S[i + 1]) / (S[i] - S[i + 1]));
	}

	/** 0 outside dawn; strongest shortly before and after sunrise. */
	public static float dawnStrength(float sunAngleRad) {
		if (Math.sin(sunAngleRad) >= 0.0F) return 0.0F;
		float elevation = (float) Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, Math.cos(sunAngleRad)))));
		return smoothstep(-20.0F, -11.0F, elevation) * (1.0F - smoothstep(8.0F, 18.0F, elevation));
	}

	private static float smoothstep(float edge0, float edge1, float value) {
		float t = Math.max(0.0F, Math.min(1.0F, (value - edge0) / (edge1 - edge0)));
		return t * t * (3.0F - 2.0F * t);
	}

	/** Per-channel lerp, result always opaque. */
	public static int mix(int a, int b, float t) {
		int out = 0xFF000000;
		for (int shift = 16; shift >= 0; shift -= 8) {
			float ca = (a >> shift) & 0xFF, cb = (b >> shift) & 0xFF;
			out |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return out;
	}

	public static void main(String[] args) {
		check(sample(ZENITH, 0.0F) == 0xFF000000 + 0x3F86EE, "noon zenith");
		check(sample(HORIZON, (float) Math.PI) == 0xFF000000 + 0x16224C, "midnight horizon");
		float dawnAngle = (float) (Math.PI * 1.5);
		float duskAngle = (float) (Math.PI * 0.5);
		check(sampleForTime(HORIZON, DAWN_HORIZON, dawnAngle) != sampleForTime(HORIZON, DAWN_HORIZON, duskAngle), "dawn differs from dusk");
		check(dawnStrength(dawnAngle) > 0.9F && dawnStrength(duskAngle) == 0.0F, "dawn haze window is morning only");
		check(fog(0x123456, 0.0F, 1.0F) == 0xFF123456, "rain = vanilla");
		check(mix(0x000000, 0xFFFFFF, 0.5F) == 0xFF808080, "mix midpoint");
		for (float a = 0; a < 6.3F; a += 0.01F) { // full day sweep: no exceptions, always opaque
			check((sky(0x78A7FF, a, 0.0F) >>> 24) == 0xFF && (fog(0xC0D8FF, a, 0.3F) >>> 24) == 0xFF, "sweep " + a);
		}
		System.out.println("SkyPalette OK");
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
