package skybox.overhaul.client;

/**
 * Pure-Java data + maths for the twilight glow (no Minecraft imports, so it self-checks:
 * java -ea GlowLayers.java). Five stacked translucent bands, each with its own colour ramp, width around
 * the sun and a time window (sun elevation) in which it is visible:
 * gold core -> orange -> pink -> violet dome, plus the "Belt of Venus" (pink band opposite the sun).
 */
public final class GlowLayers {
	/** Flip if the glow shows up on the wrong side of the sky (vanilla: sun sets toward -X, rises toward +X). */
	public static final boolean FLIP_SIDE = false;
	/** Azimuth columns of the mesh (360 / 5 deg). */
	public static final int COLUMNS = 72;

	/** sigma: width (deg) around the sun (or around the anti-sun point if anti). time: sun elevation deg {lo, peak, hi}. */
	public record Layer(float sigma, boolean anti, float[] time, float[] elev, int[] rgb, float[] alpha) {}

	public static final Layer[] ALL = {
		// gold core, hugging the sun: hot yellow-orange
		new Layer(40, false, new float[]{-10, 1, 16},
			new float[]{-10, -1, 3, 10, 24}, new int[]{0xFFC030, 0xFFD040, 0xFFC838, 0xFFB030, 0xFF9A30}, new float[]{0, 0.95F, 0.85F, 0.55F, 0}),
		// deep orange wash, wider and taller
		new Layer(80, false, new float[]{-11, -1, 11},
			new float[]{-10, 0, 8, 18, 38}, new int[]{0xFF6A00, 0xFF6A00, 0xFF5E10, 0xFF5028, 0xF03C48}, new float[]{0, 0.90F, 0.78F, 0.60F, 0}),
		// crimson -> magenta, climbs high
		new Layer(115, false, new float[]{-15, -5, 3},
			new float[]{-5, 2, 12, 26, 45}, new int[]{0xE0305E, 0xE0305E, 0xD02A70, 0xA82A82, 0x80309A}, new float[]{0, 0.55F, 0.80F, 0.55F, 0}),
		// violet dome, almost all around the sky
		new Layer(150, false, new float[]{-19, -9, -1},
			new float[]{0, 15, 32, 52, 70}, new int[]{0x7A3CB8, 0x7A3CB8, 0x6840B8, 0x4C44B0, 0x3C46AC}, new float[]{0, 0.40F, 0.60F, 0.35F, 0}),
		// Belt of Venus: blue earth-shadow below, pink band above, centred opposite the sun
		new Layer(75, true, new float[]{-12, -5, 1},
			new float[]{-4, 0, 4, 9, 16, 26}, new int[]{0x4A5A98, 0x4A5A98, 0x7A6AA8, 0xF0A0BC, 0xF4A8C0, 0xF0A0BC}, new float[]{0, 0.55F, 0.55F, 0.55F, 0.30F, 0}),
	};

	private GlowLayers() {}

	/** Sun elevation in degrees from vanilla sun angle (radians; 0 = noon, PI = midnight). */
	public static float sunElevationDeg(float sunAngleRad) {
		return (float) Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, Math.cos(sunAngleRad)))));
	}

	/** True in the afternoon (sun sets toward -X), false in the morning (sun rises toward +X). */
	public static boolean sunInWest(float sunAngleRad) {
		return Math.sin(sunAngleRad) > 0.0;
	}

	/** 0..1 visibility of a layer for a given sun elevation: smoothstep up to peak, smoothstep down to hi. */
	public static float weight(Layer l, float sunElevDeg) {
		float lo = l.time()[0], pk = l.time()[1], hi = l.time()[2];
		if (sunElevDeg <= lo || sunElevDeg >= hi) {
			return 0.0F;
		}
		float t = sunElevDeg < pk ? (sunElevDeg - lo) / (pk - lo) : (hi - sunElevDeg) / (hi - pk);
		return t * t * (3.0F - 2.0F * t) * SkyboxConfig.sunsetIntensity;
	}

	/** Elevation rows of a layer's mesh: every ~2.5 deg between its first and last keyframe. */
	public static float[] rows(Layer l) {
		float a = l.elev()[0], b = l.elev()[l.elev().length - 1];
		int n = Math.max(1, Math.round((b - a) / 2.5F));
		float[] r = new float[n + 1];
		for (int i = 0; i <= n; i++) {
			r[i] = a + (b - a) * i / n;
		}
		return r;
	}

	/** Baked vertex colour (ARGB) at elevation e, azimuth offset az (deg from the sun's azimuth, -180..180). */
	public static int argb(Layer l, float e, float az) {
		float[] E = l.elev();
		e = Math.max(E[0], Math.min(E[E.length - 1], e));
		int i = 0;
		while (i < E.length - 2 && e > E[i + 1]) i++;
		float t = (e - E[i]) / (E[i + 1] - E[i]);
		t = t * t * (3.0F - 2.0F * t);
		float d = Math.abs(az);
		d = l.anti() ? 180.0F - d : d;
		float h = (float) Math.exp(-(d / l.sigma()) * (d / l.sigma()));
		float a = (l.alpha()[i] + (l.alpha()[i + 1] - l.alpha()[i]) * t) * h;
		return (Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24) | (SkyPalette.mix(l.rgb()[i], l.rgb()[i + 1], t) & 0xFFFFFF);
	}

	public static void main(String[] args) {
		check(Math.abs(sunElevationDeg(0.0F) - 90.0F) < 1e-3F, "noon = 90 deg");
		check(Math.abs(sunElevationDeg((float) Math.PI / 2)) < 1e-3F, "sunset = 0 deg");
		check(sunInWest((float) Math.PI / 2) && !sunInWest(3.0F * (float) Math.PI / 2), "afternoon west, morning east");
		for (Layer l : ALL) {
			check(l.elev().length == l.rgb().length && l.elev().length == l.alpha().length, "keyframe arrays");
			check(weight(l, 60.0F) == 0.0F && weight(l, -60.0F) == 0.0F, "invisible at noon and midnight");
			check(Math.abs(weight(l, l.time()[1]) - SkyboxConfig.sunsetIntensity) < 1e-4F, "peak weight");
			float[] r = rows(l);
			check(r[0] == l.elev()[0] && Math.abs(r[r.length - 1] - l.elev()[l.elev().length - 1]) < 1e-3F, "rows span keyframes");
			for (float e : r) {
				for (float az = -180; az <= 180; az += 5) {
					int c = argb(l, e, az);
					check((c >>> 24) <= 255, "alpha range");
				}
			}
			check((argb(l, l.elev()[0], 0) >>> 24) == 0 && (argb(l, l.elev()[l.elev().length - 1], 0) >>> 24) == 0, "edges fade to 0");
		}
		check((argb(ALL[0], 0.0F, 0.0F) >>> 24) > 150 && (argb(ALL[0], 0.0F, 170.0F) >>> 24) < 5, "gold near sun only");
		check((argb(ALL[4], 8.0F, 180.0F) >>> 24) > 100 && (argb(ALL[4], 8.0F, 0.0F) >>> 24) < 10, "venus belt opposite sun only");
		System.out.println("GlowLayers OK");
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
