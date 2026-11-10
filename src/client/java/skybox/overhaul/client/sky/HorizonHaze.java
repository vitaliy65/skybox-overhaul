package skybox.overhaul.client.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.PrimitiveTopology;
import java.util.OptionalDouble;
import java.util.Optional;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import skybox.overhaul.client.render.SkyboxPipelines;
import skybox.overhaul.client.render.SkyboxState;

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

	private final GpuBuffer buffer;

	public HorizonHaze() {
		try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized((BASE_QUADS + HAZE_QUADS) * 4 * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
			BufferBuilder b = new BufferBuilder(bb, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
			dome(b, BASE_DEG, e -> {
				double t = Math.max(0.0, Math.min(1.0, (e - 55.0) / 34.0));
				return 1.0 - t * t * (3.0 - 2.0 * t);
			});
			dome(b, HAZE_DEG, e -> e <= 0.0 ? 1.0 : Math.exp(-Math.pow(e / 9.0, 1.4))); // tune: 9.0 = haze height, 1.4 = softness
			try (MeshData mesh = b.buildOrThrow()) {
				buffer = RenderSystem.getDevice().createBuffer(() -> "Horizon haze", 32, mesh.vertexBuffer());
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

	public void render(int skyColor) {
		if (!SkyboxState.valid) {
			return;
		}
		float a = SkyboxState.rainBrightness; // fade to vanilla in rain
		Vector4f fog = SkyboxState.FOG;
		GpuBufferSlice base = transform(((skyColor >> 16) & 0xFF) / 255.0F, ((skyColor >> 8) & 0xFF) / 255.0F, (skyColor & 0xFF) / 255.0F, a);
		GpuBufferSlice haze = transform(fog.x, fog.y, fog.z,
			Math.min(1.0F, a * (1.0F + SkyPalette.dawnStrength(SkyboxState.sunAngle) * 0.14F)));

		GpuTextureView color = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
		GpuTextureView depth = Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView();
		RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer indices = quads.getBuffer((BASE_QUADS + HAZE_QUADS) * 6);

		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Horizon haze", color, Optional.empty(), depth, OptionalDouble.empty())) {
			pass.setPipeline(SkyboxPipelines.HAZE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setVertexBuffer(0, buffer.slice());
			pass.setIndexBuffer(indices, quads.type());
			pass.setUniform("DynamicTransforms", base);
			pass.drawIndexed(BASE_QUADS * 6, 1, 0, 0, 0);
			pass.setUniform("DynamicTransforms", haze);
			pass.drawIndexed(HAZE_QUADS * 6, 1, 0, BASE_QUADS * 4, 0);
		}
	}

	@Override
	public void close() {
		buffer.close();
	}
}
