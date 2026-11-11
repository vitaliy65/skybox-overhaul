package skybox.overhaul.client.sky;

import java.util.SplittableRandom;

/**
 * Meteor showers: on every Nth in-game day the night has many more comets and short shooting stars,
 * and they all fly away from one point of the sky (the radiant). Pure Java on purpose: self-checks with
 * java -ea skybox.overhaul.client.sky.MeteorShower
 */
public final class MeteorShower {
	public static final long DAY_TICKS = 24000L;

	private MeteorShower() {}

	/** The Nth day of the world counter (gameTime / 24000, the number F3 shows) is a shower day: N-1, 2N-1, ... */
	public static boolean isShowerDay(long gameTime, boolean enabled, int everyDays) {
		return enabled && everyDays > 0 && Math.floorMod(Math.floorDiv(gameTime, DAY_TICKS) + 1, everyDays) == 0;
	}

	/** Chance (0..100) that a shooting star launches in one slot; intensity is the 0..1 setting. */
	public static float meteorPercent(float intensity) {
		return 85.0F * clamp01(intensity);
	}

	/** Comet chance multiplier on shower nights. */
	public static float cometBoost(float intensity) {
		return 1.0F + 2.0F * clamp01(intensity);
	}

	/**
	 * Radiant of that day as a unit vector in the STAR frame (the frame NightSky draws in, it turns with the stars).
	 * ponytail: y < 0 means "opposite the sun's own spot", i.e. high overhead around midnight, if the star frame
	 * turns with the sun (vanilla). If it does not, the radiant is still there, just peaks at another hour.
	 */
	public static float[] radiant(long day) {
		SplittableRandom r = new SplittableRandom(day * 0x9E3779B97F4A7C15L + 0x51ED27L);
		float x = (float) (r.nextDouble() - 0.5) * 0.7F;
		float y = -(0.5F + 0.5F * (float) r.nextDouble());
		float z = (float) (r.nextDouble() - 0.5);
		return normalize(x, y, z);
	}

	/**
	 * Column-major 3x3 for NightSky: column 1 = the radiant, column 2 = a random flight direction perpendicular to it,
	 * column 0 = col1 x col2 (right-handed). Flying along rotateX(angle) then goes radiant*cos + direction*sin.
	 */
	public static float[] basis(long day, SplittableRandom r) {
		float[] up = radiant(day);
		float[] axis = Math.abs(up[0]) < 0.9F ? new float[] {1, 0, 0} : new float[] {0, 1, 0};
		float[] u = cross(up, axis);
		u = normalize(u[0], u[1], u[2]);
		float[] v = cross(up, u);
		double phi = r.nextDouble() * Math.PI * 2.0;
		float c = (float) Math.cos(phi), s = (float) Math.sin(phi);
		float[] d = {u[0] * c + v[0] * s, u[1] * c + v[1] * s, u[2] * c + v[2] * s};
		float[] x = cross(up, d);
		return new float[] {x[0], x[1], x[2], up[0], up[1], up[2], d[0], d[1], d[2]};
	}

	private static float[] cross(float[] a, float[] b) {
		return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static float[] normalize(float x, float y, float z) {
		float l = (float) Math.sqrt(x * x + y * y + z * z);
		return new float[] {x / l, y / l, z / l};
	}

	private static float clamp01(float v) {
		return Math.max(0.0F, Math.min(1.0F, v));
	}

	public static void main(String[] args) {
		check(isShowerDay(7 * DAY_TICKS + 13000L, true, 8), "8th day is a shower day");
		check(!isShowerDay(6 * DAY_TICKS, true, 8) && !isShowerDay(8 * DAY_TICKS, true, 8), "neighbours are not");
		check(isShowerDay(15 * DAY_TICKS, true, 8), "16th day");
		check(!isShowerDay(7 * DAY_TICKS, false, 8), "switch off");
		check(isShowerDay(0L, true, 1) && isShowerDay(5 * DAY_TICKS, true, 1), "every day");
		int days = 0;
		for (long d = 0; d < 800; d++) if (isShowerDay(d * DAY_TICKS, true, 8)) days++;
		check(days == 100, "1 day in 8");
		check(meteorPercent(0) == 0 && meteorPercent(1) == 85 && cometBoost(0) == 1 && cometBoost(1) == 3, "intensity");

		for (long day = 0; day < 200; day++) {
			float[] rad = radiant(day);
			check(Math.abs(len(rad) - 1.0F) < 1e-5F && rad[1] < -0.3F, "radiant unit + overhead side " + day);
			float[] again = radiant(day);
			check(again[0] == rad[0] && again[1] == rad[1] && again[2] == rad[2], "radiant stable for the day");
			SplittableRandom r = new SplittableRandom(day);
			for (int k = 0; k < 20; k++) {
				float[] b = basis(day, r);
				float[] x = {b[0], b[1], b[2]}, y = {b[3], b[4], b[5]}, z = {b[6], b[7], b[8]};
				check(Math.abs(len(x) - 1) < 1e-4F && Math.abs(len(y) - 1) < 1e-4F && Math.abs(len(z) - 1) < 1e-4F, "unit columns");
				check(Math.abs(dot(x, y)) < 1e-4F && Math.abs(dot(y, z)) < 1e-4F && Math.abs(dot(x, z)) < 1e-4F, "orthogonal");
				check(y[0] == rad[0] && y[1] == rad[1] && y[2] == rad[2], "column 1 is the radiant");
				float[] c = cross(x, y); // right-handed: x cross y = z
				check(Math.abs(c[0] - z[0]) < 1e-4F && Math.abs(c[1] - z[1]) < 1e-4F && Math.abs(c[2] - z[2]) < 1e-4F, "right-handed");
				// a meteor at flight angle 0 sits exactly on the radiant, later it moves away from it
				float a = 0.5F;
				float[] p = {y[0] * (float) Math.cos(a) + z[0] * (float) Math.sin(a), y[1] * (float) Math.cos(a) + z[1] * (float) Math.sin(a), y[2] * (float) Math.cos(a) + z[2] * (float) Math.sin(a)};
				check(Math.abs(dot(p, y) - (float) Math.cos(a)) < 1e-4F, "moves away from radiant by the flown angle");
			}
		}
		System.out.println("MeteorShower OK");
	}

	private static float len(float[] v) {
		return (float) Math.sqrt(dot(v, v));
	}

	private static float dot(float[] a, float[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
