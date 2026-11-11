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
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import skybox.overhaul.client.SkyboxPipelines;
import skybox.overhaul.client.SkyboxState;
import skybox.overhaul.client.config.SkyboxConfig;

/**
 * Paints the whole vertical sky gradient ourselves, so vanilla's fog ramp (and the line it leaves at
 * render distance) is covered. Two translucent domes, drawn right after the vanilla disc:
 *  1. base: zenith colour, opaque up to 55 degrees, eased out toward the zenith (where vanilla is already zenith colour);
 *  2. haze: terrain fog colour, opaque at/below the horizon, decaying like real atmospheric haze
 *     (exp(-(elev/9deg)^1.4): ~65% at 5deg, ~30% at 10deg, ~5% at 20deg).
 * Colours come from vanilla values (already time-of-day palettes), so the gradient follows the sky.
 * Same buffer, third mesh: the glow of a far, silent thunderstorm. A flat patch of light low on the horizon (soft in
 * height and in width, baked once around azimuth 0) that is turned to a random side and flashed while it rains.
 * It is drawn right here, in the sky pass, so clouds and terrain stay in front of it.
 */
public final class HorizonHaze implements AutoCloseable {
	private static final int SEGMENTS = 48;
	private static final float R = 100.0F;
	private static final float[] BASE_DEG = {-30, 55, 62, 69, 76, 83, 89};
	private static final float[] HAZE_DEG = {-30, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 40};
	private static final int BASE_QUADS = SEGMENTS * (BASE_DEG.length - 1);
	private static final int HAZE_QUADS = SEGMENTS * (HAZE_DEG.length - 1);
	// far storm glow: 10 degree columns around the circle, rows reach ~30 degrees up
	private static final int FLASH_COLUMNS = 36;
	private static final float[] FLASH_DEG = {-8, 0, 1.5F, 3, 5, 7, 9.5F, 12, 15, 19, 24, 30};
	private static final int FLASH_QUADS = FLASH_COLUMNS * (FLASH_DEG.length - 1);
	private static final int FLASH_BASE_VERTEX = (BASE_QUADS + HAZE_QUADS) * 4;
	private static final int ALL_QUADS = BASE_QUADS + HAZE_QUADS + FLASH_QUADS;
	/** Pale blue-white lightning colour. */
	private static final float[] FLASH_RGB = {0.80F, 0.85F, 1.0F};
	private static final float FLASH_MAX_ALPHA = 0.75F;

	private final GpuBuffer buffer;

	public HorizonHaze() {
		try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(ALL_QUADS * 4 * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
			BufferBuilder b = new BufferBuilder(bb, Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
			dome(b, BASE_DEG, e -> {
				double t = Math.max(0.0, Math.min(1.0, (e - 55.0) / 34.0));
				return 1.0 - t * t * (3.0 - 2.0 * t);
			});
			dome(b, HAZE_DEG, e -> e <= 0.0 ? 1.0 : Math.exp(-Math.pow(e / 9.0, 1.4))); // tune: 9.0 = haze height, 1.4 = softness
			flashPatch(b);
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

	/** Glow patch centred on azimuth 0: alpha = height falloff (tune 11 = glow height) * gaussian width (tune 32 = degrees). */
	private static void flashPatch(BufferBuilder b) {
		for (int j = 0; j < FLASH_COLUMNS; j++) {
			for (int i = 0; i < FLASH_DEG.length - 1; i++) {
				flashVertex(b, FLASH_DEG[i], j);
				flashVertex(b, FLASH_DEG[i], j + 1);
				flashVertex(b, FLASH_DEG[i + 1], j + 1);
				flashVertex(b, FLASH_DEG[i + 1], j);
			}
		}
	}

	private static void flashVertex(BufferBuilder b, float elevDeg, int column) {
		double azimuth = Math.PI * 2.0 * column / FLASH_COLUMNS;
		double offsetDeg = Math.toDegrees(azimuth > Math.PI ? azimuth - Math.PI * 2.0 : azimuth); // -180..180 from the centre
		double height = elevDeg <= 0.0F ? 1.0 : Math.exp(-Math.pow(elevDeg / 11.0, 1.4));
		double width = Math.exp(-0.5 * (offsetDeg / 32.0) * (offsetDeg / 32.0));
		vertexAlpha(b, elevDeg, azimuth, height * width);
	}

	private static void vertex(BufferBuilder b, double elevDeg, double azimuth, DoubleUnaryOperator alpha) {
		vertexAlpha(b, elevDeg, azimuth, alpha.applyAsDouble(elevDeg));
	}

	private static void vertexAlpha(BufferBuilder b, double elevDeg, double azimuth, double alpha) {
		double e = Math.toRadians(elevDeg);
		double c = R * Math.cos(e);
		int a = (int) Math.round(alpha * 255.0);
		b.addVertex((float) (c * Math.cos(azimuth)), (float) (R * Math.sin(e)), (float) (c * Math.sin(azimuth))).setColor((a << 24) | 0xFFFFFF);
	}

	private static GpuBufferSlice transform(float r, float g, float bl, float alpha) {
		return RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(r, g, bl, alpha), new Vector3f(), new Matrix4f());
	}

	public void render(int skyColor) {
		if (!SkyboxState.valid) {
			return;
		}
		float a = SkyboxState.rainBrightness; // fade to vanilla in rain
		Vector4f fog = SkyboxState.FOG;
		GpuBufferSlice base = transform(((skyColor >> 16) & 0xFF) / 255.0F, ((skyColor >> 8) & 0xFF) / 255.0F, (skyColor & 0xFF) / 255.0F, a);
		GpuBufferSlice haze = transform(fog.x, fog.y, fog.z, a);
		// morning mist: second haze pass lifts alpha where the first fades; thicker together with the terrain fog (FogMist)
		float mist = Math.max(SkyPalette.dawnStrength(SkyboxState.sunAngle) * 0.35F, FogMist.current() * 0.30F) * a;
		GpuBufferSlice mistSlice = mist > 0.01F ? transform(fog.x, fog.y, fog.z, mist) : null;
		GpuBufferSlice[] flashSlices = flashSlices();

		GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
		GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
		RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(Mode.QUADS);
		GpuBuffer indices = quads.getBuffer(ALL_QUADS * 6);

		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Horizon haze", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
			pass.setPipeline(SkyboxPipelines.HAZE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setVertexBuffer(0, buffer);
			pass.setIndexBuffer(indices, quads.type());
			pass.setUniform("DynamicTransforms", base);
			pass.drawIndexed(0, 0, BASE_QUADS * 6, 1);
			pass.setUniform("DynamicTransforms", haze);
			pass.drawIndexed(BASE_QUADS * 4, 0, HAZE_QUADS * 6, 1);
			if (mistSlice != null) {
				pass.setUniform("DynamicTransforms", mistSlice);
				pass.drawIndexed(BASE_QUADS * 4, 0, HAZE_QUADS * 6, 1);
			}
			for (GpuBufferSlice flash : flashSlices) {
				pass.setUniform("DynamicTransforms", flash);
				pass.drawIndexed(FLASH_BASE_VERTEX, 0, FLASH_QUADS * 6, 1);
			}
		}
	}

	/** One uniform per flash alive now (at most 2): the patch turned to its azimuth, tinted, alpha = flash power. */
	private static GpuBufferSlice[] flashSlices() {
		ClientLevel level = Minecraft.getInstance().level;
		float rain = SkyboxState.rainLevel;
		if (!SkyboxConfig.distantStormEnabled || level == null || rain < 0.3F) {
			return new GpuBufferSlice[0];
		}
		double ticks = level.getGameTime() + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float[] azimuth = new float[2];
		float[] power = new float[2];
		int count = DistantStorm.flashes(ticks, DistantStorm.chance(SkyboxConfig.distantStormFrequency, rain), azimuth, power);
		// strongest at night, still visible (weaker) through the grey day
		float sunHeight = GlowLayers.sunElevationDeg(SkyboxState.sunAngle);
		float dark = Math.max(0.0F, Math.min(1.0F, (10.0F - sunHeight) / 18.0F));
		float brightness = SkyboxConfig.distantStormBrightness * (0.35F + 0.65F * dark) * FLASH_MAX_ALPHA;
		GpuBufferSlice[] slices = new GpuBufferSlice[count];
		for (int i = 0; i < count; i++) {
			Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix()).rotateY(azimuth[i]);
			slices[i] = RenderSystem.getDynamicUniforms().writeTransform(mv,
				new Vector4f(FLASH_RGB[0], FLASH_RGB[1], FLASH_RGB[2], power[i] * brightness), new Vector3f(), new Matrix4f());
		}
		return slices;
	}

	@Override
	public void close() {
		buffer.close();
	}
}
