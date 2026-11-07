package skybox.overhaul.client;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class SkyboxPipelines {
	private static final BindGroupLayout SAMPLER0 = BindGroupLayout.builder().withSampler("Sampler0").build();

	/** Vanilla STARS blend (additive-ish) but with per-vertex colour, so each star can have its own tint/brightness. */
	public static final RenderPipeline NIGHT = RenderPipelines.register(
		RenderPipeline.builder()
			.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
			.withLocation("pipeline/skybox_overhaul_night")
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
			.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withCull(false)
			.build());

	/** Custom sun/moon: alpha-blended textured quads (glow baked into the PNG), tested against depth, never written. */
	public static final RenderPipeline CELESTIAL = RenderPipelines.register(
		RenderPipeline.builder()
			.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
			.withLocation("pipeline/skybox_overhaul_celestial")
			.withVertexShader("core/position_tex_color")
			.withFragmentShader("core/position_tex_color")
			.withBindGroupLayout(SAMPLER0)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withCull(false)
			.build());

	/** Plain alpha-blended coloured quads: the horizon haze. */
	public static final RenderPipeline HAZE = RenderPipelines.register(
		RenderPipeline.builder()
			.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
			.withLocation("pipeline/skybox_overhaul_haze")
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withCull(false)
			.build());

	/**
	 * Aurora is ordinary alpha-blended geometry, not an additive light source. It depth-tests
	 * against opaque terrain but never writes depth, so later cloud layers remain visible.
	 * Back-face culling is enabled for closed prisms and transparent quads are sorted back-to-front.
	 */
	public static final RenderPipeline AURORA = RenderPipelines.register(
		RenderPipeline.builder()
			.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
			.withLocation("pipeline/skybox_overhaul_aurora")
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withCull(true)
			.build());

	/** Alpha blending requires back-to-front sorting of the individual prism faces. */
	public static final RenderType AURORA_TYPE = RenderType.create(
		"skybox_overhaul_aurora",
		RenderSetup.builder(AURORA).sortOnUpload().createRenderSetup());

	/** Touch from the client entrypoint so the pipelines register before shaders precompile. */
	public static void init() {}

	private SkyboxPipelines() {}
}
