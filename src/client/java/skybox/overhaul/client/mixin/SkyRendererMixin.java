package skybox.overhaul.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import skybox.overhaul.client.HorizonHaze;
import skybox.overhaul.client.NightSky;
import skybox.overhaul.client.SkyPalette;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
	@Unique
	private NightSky skybox$night;
	@Unique
	private HorizonHaze skybox$haze;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void skybox$initNight(TextureManager textureManager, AtlasManager atlasManager, CallbackInfo ci) {
		skybox$night = new NightSky();
		skybox$haze = new HorizonHaze();
	}

	// ponytail: vanilla star buffer is still built, just never drawn; cancel the build if the VRAM matters.
	@Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
	private void skybox$stars(float starBrightness, PoseStack poseStack, CallbackInfo ci) {
		skybox$night.render(poseStack, starBrightness);
		ci.cancel();
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void skybox$closeNight(CallbackInfo ci) {
		skybox$night.close();
		skybox$haze.close();
	}

	// After the disc, before sunrise glow / sun / moon / stars.
	@Inject(method = "renderSkyDisc", at = @At("TAIL"))
	private void skybox$haze(int skyColor, CallbackInfo ci) {
		skybox$haze.render();
	}

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
