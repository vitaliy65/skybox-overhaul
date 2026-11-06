package skybox.overhaul.client.aurora;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Aurora curtain fixed in world space: a chain of vertical rectangles ("rays") along a gently curved path.
 * Each rectangle has a vertical gradient (bright teal/green base -> blue/violet -> transparent top),
 * its own height/brightness/hue, and a brightness wave travelling along the chain.
 */
public final class AuroraRibbon {
	private static final float W = 5f; // rectangle width, blocks
	private final int n;
	private final double x0, z0;
	private final float dx, dz, curve, baseY, phase;
	private final float[] h, br, hue;

	public AuroraRibbon(Random r, double cellX, double cellZ, int cell) {
		x0 = cellX + r.nextDouble() * cell;
		z0 = cellZ + r.nextDouble() * cell;
		double a = r.nextDouble() * Math.PI * 2;
		dx = (float) Math.cos(a);
		dz = (float) Math.sin(a);
		n = 40 + r.nextInt(40);
		curve = 30 + r.nextFloat() * 50;
		baseY = 215 + r.nextFloat() * 30;   // above the cloud layer (y~192) so we never intersect clouds
		phase = r.nextFloat() * 100;
		h = new float[n]; br = new float[n]; hue = new float[n];
		float ribbonHue = r.nextFloat();     // 0 = teal/blue, 1 = green
		for (int i = 0; i < n; i++) {
			h[i] = 45 + r.nextFloat() * 55;
			br[i] = 0.35f + r.nextFloat() * 0.65f;
			hue[i] = Math.max(0f, Math.min(1f, ribbonHue + (r.nextFloat() - 0.5f) * 0.4f));
		}
	}

	public void render(VertexConsumer vc, Matrix4f m, double camX, double camY, double camZ, float t, float fade) {
		for (int i = 0; i < n - 1; i++) {
			float edge = (float) Math.sin(Math.PI * (i + 0.5) / n);
			float wave = 0.6f + 0.4f * (float) Math.sin(i * 0.4f - t * 1.2f + phase);
			float b = fade * edge * wave * br[i];
			if (b < 0.01f) continue;
			float[] p = point(i), q = point(i + 1);
			float height = h[i] * (0.8f + 0.2f * (float) Math.sin(t * 0.5f + i * 0.7f + phase));
			float y = baseY + 5f * (float) Math.sin(i * 0.2f + t * 0.2f + phase);
			float ax = (float) (p[0] - camX), az = (float) (p[1] - camZ);
			float bx = (float) (q[0] - camX), bz = (float) (q[1] - camZ);
			float ry = (float) (y - camY);
			// 2 gradient rows: base -> mid (0..0.3), mid -> top (0.3..1)
			row(vc, m, ax, az, bx, bz, ry, height, 0f, 0.3f, b, hue[i]);
			row(vc, m, ax, az, bx, bz, ry, height, 0.3f, 1f, b, hue[i]);
		}
	}

	private float[] point(int i) {
		float side = curve * (float) Math.sin(i * 0.06f + phase);
		return new float[]{(float) (x0 + dx * i * W - dz * side), (float) (z0 + dz * i * W + dx * side)};
	}

	private void row(VertexConsumer vc, Matrix4f m, float ax, float az, float bx, float bz, float y, float height,
					 float f0, float f1, float b, float hu) {
		float y0 = y + height * f0, y1 = y + height * f1;
		float[] c0 = color(f0, hu), c1 = color(f1, hu);
		// both windings so it is visible regardless of face culling
		for (int s = 0; s < 2; s++) {
			float x1 = s == 0 ? ax : bx, z1 = s == 0 ? az : bz, x2 = s == 0 ? bx : ax, z2 = s == 0 ? bz : az;
			float[] ca = c0, cb = c1;
			vc.addVertex(m, x1, y0, z1).setColor(ca[0], ca[1], ca[2], ca[3] * b);
			vc.addVertex(m, x2, y0, z2).setColor(ca[0], ca[1], ca[2], ca[3] * b);
			vc.addVertex(m, x2, y1, z2).setColor(cb[0], cb[1], cb[2], cb[3] * b);
			vc.addVertex(m, x1, y1, z1).setColor(cb[0], cb[1], cb[2], cb[3] * b);
		}
	}

	/** f: 0 = bottom edge, 1 = top. hu: 0 teal, 1 green. Returns r,g,b,a. */
	private static float[] color(float f, float hu) {
		float br = 0.30f, bg = 0.85f + 0.15f * hu, bb = 0.85f - 0.40f * hu;     // base: teal -> green
		if (f <= 0.3f) {
			float k = f / 0.3f;                                                  // base -> mid (blue/violet)
			return new float[]{lerp(br, 0.40f, k), lerp(bg, 0.45f, k), lerp(bb, 0.95f, k), lerp(0.9f, 0.55f, k)};
		}
		float k = (f - 0.3f) / 0.7f;                                             // mid -> violet, fading out
		return new float[]{lerp(0.40f, 0.35f, k), lerp(0.45f, 0.12f, k), lerp(0.95f, 0.70f, k), lerp(0.55f, 0f, k)};
	}

	private static float lerp(float a, float b, float k) { return a + (b - a) * k; }
}
