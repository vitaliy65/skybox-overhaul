package skybox.overhaul.client.night;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import skybox.overhaul.client.config.SkyboxConfig;
import skybox.overhaul.client.sky.MeteorShower;
import skybox.overhaul.client.sky.SkyPalette;
import skybox.overhaul.client.render.SkyboxPipelines;
import skybox.overhaul.client.sky.SkyPalette;

/**
 * Replacement for vanilla renderStars: star positions from vanilla's RNG stream (the first N stars are the same for any N), square pixel stars,
 * but each star has its own size, colour and brightness, twinkle runs per bucket of stars, plus comets that fly across the sky and fade out.
 */
public final class NightSky implements AutoCloseable {
	private static final int BUCKETS = 16;      // twinkle groups: 16 distinct rhythms, stars assigned at random
	private static final int COMET_TAIL = 14;
	private static final long COMET_SLOT = 100L;   // ticks: one spawn roll per slot
	private static final float COMET_LIFE = 100.0F; // ticks (20 s) a comet flies, then it is gone; may span several slots
	private static final float COMET_ARC = 2.4F;    // radians flown over its life (~137 degrees)
	private static final int COMET_SLOTS_BACK = (int) (COMET_LIFE / COMET_SLOT) + 1; // older slots whose comet can still be alive
	private static final long METEOR_SLOT = 20L;
	private static final float METEOR_LIFE = 30.0F;
	private static final float METEOR_ARC = 0.7F;
	private static final int METEOR_SLOTS_BACK = (int) Math.ceil(METEOR_LIFE / METEOR_SLOT) + 1;
	private static final int[] TINTS = {0xB5C8FF, 0xFFFFFF, 0xFFF2C8, 0xFFC28A, 0xFF9A80}; // blue-white .. red

	private record Star(int bucket, float[] v, int color) {}

	private static NightSky instance; // for prepare(): rebuilds the star buffer outside any render pass

	private GpuBuffer starBuffer;
	private int builtStars;
	private final GpuBuffer cometBuffer;
	private GpuBuffer indices; // set by buildStars: covers stars and comet
	private final int[] bucketStars = new int[BUCKETS];
	private final float[] freq = new float[BUCKETS];
	private final float[] phase = new float[BUCKETS];
	private final float[] depth = new float[BUCKETS];

	private final Quaternionf cometOrient = new Quaternionf();
	private float arcStart;
	private float arcLen;

	public NightSky() {
		Random r = new Random(99);
		for (int b = 0; b < BUCKETS; b++) {
			freq[b] = (0.4F + 1.8F * r.nextFloat()) * Mth.TWO_PI; // 0.4-2.2 Hz
			phase[b] = r.nextFloat() * Mth.TWO_PI;
			depth[b] = 0.2F + 0.4F * r.nextFloat();                // twinkle strength
		}
		starBuffer = buildStars(SkyboxConfig.starCount);
		builtStars = SkyboxConfig.starCount;
		cometBuffer = buildComet();
		instance = this;
	}

	/**
	 * Call from a point where no render pass is open (LevelRenderEvents.END_EXTRACTION): when the star-count
	 * setting changed, builds the new star buffer. Creating buffers inside the sky pass would hit the same
	 * "Close the existing render pass" error as the aurora upload did.
	 */
	public static void prepare() {
		NightSky n = instance;
		if (n != null && n.builtStars != SkyboxConfig.starCount) {
			n.rebuildStars();
		}
	}

	private void rebuildStars() {
		GpuBuffer oldStars = starBuffer;
		GpuBuffer oldIndices = indices;
		java.util.Arrays.fill(bucketStars, 0);
		starBuffer = buildStars(SkyboxConfig.starCount);
		builtStars = SkyboxConfig.starCount;
		oldStars.close();
		oldIndices.close();
	}

	private GpuBuffer buildStars(int count) {
		RandomSource pos = RandomSource.createThreadLocalInstance(10842L); // vanilla seed + draw order = vanilla constellations
		Random look = new Random(4242);
		List<Star> stars = new ArrayList<>();
		// vanilla draws 3000 rolls and about half pass the filter; here we keep rolling until `count` stars are accepted
		for (int i = 0; stars.size() < count && i < count * 20; i++) {
			float x = pos.nextFloat() * 2.0F - 1.0F;
			float y = pos.nextFloat() * 2.0F - 1.0F;
			float z = pos.nextFloat() * 2.0F - 1.0F;
			float r = pos.nextFloat();
			float lenSq = Mth.lengthSquared(x, y, z);
			if (lenSq <= 0.010000001F || lenSq >= 1.0F) {
				continue;
			}
			Vector3f c = new Vector3f(x, y, z).normalize(100.0F);
			float zRot = (float) (pos.nextDouble() * Math.PI * 2.0);

			float roll = look.nextFloat();
			float size, bright;
			if (roll < 0.03F) { // few big bright stars
				size = 0.35F + 0.15F * look.nextFloat();
				bright = 1.0F;
			} else if (roll < 0.20F) {
				size = 0.22F + 0.10F * r;
				bright = 0.7F + 0.3F * look.nextFloat();
			} else {
				size = 0.12F + 0.10F * r; // vanilla was 0.15-0.25
				bright = 0.35F + 0.5F * look.nextFloat();
			}
			float t = look.nextFloat();
			int tint = TINTS[t < 0.2F ? 0 : t < 0.6F ? 1 : t < 0.8F ? 2 : t < 0.92F ? 3 : 4];

			Matrix3f rot = new Matrix3f().rotateTowards(new Vector3f(c).negate(), new Vector3f(0.0F, 1.0F, 0.0F)).rotateZ(-zRot);
			float[] v = new float[12];
			float[][] corners = {{size, -size}, {size, size}, {-size, size}, {-size, -size}};
			for (int k = 0; k < 4; k++) {
				Vector3f p = new Vector3f(corners[k][0], corners[k][1], 0.0F).mul(rot).add(c);
				v[k * 3] = p.x;
				v[k * 3 + 1] = p.y;
				v[k * 3 + 2] = p.z;
			}
			stars.add(new Star(look.nextInt(BUCKETS), v, SkyPalette.mix(0x000000, tint, bright)));
		}
		stars.sort(Comparator.comparingInt(Star::bucket)); // contiguous per bucket -> one draw each
		for (Star s : stars) {
			bucketStars[s.bucket()]++;
		}
		indices = SkyboxPipelines.quadIndices(stars.size());

		try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(stars.size() * 4 * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
			BufferBuilder b = new BufferBuilder(bb, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
			for (Star s : stars) {
				for (int k = 0; k < 4; k++) {
					b.addVertex(s.v()[k * 3], s.v()[k * 3 + 1], s.v()[k * 3 + 2]).setColor(s.color());
				}
			}
			try (MeshData mesh = b.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "Night stars", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
			}
		}
	}

	/** Comet in local space: head at (0,100,0), tail trailing along +X, squares shrinking and fading. */
	private GpuBuffer buildComet() {
		try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized((2 + COMET_TAIL) * 4 * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
			BufferBuilder b = new BufferBuilder(bb, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
			quad(b, 0.0F, 0.9F, 0x30B0D0FF); // soft halo
			quad(b, 0.0F, 0.28F, 0xFFFFFFFF); // head
			for (int k = 1; k <= COMET_TAIL; k++) {
				float f = 1.0F - (float) k / (COMET_TAIL + 1);
				quad(b, 0.3F + k * 0.4F, 0.04F + 0.2F * f, (Math.round(0xB0 * f * f) << 24) | 0xA8D8FF);
			}
			try (MeshData mesh = b.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "Comet", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
			}
		}
	}

	private static void quad(BufferBuilder b, float cx, float h, int argb) {
		b.addVertex(cx - h, 100.0F, -h).setColor(argb);
		b.addVertex(cx - h, 100.0F, h).setColor(argb);
		b.addVertex(cx + h, 100.0F, h).setColor(argb);
		b.addVertex(cx + h, 100.0F, -h).setColor(argb);
	}

	/**
	 * Stateless: a comet is fully defined by its slot number (dice, launch tick, orientation), so sleeping, time skips
	 * and reloads stay consistent. Returns launch tick offset inside the slot, or -1 when this slot has no comet.
	 */
	private float rollComet(long slot) {
		SplittableRandom r = new SplittableRandom(slot * 0x9E3779B97F4A7C15L);
		int roll = r.nextInt(100);
		float start = (float) (r.nextDouble() * COMET_SLOT);
		Vector3f axis = new Vector3f((float) r.nextDouble() * 2 - 1, (float) r.nextDouble() * 2 - 1, (float) r.nextDouble() * 2 - 1);
		if (axis.lengthSquared() < 1.0E-4F) axis.set(0.0F, 1.0F, 0.0F);
		axis.normalize();
		cometOrient.identity().rotateAxis((float) (r.nextDouble() * Math.PI * 2.0), axis.x, axis.y, axis.z);
		arcStart = -COMET_ARC / 2.0F;
		arcLen = COMET_ARC;
		float percent = SkyboxConfig.cometPercent;
		if (showerSlot(slot * COMET_SLOT)) {
			percent = Math.min(100.0F, percent * MeteorShower.cometBoost(SkyboxConfig.meteorShowerIntensity));
			aimFromRadiant(r, slot * COMET_SLOT, (float) r.nextDouble() * 0.25F, COMET_ARC);
		}
		return roll < percent ? start : -1.0F;
	}

	private float rollMeteor(long slot) {
		long tick = slot * METEOR_SLOT;
		if (!showerSlot(tick)) return -1.0F;
		SplittableRandom r = new SplittableRandom(slot * 0xC2B2AE3D27D4EB4FL + 17L);
		int roll = r.nextInt(100);
		float start = (float) (r.nextDouble() * METEOR_SLOT);
		aimFromRadiant(r, tick, 0.04F + (float) r.nextDouble() * 0.4F, METEOR_ARC);
		return roll < MeteorShower.meteorPercent(SkyboxConfig.meteorShowerIntensity) ? start : -1.0F;
	}

	private static boolean showerSlot(long tick) {
		return MeteorShower.isShowerDay(tick, SkyboxConfig.meteorShowerEnabled, SkyboxConfig.meteorShowerEveryDays);
	}

	private void aimFromRadiant(SplittableRandom r, long tick, float from, float length) {
		float[] b = MeteorShower.basis(Math.floorDiv(tick, MeteorShower.DAY_TICKS), r);
		cometOrient.setFromNormalized(new Matrix3f(b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8]));
		arcStart = from;
		arcLen = length;
	}

	private static float smooth(float x) {
		x = Math.max(0.0F, Math.min(1.0F, x));
		return x * x * (3.0F - 2.0F * x);
	}

	private float twinkle(int b, double t) {
		double a = 0.5 + 0.5 * Math.sin(freq[b] * t + phase[b]);
		double c = 0.5 + 0.5 * Math.sin(freq[b] * 1.7 * t + phase[b] * 2.0);
		return (float) ((1.0 - depth[b] * a) * (1.0 - 0.4 * depth[b] * c));
	}

	private static GpuBufferSlice transform(Matrix4fStack mv, float alpha) {
		return RenderSystem.getDynamicUniforms().writeTransform(mv, new Vector4f(1.0F, 1.0F, 1.0F, alpha), new Vector3f(), new Matrix4f());
	}

	/** Called with the pass and pose vanilla renderStars gets (star rotation already applied); draws into that pass. */
	public void render(RenderPass pass, PoseStack poseStack, float starBrightness) {
		double t = (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000.0; // real seconds, twinkle only
		Matrix4fStack mv = RenderSystem.getModelViewStack();
		mv.pushMatrix();
		mv.mul(poseStack.last().pose());

		GpuBufferSlice[] slices = new GpuBufferSlice[BUCKETS];
		for (int b = 0; b < BUCKETS; b++) {
			slices[b] = transform(mv, starBrightness * twinkle(b, t));
		}

		List<GpuBufferSlice> cometSlices = new ArrayList<>();
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			double ticks = level.getGameTime() + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			long now = Math.floorDiv((long) ticks, COMET_SLOT);
			for (long slot = now; slot > now - COMET_SLOTS_BACK; slot--) {
				float start = rollComet(slot);
				if (start < 0.0F) {
					continue;
				}
				float p = (float) ((ticks - slot * COMET_SLOT - start) / COMET_LIFE); // 0..1 over its life
				if (p < 0.0F || p >= 1.0F) {
					continue;
				}
				float fade = smooth(p / 0.3F) * smooth((1.0F - p) / 0.4F); // long smooth fade in and out
				mv.pushMatrix();
				// great-circle flight; head moves toward +Z, so the tail (+X turned by 90 deg about Y) points to -Z, behind it
				mv.rotate(cometOrient).rotateX(arcStart + p * arcLen).rotateY((float) (Math.PI / 2.0));
				cometSlices.add(transform(mv, starBrightness * fade));
				mv.popMatrix();
			}
			long nowMeteor = Math.floorDiv((long) ticks, METEOR_SLOT);
			for (long slot = nowMeteor; slot > nowMeteor - METEOR_SLOTS_BACK; slot--) {
				float start = rollMeteor(slot);
				if (start < 0.0F) continue;
				float p = (float) ((ticks - slot * METEOR_SLOT - start) / METEOR_LIFE);
				if (p < 0.0F || p >= 1.0F) continue;
				float fade = smooth(p / 0.15F) * smooth((1.0F - p) / 0.5F);
				mv.pushMatrix();
				mv.rotate(cometOrient).rotateX(arcStart + p * arcLen).rotateY((float) (Math.PI / 2.0));
				cometSlices.add(transform(mv, starBrightness * fade));
				mv.popMatrix();
			}
		}

		pass.setPipeline(RenderSystem.getCompiledPipeline(SkyboxPipelines.NIGHT));
		RenderSystem.bindDefaultUniforms(pass);
		pass.setIndexBuffer(indices, IndexType.INT);
		pass.setVertexBuffer(0, starBuffer.slice());
		int baseVertex = 0;
		for (int b = 0; b < BUCKETS; b++) {
			if (bucketStars[b] > 0) {
				pass.setUniform("DynamicTransforms", slices[b]);
				pass.drawIndexed(bucketStars[b] * 6, 1, 0, baseVertex, 0);
				baseVertex += bucketStars[b] * 4;
			}
		}
		if (!cometSlices.isEmpty()) {
			pass.setVertexBuffer(0, cometBuffer.slice());
			for (GpuBufferSlice c : cometSlices) {
				pass.setUniform("DynamicTransforms", c);
				pass.drawIndexed((2 + COMET_TAIL) * 6, 1, 0, 0, 0);
			}
		}

		mv.popMatrix();
	}

	@Override
	public void close() {
		if (instance == this) {
			instance = null;
		}
		starBuffer.close();
		cometBuffer.close();
		indices.close();
	}
}
