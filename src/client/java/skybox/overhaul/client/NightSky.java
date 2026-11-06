package skybox.overhaul.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
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

/**
 * Replacement for vanilla renderStars: same 1500 star positions (same RNG stream), square pixel stars,
 * but each star has its own size, colour and brightness, twinkle runs per bucket of stars, plus a rare comet.
 */
public final class NightSky implements AutoCloseable {
	private static final int STARS = 1500;
	private static final int BUCKETS = 16;      // twinkle groups: 16 distinct rhythms, stars assigned at random
	private static final int COMET_TAIL = 14;
	private static final long DAY = 24000L;
	private static final int[] TINTS = {0xB5C8FF, 0xFFFFFF, 0xFFF2C8, 0xFFC28A, 0xFF9A80}; // blue-white .. red

	private record Star(int bucket, float[] v, int color) {}

	private final GpuBuffer starBuffer;
	private final GpuBuffer cometBuffer;
	private final int[] bucketStars = new int[BUCKETS];
	private final float[] freq = new float[BUCKETS];
	private final float[] phase = new float[BUCKETS];
	private final float[] depth = new float[BUCKETS];
	private int starIndexCount;

	private long cometDay = Long.MIN_VALUE;
	private int cometRoll; // 0..99, today's dice: comet shows when below SkyboxConfig.cometPercent (so the slider works instantly)
	private float cometTail;
	private final Quaternionf cometOrient = new Quaternionf();

	public NightSky() {
		Random r = new Random(99);
		for (int b = 0; b < BUCKETS; b++) {
			freq[b] = (0.4F + 1.8F * r.nextFloat()) * Mth.TWO_PI; // 0.4-2.2 Hz
			phase[b] = r.nextFloat() * Mth.TWO_PI;
			depth[b] = 0.2F + 0.4F * r.nextFloat();                // twinkle strength
		}
		starBuffer = buildStars();
		cometBuffer = buildComet();
	}

	private GpuBuffer buildStars() {
		RandomSource pos = RandomSource.createThreadLocalInstance(10842L); // vanilla seed + draw order = vanilla constellations
		Random look = new Random(4242);
		List<Star> stars = new ArrayList<>();
		for (int i = 0; i < STARS; i++) {
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
		starIndexCount = stars.size() * 6;

		try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(stars.size() * 4 * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
			BufferBuilder b = new BufferBuilder(bb, Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
			for (Star s : stars) {
				for (int k = 0; k < 4; k++) {
					b.addVertex(s.v()[k * 3], s.v()[k * 3 + 1], s.v()[k * 3 + 2]).setColor(s.color());
				}
			}
			try (MeshData mesh = b.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "Night stars", 32, mesh.vertexBuffer());
			}
		}
	}

	/** Comet in local space: head at (0,100,0), tail trailing along +X, squares shrinking and fading. */
	private GpuBuffer buildComet() {
		try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized((2 + COMET_TAIL) * 4 * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
			BufferBuilder b = new BufferBuilder(bb, Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
			quad(b, 0.0F, 0.9F, 0x30B0D0FF); // soft halo
			quad(b, 0.0F, 0.28F, 0xFFFFFFFF); // head
			for (int k = 1; k <= COMET_TAIL; k++) {
				float f = 1.0F - (float) k / (COMET_TAIL + 1);
				quad(b, 0.3F + k * 0.4F, 0.04F + 0.2F * f, (Math.round(0xB0 * f * f) << 24) | 0xA8D8FF);
			}
			try (MeshData mesh = b.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "Comet", 32, mesh.vertexBuffer());
			}
		}
	}

	private static void quad(BufferBuilder b, float cx, float h, int argb) {
		b.addVertex(cx - h, 100.0F, -h).setColor(argb);
		b.addVertex(cx - h, 100.0F, h).setColor(argb);
		b.addVertex(cx + h, 100.0F, h).setColor(argb);
		b.addVertex(cx + h, 100.0F, -h).setColor(argb);
	}

	private void updateComet(long day) {
		cometDay = day;
		SplittableRandom r = new SplittableRandom(day * 0x9E3779B97F4A7C15L);
		cometRoll = r.nextInt(100);
		cometTail = (float) (r.nextDouble() * Math.PI * 2.0);
		Vector3f axis = new Vector3f((float) r.nextDouble() * 2 - 1, (float) r.nextDouble() * 2 - 1, (float) r.nextDouble() * 2 - 1);
		if (axis.lengthSquared() < 1.0E-4F) {
			axis.set(0.0F, 1.0F, 0.0F);
		}
		axis.normalize();
		cometOrient.identity().rotateAxis((float) (r.nextDouble() * Math.PI * 2.0), axis.x, axis.y, axis.z);
	}

	private float twinkle(int b, double t) {
		double a = 0.5 + 0.5 * Math.sin(freq[b] * t + phase[b]);
		double c = 0.5 + 0.5 * Math.sin(freq[b] * 1.7 * t + phase[b] * 2.0);
		return (float) ((1.0 - depth[b] * a) * (1.0 - 0.4 * depth[b] * c));
	}

	private static GpuBufferSlice transform(Matrix4fStack mv, float alpha) {
		return RenderSystem.getDynamicUniforms().writeTransform(mv, new Vector4f(1.0F, 1.0F, 1.0F, alpha), new Vector3f(), new Matrix4f());
	}

	/** Called with the same pose vanilla renderStars gets (star rotation already applied). */
	public void render(PoseStack poseStack, float starBrightness) {
		double t = (System.nanoTime() / 1_000_000L % 1_000_000L) / 1000.0; // real seconds, twinkle only
		Matrix4fStack mv = RenderSystem.getModelViewStack();
		mv.pushMatrix();
		mv.mul(poseStack.last().pose());

		GpuBufferSlice[] slices = new GpuBufferSlice[BUCKETS];
		for (int b = 0; b < BUCKETS; b++) {
			slices[b] = transform(mv, starBrightness * twinkle(b, t));
		}

		GpuBufferSlice cometSlice = null;
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			double ticks = level.getGameTime() + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			long day = Math.floorDiv((long) ticks, DAY);
			if (day != cometDay) {
				updateComet(day);
			}
			if (cometRoll < SkyboxConfig.cometPercent) {
				float frac = (float) ((ticks - day * DAY) / DAY);
				mv.pushMatrix();
				// great-circle crawl of ~50 degrees over one day, tail direction fixed per comet
				mv.rotate(cometOrient).rotateX((frac - 0.5F) * 0.9F).rotateY(cometTail);
				cometSlice = transform(mv, starBrightness);
				mv.popMatrix();
			}
		}

		GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
		GpuTextureView depthTex = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
		RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(Mode.QUADS);
		GpuBuffer indices = quads.getBuffer(starIndexCount);

		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Night sky", color, OptionalInt.empty(), depthTex, OptionalDouble.empty())) {
			pass.setPipeline(SkyboxPipelines.NIGHT);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setIndexBuffer(indices, quads.type());
			pass.setVertexBuffer(0, starBuffer);
			int baseVertex = 0;
			for (int b = 0; b < BUCKETS; b++) {
				if (bucketStars[b] > 0) {
					pass.setUniform("DynamicTransforms", slices[b]);
					pass.drawIndexed(baseVertex, 0, bucketStars[b] * 6, 1);
					baseVertex += bucketStars[b] * 4;
				}
			}
			if (cometSlice != null) {
				pass.setUniform("DynamicTransforms", cometSlice);
				pass.setVertexBuffer(0, cometBuffer);
				pass.drawIndexed(0, 0, (2 + COMET_TAIL) * 6, 1);
			}
		}

		mv.popMatrix();
	}

	@Override
	public void close() {
		starBuffer.close();
		cometBuffer.close();
	}
}
