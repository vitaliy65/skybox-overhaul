package skybox.overhaul.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class SkyboxPipelines {
	/** Vanilla STARS blend (additive-ish) but with per-vertex colour, so each star can have its own tint/brightness. */
	public static final RenderPipeline NIGHT = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withLocation("pipeline/skybox_overhaul_night")
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.QUADS)
			.withCull(false)
			.build());

	/** Plain alpha-blended coloured quads: the horizon haze. */
	public static final RenderPipeline HAZE = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withLocation("pipeline/skybox_overhaul_haze")
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.QUADS)
			.withCull(false)
			.build());

	/**
	 * Aurora is ordinary alpha-blended geometry, not an additive light source. It depth-tests
	 * against opaque terrain but never writes depth, so later cloud layers remain visible.
	 * Back-face culling is enabled for closed prisms and transparent quads are sorted back-to-front.
	 */
	public static final RenderPipeline AURORA = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withLocation("pipeline/skybox_overhaul_aurora")
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.QUADS)
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
