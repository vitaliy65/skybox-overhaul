package skybox.overhaul.client.aurora;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import skybox.overhaul.client.SkyboxPipelines;
import skybox.overhaul.client.config.SkyboxConfig;

/**
 * Loads nearby aurora patches and fades each new patch in.
 *
 * <p>Performance: the aurora animates slowly, so the mesh is rebuilt only {@code 1e9 / REBUILD_NANOS} times a
 * second, not every frame. Between rebuilds the cached vertices are just replayed into vanilla's buffer.
 * Its X/Z are relative to the camera position at build time and Y is absolute; the replay matrix shifts it by
 * (anchor - current camera) so it stays glued to the world. The aurora is blended additively, so quads are
 * not sorted ({@code AURORA_TYPE} has no sortOnUpload).
 */
public final class AuroraRenderer {
	// Edit these only if you want to change streaming behaviour.
	private static final int PATCH_RADIUS = 2;
	private static final long PATCH_FADE_NANOS = 1_650_000_000L;
	private static final float NIGHT_FADE_SECONDS = 4.0f;
	private static final long REBUILD_NANOS = 50_000_000L; // 20 mesh rebuilds per second
	private static final int MAX_NEW_PATCHES_PER_BUILD = 1; // generating a patch is slow, spread it over several builds
	private static final Map<Long, Patch> CACHE = new HashMap<>();

	private static final AuroraMesh MESH = new AuroraMesh();
	private static boolean built;
	private static double anchorX, anchorZ; // camera X/Z when the mesh was built
	private static long lastBuild;

	private static float nightFade;
	private static double animTime; // Animation clock in seconds; the speed setting scales how fast it runs.
	private static long lastFrame = System.nanoTime();

	public static void init() {
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(AuroraRenderer::render);
	}

	private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
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

		Vec3 camera = mc.gameRenderer.getMainCamera().position();
		if (!built || now - lastBuild >= REBUILD_NANOS) {
			rebuild(now, camera);
		}
		if (MESH.quads() == 0) return;

		PoseStack poses = context.poseStack();
		Matrix4f matrix = new Matrix4f(poses.last().pose())
			.translate((float) (anchorX - camera.x), (float) -camera.y, (float) (anchorZ - camera.z));
		replay(context.bufferSource().getBuffer(SkyboxPipelines.AURORA_TYPE), matrix);
	}

	private static void rebuild(long now, Vec3 camera) {
		built = true;
		lastBuild = now;
		anchorX = camera.x;
		anchorZ = camera.z;
		MESH.clear();

		float time = (float) animTime;
		float cellSize = SkyboxConfig.auroraGridCellSize;
		int cameraCellX = (int) Math.floor(camera.x / cellSize);
		int cameraCellZ = (int) Math.floor(camera.z / cellSize);
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
	}

	/** Copies the cached POSITION_COLOR vertices (3 floats + 4 colour bytes each) into vanilla's buffer. */
	private static void replay(VertexConsumer out, Matrix4f matrix) {
		ByteBuffer data = MESH.data();
		while (data.remaining() >= AuroraMesh.VERTEX_BYTES) {
			float x = data.getFloat();
			float y = data.getFloat();
			float z = data.getFloat();
			int r = data.get() & 0xFF;
			int g = data.get() & 0xFF;
			int b = data.get() & 0xFF;
			int a = data.get() & 0xFF;
			out.addVertex(matrix, x, y, z).setColor(r, g, b, a);
		}
	}

	/** Night is over (or no level): drop the mesh; it is rebuilt when the aurora fades back in. */
	private static void release() {
		built = false;
		MESH.clear();
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
