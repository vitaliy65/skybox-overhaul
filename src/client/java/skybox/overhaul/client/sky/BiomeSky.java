package skybox.overhaul.client.sky;

import skybox.overhaul.client.config.SkyboxConfig;

/**
 * Per-biome sky tint on top of the time-of-day palette (SkyPalette). Each biome group has a daytime horizon and
 * zenith colour; the tint is a gentle blend toward it (between vanilla and real life, never cartoon colours),
 * strongest at noon and gone at night, in rain and with paletteStrength 0 (= vanilla sky).
 * The tint eases toward the current biome over a few seconds, so crossing a border never snaps.
 * Pure Java on purpose (the biome lookup lives in BiomeGroups): self-check with  java -ea BiomeSky.java
 */
public final class BiomeSky {
	/** Daytime horizon / zenith colours. Soft and hazy: closer to a photo than to a paint bucket. */
	public enum Group {
		NONE(0x000000, 0x000000),     // no tint: plain palette
		DESERT(0xE8D9B8, 0x5C8FD8),   // dusty, creamy horizon, paler blue
		BADLANDS(0xE6C6A2, 0x5A8CD6), // a little redder dust
		SAVANNA(0xF1E1BE, 0x4F8CDE),  // golden haze
		JUNGLE(0xCFE6D8, 0x3F8FD0),   // humid pale green-teal, saturated zenith
		SWAMP(0xBFCFBE, 0x5F8AB0),    // grey-green mist, slate zenith
		SNOW(0xDDE8F4, 0x6F9CDC),     // crisp, white-blue
		OCEAN(0xC4DCF2, 0x3A82DA),    // clean deep blue
		CHERRY(0xF2DDE6, 0x6A92DE);   // faint pink

		final int horizon;
		final int zenith;

		Group(int horizon, int zenith) {
			this.horizon = horizon;
			this.zenith = zenith;
		}
	}

	// ===== EDIT BIOME SKY HERE =====
	/** How far the sky moves toward the biome colour at full daylight (0..1). Raise for stronger biome looks. */
	private static final float STRENGTH = 0.5F;
	/** Time constant of the biome-border fade, seconds (about 3x this to settle). */
	private static final float EASE_SECONDS = 1.5F;
	// ===============================

	private static final float[] horizon = new float[3]; // eased rgb, 0..255
	private static final float[] zenith = new float[3];
	private static float amount; // eased 0..1: 1 inside a tinted group, 0 in a plain biome
	private static long lastNanos;
	private static boolean started;

	private BiomeSky() {}

	/** Call once per frame with the group of the camera's biome (render thread). */
	public static void update(Group target) {
		long now = System.nanoTime();
		float k = started ? 1.0F - (float) Math.exp(-Math.min(0.25F, (now - lastNanos) / 1.0e9F) / EASE_SECONDS) : 1.0F;
		lastNanos = now;
		started = true;
		step(target, k);
	}

	/** One easing step, k = 0..1 (1 = jump). Plain biome: fade out, keep the old colours so nothing shifts while fading. */
	static void step(Group target, float k) {
		if (target == Group.NONE) {
			amount += (0.0F - amount) * k;
			return;
		}
		if (amount < 0.01F) { // coming from nothing: start at the new colours instead of drifting through old ones
			set(horizon, target.horizon);
			set(zenith, target.zenith);
		} else {
			ease(horizon, target.horizon, k);
			ease(zenith, target.zenith, k);
		}
		amount += (1.0F - amount) * k;
	}

	/** Palette colour with the biome tint blended in; the colour itself when there is no tint to apply. */
	public static int tint(int color, boolean isHorizon, float sunAngleRad, float rain) {
		float s = Math.max(-1.0F, Math.min(1.0F, (float) Math.cos(sunAngleRad)));
		float w = amount * STRENGTH * SkyboxConfig.paletteStrength * daylight(s) * (1.0F - rain);
		if (w <= 0.0005F) {
			return color;
		}
		float[] c = isHorizon ? horizon : zenith;
		int target = Math.round(c[0]) << 16 | Math.round(c[1]) << 8 | Math.round(c[2]);
		return SkyPalette.mix(color, target, w);
	}

	/** 0 at night and around sunset (the glow layers own that time), 1 in full day. s = sin(sun elevation). */
	static float daylight(float s) {
		float t = Math.max(0.0F, Math.min(1.0F, (s - 0.05F) / 0.35F));
		return t * t * (3.0F - 2.0F * t);
	}

	private static void set(float[] c, int rgb) {
		c[0] = rgb >> 16 & 0xFF;
		c[1] = rgb >> 8 & 0xFF;
		c[2] = rgb & 0xFF;
	}

	private static void ease(float[] c, int rgb, float k) {
		c[0] += ((rgb >> 16 & 0xFF) - c[0]) * k;
		c[1] += ((rgb >> 8 & 0xFF) - c[1]) * k;
		c[2] += ((rgb & 0xFF) - c[2]) * k;
	}

	public static void main(String[] args) {
		float noon = 0.0F, midnight = (float) Math.PI, sunset = (float) Math.PI / 2;
		int base = 0x80A0C0;
		check(tint(base, true, noon, 0.0F) == base, "no tint before any biome");

		step(Group.DESERT, 1.0F);
		int day = tint(base, true, noon, 0.0F);
		check(day != base && (day >>> 24) == 0xFF, "desert tints the noon horizon, opaque");
		check(tint(base, true, midnight, 0.0F) == base && tint(base, false, midnight, 0.0F) == base, "no tint at night");
		check(tint(base, true, sunset, 0.0F) == base, "no tint at sunset (glow layers own it)");
		check(tint(base, true, noon, 1.0F) == base, "no tint in full rain");
		float old = SkyboxConfig.paletteStrength;
		SkyboxConfig.paletteStrength = 0.0F;
		check(tint(base, true, noon, 0.0F) == base, "paletteStrength 0 = vanilla");
		SkyboxConfig.paletteStrength = old;

		// tint stays gentle: never moves a channel by more than STRENGTH of the distance to the group colour
		for (Group g : Group.values()) {
			if (g == Group.NONE) continue;
			step(g, 1.0F);
			for (int c : new int[]{0x000000, 0x80A0C0, 0xFFFFFF}) {
				int out = tint(c, true, noon, 0.0F);
				for (int sh = 16; sh >= 0; sh -= 8) {
					float d = Math.abs(((out >> sh) & 0xFF) - ((c >> sh) & 0xFF));
					float max = Math.abs(((g.horizon >> sh) & 0xFF) - ((c >> sh) & 0xFF)) * STRENGTH * SkyboxConfig.paletteStrength;
					check(d <= max + 1.0F, g + " gentle");
				}
			}
		}

		// easing: leaving a tinted biome fades smoothly, no jump; entering a new one drifts, no jump
		step(Group.SNOW, 1.0F);
		int a = tint(base, false, noon, 0.0F);
		step(Group.NONE, 0.1F);
		int b = tint(base, false, noon, 0.0F);
		check(b != base && b != a, "fading, not snapping");
		for (int i = 0; i < 200; i++) step(Group.NONE, 0.1F);
		check(tint(base, false, noon, 0.0F) == base, "fully faded");
		step(Group.OCEAN, 1.0F);
		int o1 = tint(base, true, noon, 0.0F);
		step(Group.JUNGLE, 0.1F);
		int o2 = tint(base, true, noon, 0.0F);
		check(o1 != o2 && Math.abs(((o1 >> 8) & 0xFF) - ((o2 >> 8) & 0xFF)) < 8, "border drift is small per step");
		for (float x = -1.0F; x <= 1.0F; x += 0.01F) {
			check(daylight(x) >= 0.0F && daylight(x) <= 1.0F, "daylight range");
		}
		System.out.println("BiomeSky OK");
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
