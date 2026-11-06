package skybox.overhaul.client.aurora;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import skybox.overhaul.client.SkyboxConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Sparse aurora made from vertical prisms on an eight-direction square grid. */
public final class AuroraRibbon {
	// ======================== EDIT AURORA HERE ========================
	// Cell size and pillar height are live sliders in Mod Menu (SkyboxConfig).
	public static final int PATCH_CELLS = 32; // Patch width/depth, measured in grid cells.

	private static final int MIN_RIBBONS = 2;
	private static final int MAX_RIBBONS = 3;
	private static final int MIN_CHAIN_LENGTH = 18;
	private static final int MAX_CHAIN_LENGTH = 34;
	private static final int EDGE_MARGIN = 2;
	private static final int START_ATTEMPTS = 48;

	private static final float BASE_Y = 215.0f;
	private static final float BASE_Y_VARIATION = 25.0f;
	private static final float TOP_FADE_START = 0.001f; // 0 = bottom, 1 = top.
	private static final float BASE_ALPHA = 0.85f;
	private static final int COLOR_BANDS = 6;
	private static final float CYAN_HEIGHT = 0.34f;

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
	private final List<Segment> segments;

	public AuroraRibbon(Random random, int patchCellX, int patchCellZ) {
		this.patchCellX = patchCellX;
		this.patchCellZ = patchCellZ;
		this.baseY = BASE_Y + random.nextFloat() * BASE_Y_VARIATION;
		this.occupied = new boolean[PATCH_CELLS][PATCH_CELLS];
		this.segments = createRibbons(random, occupied);
	}

	private static List<Segment> createRibbons(Random random, boolean[][] occupied) {
		List<Segment> result = new ArrayList<>();
		int count = MIN_RIBBONS + random.nextInt(MAX_RIBBONS - MIN_RIBBONS + 1);

		for (int ribbon = 0; ribbon < count; ribbon++) {
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

			for (int step = 0; step < path.size(); step++) {
				Cell cell = path.get(step);
				occupied[cell.x][cell.z] = true;
				result.add(new Segment(cell.x, cell.z,
					chainHeight * (0.96f + random.nextFloat() * 0.08f),
					chainBrightness * (0.99f + random.nextFloat() * 0.01f),
					step, phase, random.nextFloat() * 0.7f, tint));
			}
		}
		return result;
	}

	private static List<Cell> walk(int startX, int startZ, int heading, int length,
			boolean[][] occupied, Random random) {
		List<Cell> path = new ArrayList<>(length);
		boolean[][] visited = new boolean[PATCH_CELLS][PATCH_CELLS];
		int x = startX;
		int z = startZ;
		int stepsToTurn = 2 + random.nextInt(5);

		for (int step = 0; step < length; step++) {
			if (!inside(x, z) || occupied[x][z] || visited[x][z]) break;
			path.add(new Cell(x, z));
			visited[x][z] = true;
			if (step + 1 == length) break;

			if (--stepsToTurn == 0) {
				heading = turn(heading, random);
				stepsToTurn = 2 + random.nextInt(5);
			}

			int nextX = x + STEP_X[heading];
			int nextZ = z + STEP_Z[heading];
			if (!open(nextX, nextZ, occupied, visited)) {
				heading = findOpenHeading(x, z, heading, occupied, visited);
				if (heading < 0) break;
				nextX = x + STEP_X[heading];
				nextZ = z + STEP_Z[heading];
			}
			x = nextX;
			z = nextZ;
		}
		return path;
	}

	private static int findOpenHeading(int x, int z, int heading, boolean[][] occupied, boolean[][] visited) {
		for (int turn = 1; turn <= 3; turn++) {
			int left = (heading - turn + 8) & 7;
			if (open(x + STEP_X[left], z + STEP_Z[left], occupied, visited)) return left;
			int right = (heading + turn) & 7;
			if (open(x + STEP_X[right], z + STEP_Z[right], occupied, visited)) return right;
		}
		return -1;
	}

	private static boolean open(int x, int z, boolean[][] occupied, boolean[][] visited) {
		return inside(x, z) && !occupied[x][z] && !visited[x][z];
	}

	private static boolean inside(int x, int z) {
		return x >= 1 && x < PATCH_CELLS - 1 && z >= 1 && z < PATCH_CELLS - 1;
	}

	private static int turn(int heading, Random random) {
		int roll = random.nextInt(100);
		int amount = roll < 52 ? 1 : roll < 91 ? 2 : 3; // 45°, 90° or 135°.
		return (heading + (random.nextBoolean() ? amount : -amount) + 8) & 7;
	}

	public void render(VertexConsumer out, Matrix4f matrix,
			double camX, double camY, double camZ, float time, float fade) {
		float cellSize = SkyboxConfig.auroraGridCellSize;
		float height = SkyboxConfig.auroraHeight;
		float halfCell = cellSize * 0.5f;

		for (Segment segment : segments) {
			float wave = 0.99f + 0.01f * (float) Math.sin(segment.step * 0.055f - time * 0.36f + segment.phase);
			float shimmer = 0.995f + 0.005f * (float) Math.sin(time * 0.55f + segment.localPhase);
			float strength = fade * segment.brightness * wave * shimmer;
			if (strength < 0.004f) continue;

			// One axis-aligned square per cell. A diagonal move only changes the cell position.
			double centerX = (patchCellX + segment.x + 0.5) * cellSize;
			double centerZ = (patchCellZ + segment.z + 0.5) * cellSize;
			float x0 = (float) (centerX - halfCell - camX);
			float x1 = (float) (centerX + halfCell - camX);
			float z0 = (float) (centerZ - halfCell - camZ);
			float z1 = (float) (centerZ + halfCell - camZ);
			float bottom = baseY - (float) camY;
			float top = bottom + height * segment.heightScale;

			drawPrism(out, matrix, x0, x1, z0, z1, bottom, top, strength, segment.tint,
				hasCell(segment.x - 1, segment.z), hasCell(segment.x + 1, segment.z),
				hasCell(segment.x, segment.z - 1), hasCell(segment.x, segment.z + 1));
		}
	}

	private boolean hasCell(int x, int z) {
		return x >= 0 && x < PATCH_CELLS && z >= 0 && z < PATCH_CELLS && occupied[x][z];
	}

	private static void drawPrism(VertexConsumer out, Matrix4f matrix,
			float x0, float x1, float z0, float z1, float bottom, float top,
			float strength, float tint, boolean west, boolean east, boolean north, boolean south) {
		float[] x = {x0, x1, x1, x0};
		float[] z = {z0, z0, z1, z1};

		for (int band = 0; band < COLOR_BANDS; band++) {
			float low = band / (float) COLOR_BANDS;
			float high = (band + 1) / (float) COLOR_BANDS;
			float yLow = bottom + (top - bottom) * low;
			float yHigh = bottom + (top - bottom) * high;
			Color lowColor = colorAt(low, tint);
			Color highColor = colorAt(high, tint);
			float lowAlpha = alphaAt(low, strength);
			float highAlpha = alphaAt(high, strength);

			for (int edge = 0; edge < 4; edge++) {
				if (edge == 0 && north || edge == 1 && east || edge == 2 && south || edge == 3 && west) continue;
				int a = edge;
				int b = (edge + 1) & 3;
				vertex(out, matrix, x[a], yLow, z[a], lowColor, lowAlpha);
				vertex(out, matrix, x[a], yHigh, z[a], highColor, highAlpha);
				vertex(out, matrix, x[b], yHigh, z[b], highColor, highAlpha);
				vertex(out, matrix, x[b], yLow, z[b], lowColor, lowAlpha);
			}
		}

		// Bottom cap only. The top stays open and fades away.
		Color base = colorAt(0, tint);
		float alpha = BASE_ALPHA * strength;
		for (int i = 0; i < 4; i++) vertex(out, matrix, x[i], bottom, z[i], base, alpha);
	}

	private static void vertex(VertexConsumer out, Matrix4f matrix,
			float x, float y, float z, Color color, float alpha) {
		out.addVertex(matrix, x, y, z).setColor(color.r, color.g, color.b, alpha);
	}

	private static float alphaAt(float height, float strength) {
		return BASE_ALPHA * strength * (1.0f - smoothstep(TOP_FADE_START, 1.0f, height));
	}

	private static Color colorAt(float height, float tint) {
		Color green = new Color(GREEN.r + 0.05f * tint, GREEN.g, GREEN.b + 0.08f * tint);
		Color violet = new Color(VIOLET.r + 0.06f * tint, VIOLET.g, VIOLET.b);
		if (height < CYAN_HEIGHT) return mix(green, CYAN, smoothstep(0, CYAN_HEIGHT, height));
		return mix(CYAN, violet, smoothstep(CYAN_HEIGHT, 1, height));
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
	private record Segment(int x, int z, float heightScale, float brightness,
		int step, float phase, float localPhase, float tint) {}
}
