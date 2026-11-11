package skybox.overhaul.client.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import java.util.function.DoubleUnaryOperator;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import skybox.overhaul.client.render.SkyboxPipelines;
import skybox.overhaul.client.render.SkyboxState;
import skybox.overhaul.client.sky.SkyPalette;
import skybox.overhaul.client.config.SkyboxConfig;
import skybox.overhaul.client.sky.FogMist;
import skybox.overhaul.client.sky.DistantStorm;
import skybox.overhaul.client.sky.GlowLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Paints the whole vertical sky gradient ourselves, so vanilla's fog ramp (and the line it leaves at
 * render distance) is covered. Two translucent domes, drawn right after the vanilla disc:
 *  1. base: zenith colour, opaque up to 55 degrees, eased out toward the zenith (where vanilla is already zenith colour);
 *  2. haze: terrain fog colour, opaque at/below the horizon, decaying like real atmospheric haze
 *     (exp(-(elev/9deg)^1.4): ~65% at 5deg, ~30% at 10deg, ~5% at 20deg).
 * Colours come from vanilla values (already time-of-day palettes), so the gradient follows the sky.
 */
public final class HorizonHaze implements AutoCloseable {
	private static final int SEGMENTS = 48;
	private static final float R = 100.0F;
	private static final float[] BASE_DEG = {-30, 55, 62, 69, 76, 83, 89};
	private static final float[] HAZE_DEG = {-30, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 40};
	private static final int BASE_QUADS = SEGMENTS * (BASE_DEG.length - 1);
	private static final int HAZE_QUADS = SEGMENTS * (HAZE_DEG.length - 1);
    private static final int FLASH_COLUMNS = 36;
    private static final float[] FLASH_DEG = {-8, 0, 1.5F, 3, 5, 7, 9.5F, 12, 15, 19, 24, 30};
    private static final int FLASH_QUADS = FLASH_COLUMNS * (FLASH_DEG.length - 1);
    private static final int FLASH_BASE_VERTEX = (BASE_QUADS + HAZE_QUADS) * 4;
    private static final int ALL_QUADS = BASE_QUADS + HAZE_QUADS + FLASH_QUADS;
    private static final float[] FLASH_RGB = {0.80F, 0.85F, 1.0F};
    private static final float FLASH_MAX_ALPHA = 0.75F;

	private final GpuBuffer buffer;
	private final GpuBuffer indices = SkyboxPipelines.quadIndices(ALL_QUADS);

	public HorizonHaze() {
		try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(ALL_QUADS * 4 * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
			BufferBuilder b = new BufferBuilder(bb, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
			dome(b, BASE_DEG, e -> {
				double t = Math.max(0.0, Math.min(1.0, (e - 55.0) / 34.0));
				return 1.0 - t * t * (3.0 - 2.0 * t);
			});
			dome(b, HAZE_DEG, e -> e <= 0.0 ? 1.0 : Math.exp(-Math.pow(e / 9.0, 1.4))); // tune: 9.0 = haze height, 1.4 = softness
			try (MeshData mesh = b.buildOrThrow()) {
				buffer = RenderSystem.getDevice().createBuffer(() -> "Horizon haze", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
			}
		}
	}

	private static void dome(BufferBuilder b, float[] deg, DoubleUnaryOperator alpha) {
		for (int j = 0; j < SEGMENTS; j++) {
			double a0 = Math.PI * 2.0 * j / SEGMENTS;
			double a1 = Math.PI * 2.0 * (j + 1) / SEGMENTS;
			for (int i = 0; i < deg.length - 1; i++) {
				vertex(b, deg[i], a0, alpha);
				vertex(b, deg[i], a1, alpha);
				vertex(b, deg[i + 1], a1, alpha);
				vertex(b, deg[i + 1], a0, alpha);
			}
		}
	}

	private static void vertex(BufferBuilder b, double elevDeg, double azimuth, DoubleUnaryOperator alpha) {
		double e = Math.toRadians(elevDeg);
		double c = R * Math.cos(e);
		int a = (int) Math.round(alpha.applyAsDouble(elevDeg) * 255.0);
		b.addVertex((float) (c * Math.cos(azimuth)), (float) (R * Math.sin(e)), (float) (c * Math.sin(azimuth))).setColor((a << 24) | 0xFFFFFF);
	}

	private static GpuBufferSlice transform(float r, float g, float bl, float alpha) {
		return RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(r, g, bl, alpha), new Vector3f(), new Matrix4f());
	}

	/** Draws into vanilla's sky pass (26.3 hands the pass to the sky methods; a second pass cannot be opened inside it). */
	public void render(RenderPass pass, int skyColor) {
		if (!SkyboxState.valid) return;
		float a = SkyboxState.rainBrightness;
		Vector4f fog = SkyboxState.FOG;
		GpuBufferSlice base = transform(((skyColor >> 16) & 0xFF) / 255.0F, ((skyColor >> 8) & 0xFF) / 255.0F, (skyColor & 0xFF) / 255.0F, a);
		GpuBufferSlice haze = transform(fog.x, fog.y, fog.z, a);
		float mist = Math.max(SkyPalette.dawnStrength(SkyboxState.sunAngle) * 0.35F, FogMist.current() * 0.30F) * a;
		GpuBufferSlice mistSlice = mist > 0.01F ? transform(fog.x, fog.y, fog.z, mist) : null;
		GpuBufferSlice[] flashes = flashSlices();
		pass.setPipeline(RenderSystem.getCompiledPipeline(SkyboxPipelines.HAZE));
		RenderSystem.bindDefaultUniforms(pass);
		pass.setVertexBuffer(0, buffer.slice());
		pass.setIndexBuffer(indices, IndexType.INT);
		pass.setUniform("DynamicTransforms", base);
		pass.drawIndexed(BASE_QUADS * 6, 1, 0, 0, 0);
		pass.setUniform("DynamicTransforms", haze);
		pass.drawIndexed(HAZE_QUADS * 6, 1, 0, BASE_QUADS * 4, 0);
		if (mistSlice != null) {
			pass.setUniform("DynamicTransforms", mistSlice);
			pass.drawIndexed(HAZE_QUADS * 6, 1, 0, BASE_QUADS * 4, 0);
		}
		for (GpuBufferSlice flash : flashes) {
			pass.setUniform("DynamicTransforms", flash);
			pass.drawIndexed(FLASH_QUADS * 6, 1, 0, FLASH_BASE_VERTEX, 0);
		}
	}

	private static GpuBufferSlice[] flashSlices() {
		ClientLevel level = Minecraft.getInstance().level;
		float rain = SkyboxState.rainLevel;
		if (!SkyboxConfig.distantStormEnabled || level == null || rain < 0.3F) return new GpuBufferSlice[0];
		double ticks = level.getGameTime() + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float[] azimuth = new float[2], power = new float[2];
		int count = DistantStorm.flashes(ticks, DistantStorm.chance(SkyboxConfig.distantStormFrequency, rain), azimuth, power);
		float sunHeight = GlowLayers.sunElevationDeg(SkyboxState.sunAngle);
		float dark = Math.max(0.0F, Math.min(1.0F, (10.0F - sunHeight) / 18.0F));
		float brightness = SkyboxConfig.distantStormBrightness * (0.35F + 0.65F * dark) * FLASH_MAX_ALPHA;
		GpuBufferSlice[] slices = new GpuBufferSlice[count];
		for (int i = 0; i < count; i++) {
			Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrixCopy()).rotateY(azimuth[i]);
			slices[i] = RenderSystem.getDynamicUniforms().writeTransform(mv,
				new Vector4f(FLASH_RGB[0], FLASH_RGB[1], FLASH_RGB[2], power[i] * brightness), new Vector3f(), new Matrix4f());
		}
		return slices;
	}

	@Override
	public void close() {
		buffer.close();
		indices.close();
	}
}
