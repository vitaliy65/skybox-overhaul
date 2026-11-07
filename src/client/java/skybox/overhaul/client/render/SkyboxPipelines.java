package skybox.overhaul.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

// 26.3: blaze3d render classes moved to com.mojang.renderpearl.api.*; MATRICES_PROJECTION was split into PROJECTION + DYNAMIC_TRANSFORMS.
// ponytail: pipelines are not passed to RenderPipelines.register; callers use RenderSystem.getCompiledPipeline(...) (compiled on first draw, like Iris does).
public final class SkyboxPipelines {
	/** Vanilla STARS blend (additive-ish) but with per-vertex colour, so each star can have its own tint/brightness. */
	public static final RenderPipeline NIGHT = quads("night")
		.withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
		.build();

	/** Plain alpha-blended coloured quads: the horizon haze and the twilight glow. */
	public static final RenderPipeline HAZE = quads("haze")
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.build();

	/**
	 * Aurora is ordinary alpha-blended geometry, not an additive light source. It is drawn inside the sky
	 * pass (before terrain, so terrain covers it) with no depth state, like the other sky pipelines.
	 * Back-face culling is enabled for closed prisms; the transparent quads are sorted back-to-front
	 * by the StagedVertexBuffer draw that AuroraRenderer appends.
	 */
	public static final RenderPipeline AURORA = quads("aurora")
		.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
		.withCull(true)
		.build();

	private static RenderPipeline.Builder quads(String name) {
		return RenderPipeline.builder()
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			.withLocation(Identifier.fromNamespaceAndPath("skybox-overhaul", "pipeline/" + name))
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withCull(false);
	}

	/**
	 * Own static index buffer for {@code quads} quads (0,1,2,2,3,0, ...). Vanilla's shared sequential buffer
	 * is resized only at frame start in 26.3, too late for a mesh we build on demand.
	 */
	public static GpuBuffer quadIndices(int quads) {
		ByteBuffer indices = ByteBuffer.allocateDirect(quads * 6 * Integer.BYTES).order(ByteOrder.nativeOrder());
		for (int q = 0; q < quads; q++) {
			int v = q * 4;
			indices.putInt(v).putInt(v + 1).putInt(v + 2).putInt(v + 2).putInt(v + 3).putInt(v);
		}
		return RenderSystem.getDevice().createBuffer(() -> "Skybox quad indices", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, indices.flip());
	}

	/** Touch from the client entrypoint so the pipelines are built at startup. */
	public static void init() {}

	private SkyboxPipelines() {}
}
