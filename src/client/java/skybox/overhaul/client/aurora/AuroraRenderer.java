package skybox.overhaul.client.aurora;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Draws world-fixed aurora ribbons (one per ~600x600 cell, seeded by cell coords) at night over cold biomes. */
public final class AuroraRenderer {
	private static final int CELL = 600;
	private static final Map<Long, AuroraRibbon> CACHE = new HashMap<>();
	private static float fade;
	private static long last = System.nanoTime();

	public static void init() {
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(AuroraRenderer::render);
	}

	private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		var level = mc.level;
		if (level == null || mc.player == null) return;

		long now = System.nanoTime();
		float dt = (now - last) / 1e9f;
		last = now;

		long tod = level.getOverworldClockTime() % 24000;
		boolean night = tod > 13000 && tod < 23000;
		boolean cold = level.getBiome(mc.player.blockPosition()).value().getBaseTemperature() <= 0.05f;
		fade = Math.max(0f, Math.min(1f, fade + (night && cold ? dt / 4f : -dt / 4f)));  // ~4s fade
		if (fade <= 0f) return;

		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		PoseStack ps = ctx.poseStack();
		VertexConsumer vc = ctx.bufferSource().getBuffer(RenderTypes.lightning());
		float t = now / 1e9f;
		int cx = Math.floorDiv((int) cam.x, CELL), cz = Math.floorDiv((int) cam.z, CELL);
		if (CACHE.size() > 200) CACHE.clear();
		for (int i = -1; i <= 1; i++)
			for (int j = -1; j <= 1; j++) {
				int x = cx + i, z = cz + j;
				long key = ((long) x << 32) ^ (z & 0xffffffffL);
				AuroraRibbon rb = CACHE.computeIfAbsent(key, k -> {
					Random r = new Random(k * 0x9E3779B97F4A7C15L);
					return r.nextFloat() < 0.8f ? new AuroraRibbon(r, (double) x * CELL, (double) z * CELL, CELL) : null;
				});
				if (rb != null) rb.render(vc, ps.last().pose(), cam.x, cam.y, cam.z, t, fade);
			}
	}
}
