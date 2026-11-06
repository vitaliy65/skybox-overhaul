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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Sunrise / sunset glow that actually fills the sky. Static mesh of {@link GlowLayers} (vertex colours baked
 * in a frame where the sun is at +X); each frame we only rotate it to the sun's side and set one alpha per
 * layer from the sun's elevation. Drawn right after {@link HorizonHaze}, under vanilla's sun, moon and stars.
 */
public final class TwilightGlow implements AutoCloseable {
	private static final float R = 100.0F;
	private static final int LAYERS = GlowLayers.ALL.length;

	private final GpuBuffer buffer;
	private final int[] baseVertex = new int[LAYERS];
	private final int[] quadCount = new int[LAYERS];
	private int totalQuads;

	public TwilightGlow() {
		float[][] rows = new float[LAYERS][];
		for (int i = 0; i < LAYERS; i++) {
			rows[i] = GlowLayers.rows(GlowLayers.ALL[i]);
			quadCount[i] = GlowLayers.COLUMNS * (rows[i].length - 1);
			baseVertex[i] = totalQuads * 4;
			totalQuads += quadCount[i];
		}
		try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(totalQuads * 4 * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
			BufferBuilder b = new BufferBuilder(bb, Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
			for (int i = 0; i < LAYERS; i++) {
				GlowLayers.Layer layer = GlowLayers.ALL[i];
				for (int j = 0; j < GlowLayers.COLUMNS; j++) {
					float az0 = 360.0F * j / GlowLayers.COLUMNS;
					float az1 = 360.0F * (j + 1) / GlowLayers.COLUMNS;
					for (int k = 0; k < rows[i].length - 1; k++) {
						vertex(b, layer, rows[i][k], az0);
						vertex(b, layer, rows[i][k], az1);
						vertex(b, layer, rows[i][k + 1], az1);
						vertex(b, layer, rows[i][k + 1], az0);
					}
				}
			}
			try (MeshData mesh = b.buildOrThrow()) {
				buffer = RenderSystem.getDevice().createBuffer(() -> "Twilight glow", 32, mesh.vertexBuffer());
			}
		}
	}

	private static void vertex(BufferBuilder b, GlowLayers.Layer layer, float elevDeg, float azDeg) {
		double e = Math.toRadians(elevDeg);
		double a = Math.toRadians(azDeg);
		double c = R * Math.cos(e);
		float offset = azDeg > 180.0F ? azDeg - 360.0F : azDeg; // -180..180 from the sun
		b.addVertex((float) (c * Math.cos(a)), (float) (R * Math.sin(e)), (float) (c * Math.sin(a))).setColor(GlowLayers.argb(layer, elevDeg, offset));
	}

	public void render() {
		if (!SkyboxState.valid) {
			return;
		}
		float elev = GlowLayers.sunElevationDeg(SkyboxState.sunAngle);
		float rain = SkyboxState.rainBrightness; // fade to vanilla in rain
		GpuBufferSlice[] slices = new GpuBufferSlice[LAYERS];
		boolean any = false;
		// local +X = sun side; sun sets toward -X, so turn the mesh half a circle in the afternoon
		boolean west = GlowLayers.sunInWest(SkyboxState.sunAngle) != GlowLayers.FLIP_SIDE;
		Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix()).rotateY(west ? (float) Math.PI : 0.0F);
		for (int i = 0; i < LAYERS; i++) {
			float w = GlowLayers.weight(GlowLayers.ALL[i], elev) * rain;
			if (w > 0.002F) {
				slices[i] = RenderSystem.getDynamicUniforms().writeTransform(mv, new Vector4f(1.0F, 1.0F, 1.0F, w), new Vector3f(), new Matrix4f());
				any = true;
			}
		}
		if (!any) {
			return; // daytime / deep night: nothing to draw
		}

		GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
		GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
		RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(Mode.QUADS);
		GpuBuffer indices = quads.getBuffer(totalQuads * 6);

		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Twilight glow", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
			pass.setPipeline(SkyboxPipelines.HAZE); // plain alpha-blended position_color quads, same as the haze
			RenderSystem.bindDefaultUniforms(pass);
			pass.setVertexBuffer(0, buffer);
			pass.setIndexBuffer(indices, quads.type());
			for (int i = 0; i < LAYERS; i++) {
				if (slices[i] != null) {
					pass.setUniform("DynamicTransforms", slices[i]);
					pass.drawIndexed(baseVertex[i], 0, quadCount[i] * 6, 1);
				}
			}
		}
	}

	@Override
	public void close() {
		buffer.close();
	}
}
