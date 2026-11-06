package skybox.overhaul.client.aurora;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import skybox.overhaul.client.SkyboxConfig;
import skybox.overhaul.client.SkyboxPipelines;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Loads nearby aurora patches and fades each new patch in. */
public final class AuroraRenderer {
	// Edit these only if you want to change streaming behaviour.
	private static final int PATCH_RADIUS = 2;
	private static final long PATCH_FADE_NANOS = 1_650_000_000L;
	private static final float NIGHT_FADE_SECONDS = 4.0f;
	private static final Map<Long, Patch> CACHE = new HashMap<>();

	private static float nightFade;
	private static double animTime; // Animation clock in seconds; the speed setting scales how fast it runs.
	private static long lastFrame = System.nanoTime();

	public static void init() {
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(AuroraRenderer::render);
	}

	private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		var level = mc.level;
		if (level == null || mc.player == null) return;

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
		if (nightFade == 0) return;
		animTime += delta * SkyboxConfig.auroraSpeed;

		Vec3 camera = mc.gameRenderer.getMainCamera().position();
		PoseStack poses = context.poseStack();
		VertexConsumer out = context.bufferSource().getBuffer(SkyboxPipelines.AURORA_TYPE);
		float time = (float) animTime;
		float cellSize = SkyboxConfig.auroraGridCellSize;
		int cameraCellX = (int) Math.floor(camera.x / cellSize);
		int cameraCellZ = (int) Math.floor(camera.z / cellSize);
		int centerPatchX = Math.floorDiv(cameraCellX, AuroraRibbon.PATCH_CELLS);
		int centerPatchZ = Math.floorDiv(cameraCellZ, AuroraRibbon.PATCH_CELLS);

		for (int dx = -PATCH_RADIUS; dx <= PATCH_RADIUS; dx++) {
			for (int dz = -PATCH_RADIUS; dz <= PATCH_RADIUS; dz++) {
				int patchX = centerPatchX + dx;
				int patchZ = centerPatchZ + dz;
				long key = key(patchX, patchZ);
				Patch patch = CACHE.computeIfAbsent(key, ignored -> new Patch(
					new AuroraRibbon(new Random(key * 0x9E3779B97F4A7C15L),
						patchX * AuroraRibbon.PATCH_CELLS, patchZ * AuroraRibbon.PATCH_CELLS),
					now, patchX, patchZ));

				float loaded = smoothstep((now - patch.createdAt) / (float) PATCH_FADE_NANOS);
				patch.ribbon.render(out, poses.last().pose(), camera.x, camera.y, camera.z,
					time, nightFade * loaded);
			}
		}

		CACHE.entrySet().removeIf(entry ->
			Math.abs(entry.getValue().x - centerPatchX) > PATCH_RADIUS + 2
				|| Math.abs(entry.getValue().z - centerPatchZ) > PATCH_RADIUS + 2);
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
