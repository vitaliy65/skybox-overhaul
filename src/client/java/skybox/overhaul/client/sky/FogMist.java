package skybox.overhaul.client.sky;

import skybox.overhaul.client.config.SkyboxConfig;

/**
 * How misty the terrain fog should be right now, 0..1: early morning (peaks at sunrise, gone by ~45 degrees sun height)
 * and after rain (wetness held at the peak, then fades over a few minutes). FogRendererMixin turns it into a longer fog ramp.
 * Pure Java: java -ea skybox.overhaul.client.sky.FogMist
 */
public final class FogMist {
	/** Ticks (5 min) for a fully wet ground to dry out. */
	private static final long DRY_TICKS = 6000L;

	private static float wet;
	private static long last = Long.MIN_VALUE;
	private static float current;

	private FogMist() {}

	/** Morning side only (the sun rises toward +X, sin(angle) <= 0): rises before sunrise, fades as the sun climbs. */
	public static float dawn(float sunAngleRad) {
		if (Math.sin(sunAngleRad) > 0.0) {
			return 0.0F; // evening
		}
		float elevation = (float) Math.toDegrees(Math.asin(clamp((float) Math.cos(sunAngleRad), -1.0F, 1.0F)));
		return smoothstep(-25.0F, -8.0F, elevation) * (1.0F - smoothstep(12.0F, 45.0F, elevation));
	}

	/** Call once per frame with the world clock; returns (and remembers) the strength after the user setting. */
	public static float strength(float sunAngleRad, long gameTime, float rainLevel) {
		float rain = clamp(rainLevel, 0.0F, 1.0F);
		if (last == Long.MIN_VALUE || gameTime < last) {
			wet = rain; // first frame, new world or time went backwards
		} else {
			wet = Math.max(rain, wet - (gameTime - last) / (float) DRY_TICKS);
		}
		last = gameTime;
		float afterRain = SkyboxConfig.morningFogAfterRain ? Math.max(0.0F, wet - rain) : 0.0F; // during rain vanilla fog is enough
		current = clamp(Math.max(dawn(sunAngleRad), afterRain), 0.0F, 1.0F) * SkyboxConfig.morningFog;
		return current;
	}

	/** Last value from {@link #strength}; the horizon haze reads it so the sky thickens together with the terrain fog. */
	public static float current() {
		return current;
	}

	/** Not in a sky dimension (or no world): no mist, forget the wetness. */
	public static void clear() {
		current = 0.0F;
		last = Long.MIN_VALUE;
		wet = 0.0F;
	}

	private static float smoothstep(float e0, float e1, float v) {
		float t = clamp((v - e0) / (e1 - e0), 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	public static void main(String[] args) {
		float sunrise = (float) (Math.PI * 1.5), sunset = (float) (Math.PI * 0.5);
		check(dawn(sunrise) > 0.99F, "full at sunrise");
		check(dawn(sunset) == 0.0F && dawn(0.0F) == 0.0F && dawn((float) Math.PI) == 0.0F, "no mist evening, noon, midnight");
		// sweep one morning: rises, peaks, then falls and never jumps
		float prev = 0.0F, peak = 0.0F;
		boolean falling = false;
		for (float a = (float) Math.PI; a < 2.0F * (float) Math.PI + 1.6F; a += 0.001F) {
			if (Math.sin(a) > 0.0 && a > 2.0F * Math.PI) break;
			float d = dawn(a);
			check(Math.abs(d - prev) < 0.01F, "mist jumps at " + a);
			if (d < peak - 1e-4F) falling = true;
			if (falling) check(d <= prev + 1e-4F, "mist re-rises after the peak");
			peak = Math.max(peak, d);
			prev = d;
		}
		check(peak > 0.99F, "morning peak");
		check(dawn((float) (2.0 * Math.PI - Math.acos(Math.sin(Math.toRadians(50.0))))) < 1e-4F, "gone at 50 degrees");

		SkyboxConfig.reset();
		SkyboxConfig.morningFog = 1.0F;
		clear();
		check(strength(0.0F, 1000L, 0.0F) == 0.0F, "noon, dry: clear");
		strength(0.0F, 2000L, 1.0F); // heavy rain at noon: still no extra mist while it pours
		check(current() == 0.0F, "no mist during rain");
		float m0 = strength(0.0F, 2001L, 0.0F); // rain stopped at once
		check(m0 > 0.99F, "mist right after rain " + m0);
		float m1 = strength(0.0F, 2001L + 3000L, 0.0F);
		check(m1 > 0.45F && m1 < 0.55F, "half way dry " + m1);
		check(strength(0.0F, 2001L + 6000L, 0.0F) < 1e-4F, "dry again");
		SkyboxConfig.morningFogAfterRain = false;
		clear();
		strength(0.0F, 2000L, 1.0F);
		check(strength(0.0F, 2001L, 0.0F) == 0.0F, "after-rain switch off");
		SkyboxConfig.morningFogAfterRain = true;
		SkyboxConfig.morningFog = 0.0F;
		check(strength(sunrise, 5000L, 0.0F) == 0.0F, "strength 0 = off");
		SkyboxConfig.morningFog = 0.5F;
		check(Math.abs(strength(sunrise, 5001L, 0.0F) - 0.5F) < 1e-3F, "scaled by the setting");
		check(strength(sunrise, 100L, 0.0F) >= 0.0F, "time going backwards is fine");
		SkyboxConfig.reset();
		System.out.println("FogMist OK");
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
