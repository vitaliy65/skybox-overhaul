package skybox.overhaul.client.cloud;

import skybox.overhaul.client.config.SkyboxConfig;

/**
 * Smooth cloud speed. Instead of scaling absolute world time (which jumps when the slider moves and steps back
 * and forth for speed < 1), the cloud clock is shifted by (speed - 1) for every tick that really elapsed.
 * Speed 1 = vanilla, 0 = frozen. Pure Java, no Minecraft imports.
 */
public final class CloudMotion {
	private static double extra;
	private static double last = Double.NaN;
	private static long seenTick;
	private static float seenPartial;

	private CloudMotion() {}

	/** The mixin reports the vanilla (untouched) clock of the main cloud pass. */
	public static void seenTick(long gameTime) {
		seenTick = gameTime;
	}

	public static void seenPartial(float partialTicks) {
		seenPartial = partialTicks;
	}

	/** Once per frame, from LevelRenderer#endFrame: the shift stays constant for the whole frame. */
	public static void advance() {
		double now = seenTick + seenPartial;
		double dt = now - last;
		last = now;
		if (dt > 0.0 && dt <= 40.0) extra += dt * (SkyboxConfig.cloudSpeed - 1.0); // NaN, rewind, world switch: resync only
	}

	public static long time(long gameTime) {
		return gameTime + (long) Math.floor(extra);
	}

	public static float partial(float partialTicks) {
		return partialTicks + (float) (extra - Math.floor(extra));
	}

	public static void main(String[] args) {
		for (float speed : new float[] {0.0F, 0.5F, 1.0F, 1.5F, 3.0F}) {
			SkyboxConfig.cloudSpeed = speed;
			extra = 0;
			last = Double.NaN;
			double prev = Double.NaN;
			for (long n = 1000; n < 1400; n++) {
				for (int f = 0; f < 3; f++) {
					float pt = f / 3.0F;
					seenTick(n);
					seenPartial(pt);
					double pos = time(n) + partial(pt); // what the cloud shader integrates
					advance();
					if (n > 1000) { // first frames: the shift is applied one frame late (advance runs in endFrame)
						check(pos - prev >= -1e-3, "never moves backwards, speed " + speed);
						check(Math.abs((pos - prev) - speed / 3.0) < 2e-3, "constant step, speed " + speed);
					}
					prev = pos;
				}
			}
			// slider moved: positions stay continuous, only the rate changes from the next frame on
			SkyboxConfig.cloudSpeed = speed == 3.0F ? 0.0F : 3.0F;
			for (int f = 0; f < 6; f++) {
				seenTick(1400 + f / 3);
				seenPartial((f % 3) / 3.0F);
				double pos = time(1400 + f / 3) + partial((f % 3) / 3.0F);
				advance();
				check(Math.abs(pos - prev) <= 1.0 + 1e-3, "no jump when the slider moves, speed " + speed);
				prev = pos;
			}
		}
		SkyboxConfig.cloudSpeed = 1.0F;
		extra = 0;
		last = Double.NaN;
		seenTick(5000);
		seenPartial(0.5F);
		advance();
		seenTick(1); // world switch: no accumulation
		advance();
		check(time(77) == 77 && partial(0.25F) == 0.25F, "speed 1 = vanilla");
		System.out.println("CloudMotion OK");
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
