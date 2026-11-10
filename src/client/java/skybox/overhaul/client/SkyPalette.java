package skybox.overhaul.client;

/**
 * Sun height + solar side -> sky colours. s = sin(sun elevation) = cos(vanilla sun angle):
 * 1 noon, 0 horizon, -1 midnight. Horizon colour feeds fog and zenith colour feeds the sky disc.
 * Sunrise has its own cool palette; sunset keeps the warmer palette for a wider elevation range.
 * Pure Java on purpose: self-check with  java -ea SkyPalette.java
 */
public final class SkyPalette {
	private static final float[] S = {1.00F, 0.50F, 0.30F, 0.15F, 0.05F, -0.02F, -0.09F, -0.18F, -0.32F, -1.00F};
	private static final int[] HORIZON = {0xBFDDFF, 0xCFE3FA, 0xFFDCA0, 0xFFC25A, 0xFF9E2A, 0xFF6A1E, 0xE0455E, 0x9A3A8C, 0x3F3A86, 0x16224C};
	private static final int[] ZENITH = {0x3F86EE, 0x4F90EE, 0x6B93E0, 0xA874C0, 0xB93A5E, 0x92306A, 0x5A2B7A, 0x2C2468, 0x0E1233, 0x050818};

	// East-facing dawn: pale blue overhead, rose and lavender close to the horizon.
	// The day and deep-night endpoints intentionally match the normal palette.
	private static final int[] DAWN_HORIZON = {
		0xBFDDFF, 0xCFE3FA, 0xDDE9FA, 0xE9D2E8, 0xF1BBD3,
		0xDCA2C9, 0xAE91C2, 0x7184B5, 0x3F5A91, 0x16224C
	};
	private static final int[] DAWN_ZENITH = {
		0x3F86EE, 0x4F90EE, 0x6D93D9, 0x849BD7, 0x9CA4DF,
		0x8F93CD, 0x6A75B1, 0x43578F, 0x202F63, 0x050818
	};

	private SkyPalette() {}

	/** Fog colour: vanilla blended toward the directional palette's horizon colour. */
	public static int fog(int vanilla, float sunAngleRad, float rain) {
		return mix(vanilla, sampleForTime(HORIZON, DAWN_HORIZON, sunAngleRad), SkyboxConfig.paletteStrength * (1.0F - rain));
	}

	/** Sky disc colour: vanilla blended toward the directional palette's zenith colour. */
	public static int sky(int vanilla, float sunAngleRad, float rain) {
		return mix(vanilla, sampleForTime(ZENITH, DAWN_ZENITH, sunAngleRad), SkyboxConfig.paletteStrength * (1.0F - rain));
	}

	/** 0..1 dawn haze boost, strongest while the sun is just below/above the eastern horizon. */
	public static float dawnStrength(float sunAngleRad) {
		if (Math.sin(sunAngleRad) > 0.0F) return 0.0F; // west = evening
		float elevation = (float) Math.toDegrees(Math.asin(clamp((float) Math.cos(sunAngleRad), -1.0F, 1.0F)));
		return smoothstep(-20.0F, -11.0F, elevation) * (1.0F - smoothstep(8.0F, 18.0F, elevation));
	}

	private static int sampleForTime(int[] sunsetColors, int[] dawnColors, float sunAngleRad) {
		float s = clamp((float) Math.cos(sunAngleRad), -1.0F, 1.0F);
		if (Math.sin(sunAngleRad) > 0.0F) {
			// Compress the effective solar elevation around the horizon: warm colours start earlier
			// and the orange-to-violet afterglow remains until the sun is farther below the horizon.
			if (s < 0.50F) s *= 0.90F;
			return sample(sunsetColors, s);
		}

		int normal = sample(sunsetColors, s);
		float dawn = dawnStrength(sunAngleRad);
		return dawn <= 0.0F ? normal : mix(normal, sample(dawnColors, s), dawn);
	}

	private static int sample(int[] colors, float s) {
		int i = 0;
		while (s < S[i + 1]) i++; // s >= -1 == S[last], never overruns
		return mix(colors[i + 1], colors[i], (s - S[i + 1]) / (S[i] - S[i + 1]));
	}

	/** Per-channel lerp, result always opaque. */
	static int mix(int a, int b, float t) {
		t = clamp(t, 0.0F, 1.0F);
		int out = 0xFF000000;
		for (int shift = 16; shift >= 0; shift -= 8) {
			float ca = (a >> shift) & 0xFF, cb = (b >> shift) & 0xFF;
			out |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return out;
	}

	private static float smoothstep(float edge0, float edge1, float value) {
		float t = clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	public static void main(String[] args) {
		check(sample(ZENITH, 1.0F) == 0xFF3F86EE, "noon zenith");
		check(sample(HORIZON, -1.0F) == 0xFF16224C, "midnight horizon");
		check(fog(0x123456, 0.0F, 1.0F) == 0xFF123456, "rain = vanilla");
		check(mix(0x000000, 0xFFFFFF, 0.5F) == 0xFF808080, "mix midpoint");
		float sunrise = (float) (Math.PI * 1.5);
		float sunset = (float) (Math.PI * 0.5);
		check(dawnStrength(sunrise) > 0.9F, "dawn mist around sunrise");
		check(dawnStrength(sunset) == 0.0F, "no dawn mist at sunset");
		check(sampleForTime(HORIZON, DAWN_HORIZON, sunrise) != sampleForTime(HORIZON, DAWN_HORIZON, sunset), "dawn differs from sunset");
		for (float a = 0; a < 6.3F; a += 0.01F) {
			check((sky(0x78A7FF, a, 0.0F) >>> 24) == 0xFF && (fog(0xC0D8FF, a, 0.3F) >>> 24) == 0xFF, "sweep " + a);
		}
		System.out.println("SkyPalette OK");
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
