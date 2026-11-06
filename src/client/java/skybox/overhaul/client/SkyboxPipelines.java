package skybox.overhaul.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.RenderPipelines;

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

	/** Touch from the client entrypoint so the pipeline registers before shaders precompile. */
	public static void init() {}

	private SkyboxPipelines() {}
}
