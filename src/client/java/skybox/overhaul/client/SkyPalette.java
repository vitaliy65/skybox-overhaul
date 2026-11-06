package skybox.overhaul.client;

/**
 * Sun height -> sky colours. s = sin(sun elevation) = cos(vanilla sun angle):
 * 1 noon, 0 horizon, -1 midnight. Horizon colour feeds fog (so terrain and sky meet),
 * zenith colour feeds the sky disc; vanilla fog blends one into the other = the gradient.
 * Pure Java on purpose: self-check with  java -ea SkyPalette.java
 */
public final class SkyPalette {
	// descending s: noon, day, afternoon, golden hour, sunset, horizon, rose, purple dusk, indigo, night
	private static final float[] S       = {1.00F,    0.50F,    0.30F,    0.15F,    0.05F,    -0.02F,   -0.09F,   -0.18F,   -0.32F,   -1.00F};
	private static final int[] HORIZON   = {0xBFDDFF, 0xCFE3FA, 0xFFE7C4, 0xFFD39A, 0xFFAE6B, 0xFF7F5A, 0xE0657F, 0x8F4F99, 0x3A3F82, 0x16224C};
	private static final int[] ZENITH    = {0x3F86EE, 0x4F90EE, 0x6A98E4, 0x7A9AD8, 0x7B7FC9, 0x5E5FAE, 0x3F3F8F, 0x24265E, 0x0E1233, 0x050818};

	private SkyPalette() {}

	/** Fog colour: vanilla blended toward the palette horizon colour. */
	public static int fog(int vanilla, float sunAngleRad, float rain) {
		return mix(vanilla, sample(HORIZON, sunAngleRad), SkyboxConfig.paletteStrength * (1.0F - rain));
	}

	/** Sky disc colour: vanilla blended toward the palette zenith colour. */
	public static int sky(int vanilla, float sunAngleRad, float rain) {
		return mix(vanilla, sample(ZENITH, sunAngleRad), SkyboxConfig.paletteStrength * (1.0F - rain));
	}

	private static int sample(int[] colors, float sunAngleRad) {
		float s = Math.max(-1.0F, Math.min(1.0F, (float) Math.cos(sunAngleRad)));
		int i = 0;
		while (s < S[i + 1]) i++; // s >= -1 == S[last], never overruns
		return mix(colors[i + 1], colors[i], (s - S[i + 1]) / (S[i] - S[i + 1]));
	}

	/** Per-channel lerp, result always opaque. */
	static int mix(int a, int b, float t) {
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
