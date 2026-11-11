package skybox.overhaul.client.sky;

import java.util.SplittableRandom;

/**
 * Silent far-away lightning: while it rains, short double/triple flashes light up a random stretch of the horizon.
 * Stateless like the comets: a flash is fully defined by its slot number, so reloads and time skips stay consistent.
 * Pure Java: java -ea skybox.overhaul.client.sky.DistantStorm
 */
public final class DistantStorm {
	/** Ticks per spawn roll (5 s). */
	public static final long SLOT = 100L;
	/** A flash is over this many ticks after it starts (last pulse at most 17 ticks in, decay to ~1% takes ~12 more). */
	private static final float LIFE = 30.0F;
	private static final int SLOTS_BACK = (int) Math.ceil(LIFE / SLOT) + 1;

	private DistantStorm() {}

	/** Chance (0..1) per slot: the frequency setting, scaled in only once the rain is properly on. */
	public static float chance(float frequency, float rainLevel) {
		float t = Math.max(0.0F, Math.min(1.0F, (rainLevel - 0.3F) / 0.6F));
		return Math.max(0.0F, Math.min(1.0F, frequency)) * 0.9F * t * t * (3.0F - 2.0F * t);
	}

	/** Fills azimuth (radians, turn about Y) and power (0..1) of every flash alive now, up to az.length; returns the count. */
	public static int flashes(double ticks, float chance, float[] az, float[] power) {
		int n = 0;
		long now = Math.floorDiv((long) ticks, SLOT);
		for (long slot = now; slot > now - SLOTS_BACK && n < az.length; slot--) {
			SplittableRandom r = new SplittableRandom(slot * 0x9E3779B97F4A7C15L ^ 0x7F4A7C15L);
			double roll = r.nextDouble(); // compared with the live setting, so sliders work instantly
			float start = (float) (r.nextDouble() * SLOT);
			float azimuth = (float) (r.nextDouble() * Math.PI * 2.0);
			float gap1 = 4.0F + (float) (r.nextDouble() * 5.0);
			float gap2 = 3.0F + (float) (r.nextDouble() * 5.0);
			float amp1 = 0.6F + 0.3F * (float) r.nextDouble();
			float amp2 = 0.35F + 0.3F * (float) r.nextDouble();
			boolean third = r.nextDouble() < 0.6;
			if (roll >= chance) {
				continue;
			}
			float t = (float) (ticks - slot * SLOT - start);
			float p = pulse(t) + amp1 * pulse(t - gap1) + (third ? amp2 * pulse(t - gap1 - gap2) : 0.0F);
			p = Math.min(1.0F, p);
			if (p > 0.01F) {
				az[n] = azimuth;
				power[n] = p;
				n++;
			}
		}
		return n;
	}

	/** Near-instant rise, quick exponential decay: one stroke of light. */
	private static float pulse(float t) {
		return t < 0.0F ? 0.0F : (float) (Math.min(1.0F, t / 0.6F) * Math.exp(-Math.max(0.0F, t - 0.6F) / 2.5F)); // peaks at 1 after 0.6 ticks
	}

	public static void main(String[] args) {
		check(chance(1.0F, 0.0F) == 0.0F && chance(1.0F, 0.3F) == 0.0F, "dry = no flashes");
		check(Math.abs(chance(1.0F, 1.0F) - 0.9F) < 1e-6F && chance(0.0F, 1.0F) == 0.0F, "chance scale");
		float[] az = new float[2], pw = new float[2];
		check(flashes(1000.0, 0.0F, az, pw) == 0, "zero chance");
		int flashTicks = 0, total = 0;
		float peak = 0.0F;
		float prev = 0.0F;
		float maxStep = 0.0F;
		for (int t = 0; t < 200_000; t++) { // sub-tick steps, like partial ticks
			int n = flashes(t * 0.25, 0.9F, az, pw);
			total++;
			float sum = 0.0F;
			for (int i = 0; i < n; i++) {
				check(az[i] >= 0.0F && az[i] < 6.2832F && pw[i] > 0.0F && pw[i] <= 1.0F, "flash values");
				sum = Math.max(sum, pw[i]);
			}
			if (n > 0) flashTicks++;
			peak = Math.max(peak, sum);
			maxStep = Math.max(maxStep, Math.abs(sum - prev));
			prev = sum;
		}
		float share = (float) flashTicks / total;
		check(share > 0.03F && share < 0.2F, "flash on-time share " + share); // at 90%: lit ~ 10% of the time
		check(peak > 0.95F, "some flashes peak");
		check(maxStep < 0.75F, "no single-step teleport beyond the sharp strike " + maxStep);
		// stateless: the same time gives the same flashes
		int a = flashes(54321.5, 0.9F, az, pw);
		float a0 = az[0];
		check(flashes(54321.5, 0.9F, az, pw) == a && az[0] == a0, "stateless");
		System.out.println("DistantStorm OK");
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
