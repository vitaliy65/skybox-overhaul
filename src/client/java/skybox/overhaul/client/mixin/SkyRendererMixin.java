package skybox.overhaul.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import skybox.overhaul.client.SkyPalette;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
	// Sky disc colour (zenith). Fog (horizon) is handled in AtmosphericFogEnvironmentMixin;
	// vanilla's sky shader fades disc -> fog colour toward the horizon = the gradient.
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void skybox$zenith(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo ci) {
		if (state.skybox == Skybox.NONE || state.skybox == Skybox.END) {
			return;
		}
		state.skyColor = SkyPalette.sky(state.skyColor, state.sunAngle, level.getRainLevel(partialTicks));
	}
}
