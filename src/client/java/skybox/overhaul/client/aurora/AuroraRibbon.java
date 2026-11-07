package skybox.overhaul.client.aurora;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import skybox.overhaul.client.SkyboxConfig;

/** Sparse aurora made from vertical prisms on an eight-direction square grid. */
public final class AuroraRibbon {
	// ======================== EDIT AURORA HERE ========================
	// Cell size and pillar height are live sliders in Mod Menu (SkyboxConfig).
	public static final int PATCH_CELLS = 128; // Patch width/depth, measured in grid cells.

	// Every patch always generates this many snakes; the "Ribbon frequency" setting decides how many are shown
	// (each snake has a fixed random threshold, so the slider works live and the layout never reshuffles).
	private static final int MAX_RIBBONS = 8;
	private static final int MIN_CHAIN_LENGTH = 38;
	private static final int MAX_CHAIN_LENGTH = 72;
	private static final int EDGE_MARGIN = 2;
	private static final int START_ATTEMPTS = 48;
	private static final int MAX_DIRECTION_DEVIATION = 2; // Up to 90 degrees from the chain's overall direction.
	private static final int MAX_SIDEWAYS_STEPS = 2; // Prevent long sideways runs.

	private static final float BASE_Y = 215.0f;
	private static final float BASE_Y_VARIATION = 25.0f;
	private static final float TOP_FADE_START = 0.001f; // 0 = bottom, 1 = top.
	private static final float FADE_STRENGTH = 2.0f; // 0 = no fade, 1 = fully transparent at the top.
	private static final float BASE_ALPHA = 0.85f;
	private static final int COLOR_BANDS = 6;
	private static final float CYAN_POSITION = 0.10f; // Full cyan at 10% height.
	private static final float VIOLET_POSITION = 0.50f; // Full violet at 20%; remains to the top.
	private static final float[] VERTICAL_STOPS = verticalStops();

	// Level of detail by distance: far rays are small on screen, so they get fewer gradient bands (fewer vertices).
	private static final float[] STOPS_FULL = dedupe(VERTICAL_STOPS);
	private static final float[] STOPS_MID = {0.0f, CYAN_POSITION, 0.3f, VIOLET_POSITION, 0.75f, 1.0f};
	private static final float[] STOPS_FAR = {0.0f, CYAN_POSITION, VIOLET_POSITION, 1.0f};
	private static final float LOD_MID_DISTANCE = 1200.0f; // blocks, horizontal
	private static final float LOD_FAR_DISTANCE = 2400.0f;
	private static final int MAX_SEGMENT_BYTES = (STOPS_FULL.length * 4 + 4) * 4 * AuroraMesh.VERTEX_BYTES;

	// ---- Animation. Seconds are "animation seconds": the Mod Menu speed slider scales them. ----
	private static final float WAVE_LENGTH_STEPS = 12.0f; // Distance between two bright crests along a chain, in cells.
	private static final float WAVE_PERIOD = 7.0f; // Seconds for a crest to travel one wavelength.
	private static final float WAVE_MIN = 0.35f; // Ray brightness in a wave trough (a crest is 1).
	private static final float SHIMMER_DEPTH = 0.2f; // How much each ray flickers on its own (0 = not at all).
	private static final float SHIMMER_PERIOD_FAST = 2.2f; // Every ray picks its own period between these two.
	private static final float SHIMMER_PERIOD_SLOW = 5.0f;
	private static final float FLARE_CYCLE_STEPS = 120.0f; // Longer than any chain: a flare crosses it, then a pause.
	private static final float FLARE_SPEED = 4.0f; // Cells per second, so one cycle is 30 s.
	private static final float FLARE_WIDTH = 6.0f; // Half-width of the bright band, in cells.
	private static final float FLARE_LEAD = 15.0f; // The flare starts this far before the chain, so it never pops in.
	private static final float FLARE_GAIN = 0.7f; // Extra brightness at the flare's centre.
	private static final float HEIGHT_MIN = 0.88f; // Ray top at its dimmest, relative to the nominal height...
	private static final float HEIGHT_MAX = 1.12f; // ...and at its brightest.
	private static final float HEIGHT_WAVE_LENGTH_STEPS = 18.0f; // Distance between two tall crests along a chain, in cells.
	private static final float HEIGHT_WAVE_PERIOD = 11.0f; // Seconds for a tall crest to travel one wavelength.
	private static final float HEIGHT_WAVE_MIN = 0.6f; // Height (and so where it fades out) in a low trough, x nominal...
	private static final float HEIGHT_WAVE_MAX = 1.2f; // ...and on a high crest.
	private static final float TWO_PI = (float) (Math.PI * 2);
	private static final float WAVE_FREQ = TWO_PI / WAVE_LENGTH_STEPS;
	private static final float WAVE_RATE = TWO_PI / WAVE_PERIOD;
	private static final float HEIGHT_WAVE_FREQ = TWO_PI / HEIGHT_WAVE_LENGTH_STEPS;
	private static final float HEIGHT_WAVE_RATE = TWO_PI / HEIGHT_WAVE_PERIOD;
	private static final float SHIMMER_RATE_FAST = TWO_PI / SHIMMER_PERIOD_FAST;
	private static final float SHIMMER_RATE_SLOW = TWO_PI / SHIMMER_PERIOD_SLOW;

	// RGB values are 0..1. Edit these three colors to change the whole palette.
	private static final Color GREEN = new Color(0.13f, 0.91f, 0.66f);
	private static final Color CYAN = new Color(0.18f, 0.72f, 0.94f);
	private static final Color VIOLET = new Color(0.55f, 0.24f, 0.82f);

	// Each step is one of the 8 neighbouring cells. Columns themselves never rotate.
	private static final int[] STEP_X = {1, 1, 0, -1, -1, -1, 0, 1};
	private static final int[] STEP_Z = {0, 1, 1, 1, 0, -1, -1, -1};
	// ====================== END AURORA SETTINGS ======================

	private final int patchCellX;
	private final int patchCellZ;
	private final float baseY;
	private final boolean[][] occupied;
	private final float[][] appearAt; // Per cell: the snake's threshold, 2 = empty. Shown while below the frequency.
	private final List<Segment> segments;

	public AuroraRibbon(Random random, int patchCellX, int patchCellZ) {
		this.patchCellX = patchCellX;
		this.patchCellZ = patchCellZ;
		this.baseY = BASE_Y + random.nextFloat() * BASE_Y_VARIATION;
		this.occupied = new boolean[PATCH_CELLS][PATCH_CELLS];
		this.appearAt = new float[PATCH_CELLS][PATCH_CELLS];
		for (float[] row : appearAt) Arrays.fill(row, 2.0f);
		this.segments = createRibbons(random, occupied, appearAt);
	}

	private static List<Segment> createRibbons(Random random, boolean[][] occupied, float[][] appearAt) {
		List<Segment> result = new ArrayList<>();

		for (int ribbon = 0; ribbon < MAX_RIBBONS; ribbon++) {
			List<Cell> path = null;
			for (int attempt = 0; attempt < START_ATTEMPTS; attempt++) {
				int x = EDGE_MARGIN + random.nextInt(PATCH_CELLS - EDGE_MARGIN * 2);
				int z = EDGE_MARGIN + random.nextInt(PATCH_CELLS - EDGE_MARGIN * 2);
				if (occupied[x][z]) continue;

				int heading = random.nextInt(8);
				int length = MIN_CHAIN_LENGTH + random.nextInt(MAX_CHAIN_LENGTH - MIN_CHAIN_LENGTH + 1);
				List<Cell> candidate = walk(x, z, heading, length, occupied, random);
				if (candidate.size() >= MIN_CHAIN_LENGTH) {
					path = candidate;
					break;
				}
			}
			if (path == null) continue;

			float phase = random.nextFloat() * (float) (Math.PI * 2);
			float tint = 0.25f + random.nextFloat() * 0.5f;
			float chainHeight = 0.88f + random.nextFloat() * 0.24f;
			float chainBrightness = 0.97f + random.nextFloat() * 0.03f;
			float appear = random.nextFloat(); // 0..1, shown when the frequency setting is above it.
			Style style = new Style(tint);

			for (int step = 0; step < path.size(); step++) {
				Cell cell = path.get(step);
				occupied[cell.x][cell.z] = true;
				appearAt[cell.x][cell.z] = appear;
				result.add(new Segment(cell.x, cell.z, appear,
					chainHeight * (0.96f + random.nextFloat() * 0.08f),
					chainBrightness * (0.99f + random.nextFloat() * 0.01f),
					step, phase, random.nextFloat() * 0.7f, style));
			}
		}
		return result;
	}

	private static List<Cell> walk(int startX, int startZ, int heading, int length,
			boolean[][] occupied, Random random) {
		List<Cell> path = new ArrayList<>(length);
		boolean[][] visited = new boolean[PATCH_CELLS][PATCH_CELLS];
		boolean[][] risingDiagonals = new boolean[PATCH_CELLS][PATCH_CELLS];
		boolean[][] fallingDiagonals = new boolean[PATCH_CELLS][PATCH_CELLS];
		int x = startX;
		int z = startZ;
		int deviation = 0;
		int sidewaysSteps = 0;
		int stepsToTurn = 1 + random.nextInt(2);

		for (int step = 0; step < length; step++) {
			if (!open(x, z, occupied, visited)) break;
			path.add(new Cell(x, z));
			visited[x][z] = true;
			if (step + 1 == length) break;

			int preferredDeviation = deviation;
			if (--stepsToTurn == 0) {
				preferredDeviation = nextDeviation(deviation, sidewaysSteps, random);
				stepsToTurn = 2 + random.nextInt(4);
			}

			int nextDeviation = findOpenDeviation(x, z, heading, deviation, preferredDeviation, sidewaysSteps,
					occupied, visited, risingDiagonals, fallingDiagonals);
			if (nextDeviation == Integer.MIN_VALUE) break;

			int nextHeading = (heading + nextDeviation + 8) & 7;
			int nextX = x + STEP_X[nextHeading];
			int nextZ = z + STEP_Z[nextHeading];
			markDiagonal(x, z, nextX, nextZ, risingDiagonals, fallingDiagonals);
			sidewaysSteps = Math.abs(nextDeviation) == MAX_DIRECTION_DEVIATION
					? sidewaysSteps + 1 : 0;
			deviation = nextDeviation;
			x = nextX;
			z = nextZ;
		}
		return path;
	}

	private static int nextDeviation(int current, int sidewaysSteps, Random random) {
		int limit = sidewaysSteps >= MAX_SIDEWAYS_STEPS ? MAX_DIRECTION_DEVIATION - 1 : MAX_DIRECTION_DEVIATION;
		current = Math.max(-limit, Math.min(limit, current));
		if (current == 0) return random.nextInt(4) != 0 ? (random.nextBoolean() ? 1 : -1) : 0;
		int roll = random.nextInt(100);
		if (roll < 50) return current;
		return roll < 82 ? current - Integer.signum(current) : Math.max(-limit, Math.min(limit, current + Integer.signum(current)));
	}

	private static int findOpenDeviation(int x, int z, int heading, int current, int preferred, int sidewaysSteps,
			boolean[][] occupied, boolean[][] visited, boolean[][] risingDiagonals,
			boolean[][] fallingDiagonals) {
		int limit = sidewaysSteps >= MAX_SIDEWAYS_STEPS ? MAX_DIRECTION_DEVIATION - 1 : MAX_DIRECTION_DEVIATION;
		int[] choices = {preferred, current, current - 1, current + 1};
		for (int deviation : choices) {
			if (deviation < -limit || deviation > limit || Math.abs(deviation - current) > 1) continue;
			int direction = (heading + deviation + 8) & 7;
			int nextX = x + STEP_X[direction];
			int nextZ = z + STEP_Z[direction];
			if (open(nextX, nextZ, occupied, visited)
					&& !crossesDiagonal(x, z, nextX, nextZ, risingDiagonals, fallingDiagonals)) return deviation;
		}
		return Integer.MIN_VALUE;
	}

	private static boolean crossesDiagonal(int x, int z, int nextX, int nextZ,
			boolean[][] risingDiagonals, boolean[][] fallingDiagonals) {
		int dx = nextX - x;
		int dz = nextZ - z;
		if (Math.abs(dx) != 1 || Math.abs(dz) != 1) return false;
		int cellX = Math.min(x, nextX);
		int cellZ = Math.min(z, nextZ);
		return dx == dz ? fallingDiagonals[cellX][cellZ] : risingDiagonals[cellX][cellZ];
	}

	private static void markDiagonal(int x, int z, int nextX, int nextZ,
			boolean[][] risingDiagonals, boolean[][] fallingDiagonals) {
		int dx = nextX - x;
		int dz = nextZ - z;
		if (Math.abs(dx) != 1 || Math.abs(dz) != 1) return;
		int cellX = Math.min(x, nextX);
		int cellZ = Math.min(z, nextZ);
		if (dx == dz) risingDiagonals[cellX][cellZ] = true;
		else fallingDiagonals[cellX][cellZ] = true;
	}

	private static boolean open(int x, int z, boolean[][] occupied, boolean[][] visited) {
		return inside(x, z) && !occupied[x][z] && !visited[x][z];
	}

	private static boolean inside(int x, int z) {
		return x >= 1 && x < PATCH_CELLS - 1 && z >= 1 && z < PATCH_CELLS - 1;
	}


	/**
	 * Brightness of one ray at a given time (pure maths, no rendering): about 0.28..1 on its own, up to about 1.7
	 * inside a flare. Three layers: a wave running along the chain (two sines, so it never looks mechanical),
	 * a slow independent shimmer per ray, and a rare bright pulse that sweeps the whole chain.
	 */
	static float activity(int step, float phase, float localPhase, float time, boolean flares) {
		float crest = (float) Math.sin(step * WAVE_FREQ - time * WAVE_RATE + phase);
		float drift = (float) Math.sin(step * 0.23f + time * 0.37f + phase * 1.7f);
		float wave = 0.5f + 0.5f * (0.65f * crest + 0.35f * drift);

		float rate = lerp(SHIMMER_RATE_SLOW, SHIMMER_RATE_FAST, localPhase / 0.7f);
		float shimmer = 1.0f - SHIMMER_DEPTH * (0.5f + 0.5f * (float) Math.sin(time * rate + localPhase * 9.0f));

		float ambient = lerp(WAVE_MIN, 1.0f, wave) * shimmer;
		if (!flares) return ambient;

		float cycle = time * FLARE_SPEED + phase * (FLARE_CYCLE_STEPS / TWO_PI);
		float position = cycle - FLARE_CYCLE_STEPS * (float) Math.floor(cycle / FLARE_CYCLE_STEPS) - FLARE_LEAD;
		float d = (step - position) / FLARE_WIDTH;
		return ambient + FLARE_GAIN * (float) Math.exp(-d * d);
	}

	/**
	 * Height multiplier of one ray (HEIGHT_WAVE_MIN..HEIGHT_WAVE_MAX): its own slow wave along the chain, not tied to
	 * the brightness wave. The ray's top, and so the place where it fades to transparent, rises and sinks with it.
	 */
	static float heightWave(int step, float phase, float time) {
		float crest = (float) Math.sin(step * HEIGHT_WAVE_FREQ - time * HEIGHT_WAVE_RATE + phase * 2.3f + 1.3f);
		float drift = (float) Math.sin(step * 0.11f + time * 0.21f + phase * 0.7f);
		return lerp(HEIGHT_WAVE_MIN, HEIGHT_WAVE_MAX, 0.5f + 0.5f * (0.7f * crest + 0.3f * drift));
	}

	/**
	 * Writes this patch into {@code out}. X/Z are relative to the anchor (the camera when the mesh is built),
	 * Y is absolute; the renderer translates the whole mesh by (anchor - camera) every frame.
	 */
	public void render(AuroraMesh out, double anchorX, double anchorZ, float time, float fade) {
		float cellSize = SkyboxConfig.auroraGridCellSize;
		float height = SkyboxConfig.auroraHeight;
		float halfCell = cellSize * 0.5f;
		float brightness = SkyboxConfig.auroraBrightness;
		boolean flares = SkyboxConfig.auroraFlares;
		float frequency = SkyboxConfig.auroraFrequency;
		float midSq = LOD_MID_DISTANCE * LOD_MID_DISTANCE;
		float farSq = LOD_FAR_DISTANCE * LOD_FAR_DISTANCE;

		for (Segment segment : segments) {
			if (segment.appear >= frequency) continue;
			float activity = Math.min(1.0f, activity(segment.step, segment.phase, segment.localPhase, time, flares));
			float strength = fade * brightness * segment.brightness * activity;
			if (strength < 0.004f) continue;

			// One axis-aligned square per cell. A diagonal move only changes the cell position.
			double centerX = (patchCellX + segment.x + 0.5) * cellSize;
			double centerZ = (patchCellZ + segment.z + 0.5) * cellSize;
			float dx = (float) (centerX - anchorX);
			float dz = (float) (centerZ - anchorZ);
			float distSq = dx * dx + dz * dz;
			Lod lod = distSq < midSq ? segment.style.full : distSq < farSq ? segment.style.mid : segment.style.far;

			float bottom = baseY;
			// Brighter rays stretch a little higher; the top is faded out, so the uneven skyline stays invisible.
			float top = bottom + height * segment.heightScale * lerp(HEIGHT_MIN, HEIGHT_MAX, activity)
				* heightWave(segment.step, segment.phase, time);

			out.ensure(MAX_SEGMENT_BYTES);
			drawPrism(out, dx - halfCell, dx + halfCell, dz - halfCell, dz + halfCell, bottom, top, strength, lod,
				hasCell(segment.x - 1, segment.z), hasCell(segment.x + 1, segment.z),
				hasCell(segment.x, segment.z - 1), hasCell(segment.x, segment.z + 1));
		}
	}

	private boolean hasCell(int x, int z) {
		return x >= 0 && x < PATCH_CELLS && z >= 0 && z < PATCH_CELLS && appearAt[x][z] < SkyboxConfig.auroraFrequency;
	}

	private static void drawPrism(AuroraMesh out, float x0, float x1, float z0, float z1, float bottom, float top,
			float strength, Lod lod, boolean west, boolean east, boolean north, boolean south) {
		float height = top - bottom;
		int bands = lod.stops.length - 1;
		for (int band = 0; band < bands; band++) {
			float yLow = bottom + height * lod.stops[band];
			float yHigh = bottom + height * lod.stops[band + 1];
			byte lowA = alphaByte(lod.alpha[band] * strength);
			byte highA = alphaByte(lod.alpha[band + 1] * strength);
			byte lr = lod.r[band], lg = lod.g[band], lb = lod.b[band];
			byte hr = lod.r[band + 1], hg = lod.g[band + 1], hb = lod.b[band + 1];

			if (!north) wall(out, x0, z0, x1, z0, yLow, yHigh, lr, lg, lb, lowA, hr, hg, hb, highA);
			if (!east) wall(out, x1, z0, x1, z1, yLow, yHigh, lr, lg, lb, lowA, hr, hg, hb, highA);
			if (!south) wall(out, x1, z1, x0, z1, yLow, yHigh, lr, lg, lb, lowA, hr, hg, hb, highA);
			if (!west) wall(out, x0, z1, x0, z0, yLow, yHigh, lr, lg, lb, lowA, hr, hg, hb, highA);
		}

		// Bottom cap only. The top stays open and fades away.
		byte a = alphaByte(lod.alpha[0] * strength);
		out.vertex(x0, bottom, z0, lod.r[0], lod.g[0], lod.b[0], a);
		out.vertex(x1, bottom, z0, lod.r[0], lod.g[0], lod.b[0], a);
		out.vertex(x1, bottom, z1, lod.r[0], lod.g[0], lod.b[0], a);
		out.vertex(x0, bottom, z1, lod.r[0], lod.g[0], lod.b[0], a);
	}

	private static void wall(AuroraMesh out, float ax, float az, float bx, float bz, float yLow, float yHigh,
			byte lr, byte lg, byte lb, byte la, byte hr, byte hg, byte hb, byte ha) {
		out.vertex(ax, yLow, az, lr, lg, lb, la);
		out.vertex(ax, yHigh, az, hr, hg, hb, ha);
		out.vertex(bx, yHigh, bz, hr, hg, hb, ha);
		out.vertex(bx, yLow, bz, lr, lg, lb, la);
	}

	private static byte alphaByte(float alpha) {
		return (byte) Math.round(clamp(alpha) * 255.0f);
	}

	private static byte channel(float value) {
		return (byte) Math.round(clamp(value) * 255.0f);
	}

	private static float alphaAt(float height, float strength) {
		float fade = smoothstep(TOP_FADE_START, 1.0f, height) * clamp(FADE_STRENGTH);
		return BASE_ALPHA * strength * (1.0f - fade);
	}

	private static Color colorAt(float height, float tint) {
		Color green = new Color(GREEN.r + 0.05f * tint, GREEN.g, GREEN.b + 0.08f * tint);
		Color violet = new Color(VIOLET.r + 0.06f * tint, VIOLET.g, VIOLET.b);
		if (height < CYAN_POSITION) return mix(green, CYAN, smoothstep(0, CYAN_POSITION, height));
		if (height < VIOLET_POSITION) return mix(CYAN, violet, smoothstep(CYAN_POSITION, VIOLET_POSITION, height));
		return violet;
	}

	private static float[] verticalStops() {
		float[] stops = new float[COLOR_BANDS + 4];
		for (int i = 0; i <= COLOR_BANDS; i++) stops[i] = i / (float) COLOR_BANDS;
		stops[COLOR_BANDS + 1] = CYAN_POSITION;
		stops[COLOR_BANDS + 2] = VIOLET_POSITION;
		stops[COLOR_BANDS + 3] = TOP_FADE_START;
		Arrays.sort(stops);
		return stops;
	}

	private static float[] dedupe(float[] sorted) {
		float[] out = new float[sorted.length];
		int n = 0;
		for (float v : sorted) {
			if (n == 0 || v - out[n - 1] > 1.0e-5f) out[n++] = v;
		}
		return Arrays.copyOf(out, n);
	}

	private static Color mix(Color a, Color b, float amount) {
		return new Color(lerp(a.r, b.r, amount), lerp(a.g, b.g, amount), lerp(a.b, b.b, amount));
	}

	private static float smoothstep(float from, float to, float value) {
		float t = clamp((value - from) / (to - from));
		return t * t * (3.0f - 2.0f * t);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float clamp(float value) {
		return Math.max(0, Math.min(1, value));
	}

	private record Cell(int x, int z) {}
	private record Color(float r, float g, float b) {}
	private record Segment(int x, int z, float appear, float heightScale, float brightness,
		int step, float phase, float localPhase, Style style) {}

	/** Gradient of one chain, precomputed once per level of detail: colour bytes and the alpha shape per stop. */
	private static final class Lod {
		final float[] stops;
		final byte[] r, g, b;
		final float[] alpha; // multiplied by the ray's strength at build time

		Lod(float[] stops, float tint) {
			this.stops = stops;
			int n = stops.length;
			r = new byte[n];
			g = new byte[n];
			b = new byte[n];
			alpha = new float[n];
			for (int i = 0; i < n; i++) {
				Color c = colorAt(stops[i], tint);
				r[i] = channel(c.r);
				g[i] = channel(c.g);
				b[i] = channel(c.b);
				alpha[i] = alphaAt(stops[i], 1.0f);
			}
		}
	}

	private static final class Style {
		final Lod full, mid, far;

		Style(float tint) {
			full = new Lod(STOPS_FULL, tint);
			mid = new Lod(STOPS_MID, tint);
			far = new Lod(STOPS_FAR, tint);
		}
	}
}
