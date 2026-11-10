package skybox.overhaul.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import skybox.overhaul.client.CloudLighting;
import skybox.overhaul.client.HorizonHaze;
import skybox.overhaul.client.NightSky;
import skybox.overhaul.client.SkyPalette;
import skybox.overhaul.client.SkyboxConfig;
import skybox.overhaul.client.SkyboxState;
import skybox.overhaul.client.TwilightGlow;

@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
	@Unique
	private NightSky skybox$night;
	@Unique
	private HorizonHaze skybox$haze;
	@Unique
	private TwilightGlow skybox$glow;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void skybox$initNight(TextureManager textureManager, AtlasManager atlasManager, CallbackInfo ci) {
		skybox$night = new NightSky();
		skybox$haze = new HorizonHaze();
		skybox$glow = new TwilightGlow();
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
		skybox$glow.close();
	}

	// After the disc (gradient + haze, then our twilight glow), before vanilla sunrise fan / sun / moon / stars.
	@Inject(method = "renderSkyDisc", at = @At("TAIL"))
	private void skybox$haze(int skyColor, CallbackInfo ci) {
		skybox$haze.render(skyColor);
		skybox$glow.render();
	}

	// Vanilla's sunrise/sunset fan: 16-segment triangle fan, blocky next to the sun. TwilightGlow replaces it.
	@Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
	private void skybox$noFan(PoseStack poseStack, float sunAngle, int sunriseAndSunsetColor, CallbackInfo ci) {
		if (SkyboxConfig.hideVanillaFan && SkyboxState.valid) {
			ci.cancel();
		}
	}

	// Sky disc colour (zenith). Fog (horizon) is handled in AtmosphericFogEnvironmentMixin;
	// vanilla's sky shader fades disc -> fog colour toward the horizon = the gradient.
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void skybox$zenith(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo ci) {
		SkyboxState.rainLevel = level.getRainLevel(partialTicks);
		SkyboxState.lightningFlash = CloudLighting.lightningStrength(
			((ClientLevelAccessor) (Object) level).skybox$getSkyFlashTime());
		if (state.skybox == Skybox.NONE || state.skybox == Skybox.END) {
			return;
		}
		SkyboxState.rainBrightness = state.rainBrightness;
		SkyboxState.sunAngle = state.sunAngle;
		state.skyColor = SkyPalette.sky(state.skyColor, state.sunAngle, level.getRainLevel(partialTicks));
	}
}
