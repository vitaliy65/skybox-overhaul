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
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Vanilla's sky disc is solid fog colour beyond render distance, and the ramp into that solid colour
 * shows as a hard line. This draws a cylinder in the exact terrain fog colour: opaque up to where the
 * line is, then eased to transparent, so the sky melts into the horizon with no seam.
 * Cylinder is built once; its height is scaled per frame to the render distance.
 */
public final class HorizonHaze implements AutoCloseable {
	private static final int SEGMENTS = 32;
	private static final int EASE_STEPS = 8;
	private static final float RADIUS = 100.0F;
	private static final float PLATEAU = 10.0F; // y where alpha starts to fall (tan = 0.1)
	private static final float TOP = 60.0F;     // y where alpha reaches 0
	private static final float BOTTOM = -30.0F;
	private static final int QUADS = SEGMENTS * (1 + EASE_STEPS);

	private final GpuBuffer buffer;

	public HorizonHaze() {
		try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(QUADS * 4 * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
			BufferBuilder b = new BufferBuilder(bb, Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
			float[] ys = new float[EASE_STEPS + 2];
			float[] alphas = new float[EASE_STEPS + 2];
			ys[0] = BOTTOM;
			alphas[0] = 1.0F;
			ys[1] = PLATEAU;
			alphas[1] = 1.0F;
			for (int i = 1; i <= EASE_STEPS; i++) {
				float t = (float) i / EASE_STEPS;
				ys[i + 1] = PLATEAU + (TOP - PLATEAU) * t;
				alphas[i + 1] = 1.0F - t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F); // smootherstep: no slope jump at either end
			}
			for (int j = 0; j < SEGMENTS; j++) {
				double a0 = Math.PI * 2.0 * j / SEGMENTS;
				double a1 = Math.PI * 2.0 * (j + 1) / SEGMENTS;
				float x0 = (float) (RADIUS * Math.cos(a0)), z0 = (float) (RADIUS * Math.sin(a0));
				float x1 = (float) (RADIUS * Math.cos(a1)), z1 = (float) (RADIUS * Math.sin(a1));
				for (int i = 0; i < ys.length - 1; i++) {
					int lo = (Math.round(alphas[i] * 255.0F) << 24) | 0xFFFFFF;
					int hi = (Math.round(alphas[i + 1] * 255.0F) << 24) | 0xFFFFFF;
					b.addVertex(x0, ys[i], z0).setColor(lo);
					b.addVertex(x1, ys[i], z1).setColor(lo);
					b.addVertex(x1, ys[i + 1], z1).setColor(hi);
					b.addVertex(x0, ys[i + 1], z0).setColor(hi);
				}
			}
			try (MeshData mesh = b.buildOrThrow()) {
				buffer = RenderSystem.getDevice().createBuffer(() -> "Horizon haze", 32, mesh.vertexBuffer());
			}
		}
	}

	public void render() {
		if (!SkyboxState.valid) {
			return;
		}
		// Disc is fully fogged where its horizontal distance passes render distance:
		// tan(elevation) = 16 / (chunks * 16) = 1 / chunks (disc sits 16 blocks up). Align plateau top with that.
		float scaleY = Mth.clamp(1.15F * (RADIUS * (1.0F / SkyboxState.renderDistanceChunks)) / PLATEAU, 0.3F, 3.0F);
		Vector4f fog = SkyboxState.FOG;

		Matrix4fStack mv = RenderSystem.getModelViewStack();
		mv.pushMatrix();
		mv.scale(1.0F, scaleY, 1.0F);
		GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(mv, new Vector4f(fog.x, fog.y, fog.z, 1.0F), new Vector3f(), new Matrix4f());
		mv.popMatrix();

		GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
		GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
		RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(Mode.QUADS);
		GpuBuffer indices = quads.getBuffer(QUADS * 6);

		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Horizon haze", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
			pass.setPipeline(SkyboxPipelines.HAZE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			pass.setVertexBuffer(0, buffer);
			pass.setIndexBuffer(indices, quads.type());
			pass.drawIndexed(0, 0, QUADS * 6, 1);
		}
	}

	@Override
	public void close() {
		buffer.close();
	}
}
