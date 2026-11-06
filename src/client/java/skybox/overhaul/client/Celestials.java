package skybox.overhaul.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.MoonPhase;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Custom sun and moon (own 1024x1024 textures with the glow baked in), drawn instead of vanilla's when
 * SkyboxConfig.customCelestials is on. Same place in the sky, same geometry and UV order as vanilla's quads
 * (the 1.21.4 renderSun/renderMoon), just bigger quads so the body keeps vanilla's apparent size and the halo
 * fits around it.
 *
 * Both bodies live in the sun's rotating frame (vanilla draws them in one frame: sun at +Y, moon opposite at -Y),
 * so the moon reuses the pose captured in renderSun.
 */
public final class Celestials implements AutoCloseable {
	// ===== EDIT SUN / MOON HERE =====
	/** Vanilla quad: sun +-30, moon +-20. The bodies fill 389 px (sun square) and 263 px (moon) of 1024 in the PNGs. */
	private static final float SUN_HALF = 30.0F * 1024.0F / 389.0F;
	private static final float MOON_HALF = 20.0F * 1024.0F / 263.0F;
	private static final float SUN_HEIGHT = 426;
	private static final float MOON_HEIGHT = -426;
	/** Flip if the moon phases look mirrored in game (lit side of a waxing moon should face the setting sun). */
	private static final boolean MIRROR_MOON = false;
	// =================================

	private static final String NS = "skybox-overhaul";

	private final GpuBuffer sunQuad;
	private final GpuBuffer moonQuad;
	private final Matrix4f sunPose = new Matrix4f();
	private boolean sunPoseValid;

	public Celestials() {
		// vanilla renderSun: (-30,100,-30)uv(0,0) (30,100,-30)uv(1,0) (30,100,30)uv(1,1) (-30,100,30)uv(0,1)
		sunQuad = quad("Custom sun",
			-1, -1, 0, 0,   1, -1, 1, 0,   1, 1, 1, 1,   -1, 1, 0, 1);
		// vanilla renderMoon: (-20,-100,20)uv(u1,v1) (20,-100,20)uv(u0,v1) (20,-100,-20)uv(u0,v0) (-20,-100,-20)uv(u1,v0)
		float a = MIRROR_MOON ? 0.0F : 1.0F;
		float b = 1.0F - a;
		moonQuad = quad("Custom moon",
			-1, 1, a, 1,   1, 1, b, 1,   1, -1, b, 0,   -1, -1, a, 0);
	}

	private static GpuBuffer quad(String name, float... v) { // 4 x (x, z, u, v), y = 0
		try (ByteBufferBuilder bb = ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
			BufferBuilder b = new BufferBuilder(bb, Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			for (int i = 0; i < 4; i++) {
				b.addVertex(v[i * 4], 0.0F, v[i * 4 + 1]).setUv(v[i * 4 + 2], v[i * 4 + 3]).setColor(0xFFFFFFFF);
			}
			try (MeshData mesh = b.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> name, 32, mesh.vertexBuffer());
			}
		}
	}

	/** Called with the pose vanilla renderSun gets (sun rotation applied). Always call it, the moon needs the pose. */
	public void captureSunPose(Matrix4f pose) {
		sunPose.set(pose);
		sunPoseValid = true;
	}

	public void renderSun(float alpha) {
		if (sunPoseValid) {
			draw(sunQuad, Identifier.fromNamespaceAndPath(NS, "textures/environment/celestial/sun.png"), SUN_HALF, SUN_HEIGHT, alpha, "Custom sun");
		}
	}

	/** @return false when it could not draw (no sun pose yet), so the caller lets vanilla draw the moon. */
	public boolean renderMoon(MoonPhase phase, float alpha) {
		if (!sunPoseValid) {
			return false;
		}
		String name = phase.name().toLowerCase(Locale.ROOT); // full_moon, waning_gibbous, ... = the PNG names
		draw(moonQuad, Identifier.fromNamespaceAndPath(NS, "textures/environment/celestial/" + name + ".png"), MOON_HALF, MOON_HEIGHT, alpha, "Custom moon");
		return true;
	}

	private void draw(GpuBuffer quad, Identifier texture, float half, float height, float alpha, String label) {
		AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
		Matrix4fStack mv = RenderSystem.getModelViewStack();
		mv.pushMatrix();
		mv.mul(sunPose).translate(0.0F, height, 0.0F).scale(half, 1.0F, half);
		GpuBufferSlice slice = RenderSystem.getDynamicUniforms().writeTransform(mv, new Vector4f(1.0F, 1.0F, 1.0F, alpha), new Vector3f(), new Matrix4f());
		mv.popMatrix();

		GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
		GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
		RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(Mode.QUADS);
		GpuBuffer indices = quads.getBuffer(6);

		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> label, color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
			pass.setPipeline(SkyboxPipelines.CELESTIAL);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", slice);
			// NEAREST keeps the pixel-art body crisp; the PNGs are 1024 px so the baked glow stays smooth anyway.
			pass.bindTexture("Sampler0", tex.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.setIndexBuffer(indices, quads.type());
			pass.setVertexBuffer(0, quad);
			pass.drawIndexed(0, 0, 6, 1);
		}
	}

	@Override
	public void close() {
		sunQuad.close();
		moonQuad.close();
	}
}
