package skybox.overhaul.client.aurora;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import skybox.overhaul.client.config.SkyboxConfig;
import skybox.overhaul.client.render.SkyboxPipelines;

/**
 * Loads nearby aurora patches and fades each new patch in.
 *
 * <p>26.3: vanilla keeps a render pass open while the level render events fire, and uploading a buffer or opening
 * a second pass inside it throws "Close the existing render pass before performing additional commands".
 * So the work is split: {@link #prepare} builds the mesh in LevelRenderEvents.END_EXTRACTION (no pass is open
 * there), and {@link #drawInSky} draws it into the sky pass that SkyRendererMixin already gets.
 *
 * <p>Performance: the aurora animates slowly, so the mesh is rebuilt only {@code 1e9 / REBUILD_NANOS} times a
 * second, not every frame. Its X/Z are relative to the camera position at build time and Y is absolute;
 * {@link #drawInSky} shifts it by (anchor - current camera) so it stays glued to the world in between.
 * The aurora is blended additively, so quads are not sorted.
 */
public final class AuroraRenderer {
	// Edit these only if you want to change streaming behaviour.
	private static final int PATCH_RADIUS = 2;
	private static final long PATCH_FADE_NANOS = 1_650_000_000L;
	private static final float NIGHT_FADE_SECONDS = 4.0f;
	private static final long REBUILD_NANOS = 50_000_000L; // 20 mesh rebuilds per second
	private static final int MAX_NEW_PATCHES_PER_BUILD = 1; // generating a patch is slow, spread it over several builds
	private static final int RETIRE_FRAMES = 3; // a replaced buffer is closed this many frames later
	private static final Map<Long, Patch> CACHE = new HashMap<>();

	private static final AuroraMesh MESH = new AuroraMesh();
	private static GpuBuffer vertexBuffer;
	private static int quadCount;
	private static GpuBuffer indexBuffer;
	private static int indexCapacityQuads;
	private static double anchorX, anchorZ; // camera X/Z when the mesh was built
	private static double camX, camY, camZ; // camera now
	private static long lastBuild;
	private static long frame;
	private record Retired(GpuBuffer buffer, long frame) {}
	private static final ArrayDeque<Retired> RETIRED = new ArrayDeque<>();

	private static float nightFade;
	private static double animTime; // Animation clock in seconds; the speed setting scales how fast it runs.
	private static long lastFrame = System.nanoTime();

	public static void init() {
		LevelRenderEvents.END_EXTRACTION.register(AuroraRenderer::prepare);
	}

	private static void prepare(LevelExtractionContext context) {
		frame++;
		closeRetired();
		Minecraft mc = Minecraft.getInstance();
		var level = mc.level;
		if (level == null || mc.player == null) {
			release();
			return;
		}

		long now = System.nanoTime();
		float delta = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;

		boolean show = false;
		if (SkyboxConfig.auroraEnabled) {
			long dayTime = level.getOverworldClockTime() % 24000;
			boolean night = dayTime > 13000 && dayTime < 23000;
			show = night && (!SkyboxConfig.auroraColdOnly
				|| level.getBiome(mc.player.blockPosition()).value().getBaseTemperature() <= 0.05f);
		}
		nightFade = clamp(nightFade + (show ? delta : -delta) / NIGHT_FADE_SECONDS);
		if (nightFade == 0) {
			release();
			return;
		}
		animTime += delta * SkyboxConfig.auroraSpeed;

		Vec3 camera = mc.gameRenderer.mainCamera().position();
		camX = camera.x;
		camY = camera.y;
		camZ = camera.z;
		if (vertexBuffer == null || now - lastBuild >= REBUILD_NANOS) {
			rebuild(now);
		}
	}

	private static void rebuild(long now) {
		lastBuild = now;
		anchorX = camX;
		anchorZ = camZ;
		MESH.clear();

		float time = (float) animTime;
		float cellSize = SkyboxConfig.auroraGridCellSize;
		int cameraCellX = (int) Math.floor(camX / cellSize);
		int cameraCellZ = (int) Math.floor(camZ / cellSize);
		int centerPatchX = Math.floorDiv(cameraCellX, AuroraRibbon.PATCH_CELLS);
		int centerPatchZ = Math.floorDiv(cameraCellZ, AuroraRibbon.PATCH_CELLS);

		int created = 0;
		for (int dx = -PATCH_RADIUS; dx <= PATCH_RADIUS; dx++) {
			for (int dz = -PATCH_RADIUS; dz <= PATCH_RADIUS; dz++) {
				int patchX = centerPatchX + dx;
				int patchZ = centerPatchZ + dz;
				long key = key(patchX, patchZ);
				Patch patch = CACHE.get(key);
				if (patch == null) {
					if (created >= MAX_NEW_PATCHES_PER_BUILD) continue;
					created++;
					patch = new Patch(new AuroraRibbon(new Random(key * 0x9E3779B97F4A7C15L),
						patchX * AuroraRibbon.PATCH_CELLS, patchZ * AuroraRibbon.PATCH_CELLS), now, patchX, patchZ);
					CACHE.put(key, patch);
				}
				float loaded = smoothstep((now - patch.createdAt) / (float) PATCH_FADE_NANOS);
				patch.ribbon.render(MESH, anchorX, anchorZ, time, nightFade * loaded);
			}
		}

		CACHE.entrySet().removeIf(entry ->
			Math.abs(entry.getValue().x - centerPatchX) > PATCH_RADIUS + 2
				|| Math.abs(entry.getValue().z - centerPatchZ) > PATCH_RADIUS + 2);

		retire(vertexBuffer);
		vertexBuffer = null;
		quadCount = MESH.quads();
		if (quadCount == 0) return;

		if (indexBuffer == null || quadCount > indexCapacityQuads) {
			retire(indexBuffer);
			indexCapacityQuads = Math.max(quadCount + quadCount / 2, 8192);
			indexBuffer = SkyboxPipelines.quadIndices(indexCapacityQuads);
		}
		vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Skybox aurora",
			GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, MESH.data());
	}

	/** Called from SkyRendererMixin inside vanilla's sky pass; draws the mesh built by {@link #prepare}. */
	public static void drawInSky(RenderPass pass) {
		if (vertexBuffer == null || indexBuffer == null || quadCount == 0) return;
		Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrixCopy())
			.translate((float) (anchorX - camX), (float) -camY, (float) (anchorZ - camZ));
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
			modelView, new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
		pass.setPipeline(RenderSystem.getCompiledPipeline(SkyboxPipelines.AURORA));
		RenderSystem.bindDefaultUniforms(pass);
		pass.setUniform("DynamicTransforms", transforms);
		pass.setVertexBuffer(0, vertexBuffer.slice());
		pass.setIndexBuffer(indexBuffer, IndexType.INT);
		pass.drawIndexed(quadCount * 6, 1, 0, 0, 0);
	}

	/** Night is over (or no level): drop the mesh; it is rebuilt when the aurora fades back in. */
	private static void release() {
		retire(vertexBuffer);
		vertexBuffer = null;
		quadCount = 0;
	}

	/** Buffers may still be referenced by commands recorded for frames in flight, so close them a few frames later. */
	private static void retire(GpuBuffer buffer) {
		if (buffer != null) RETIRED.add(new Retired(buffer, frame));
	}

	private static void closeRetired() {
		while (!RETIRED.isEmpty() && frame - RETIRED.peek().frame >= RETIRE_FRAMES) {
			RETIRED.poll().buffer.close();
		}
	}

	private static long key(int x, int z) {
		return ((long) x << 32) | (z & 0xffffffffL);
	}

	private static float smoothstep(float value) {
		float t = clamp(value);
		return t * t * (3 - 2 * t);
	}

	private static float clamp(float value) {
		return Math.max(0, Math.min(1, value));
	}

	private record Patch(AuroraRibbon ribbon, long createdAt, int x, int z) {}

	private AuroraRenderer() {}
}
