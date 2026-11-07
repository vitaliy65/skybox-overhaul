package skybox.overhaul.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import skybox.overhaul.client.Celestials;
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
	@Unique
	private Celestials skybox$celestials;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void skybox$initNight(CallbackInfo ci) {
		skybox$night = new NightSky();
		skybox$haze = new HorizonHaze();
		skybox$glow = new TwilightGlow();
		skybox$celestials = new Celestials();
	}

	// Custom sun/moon textures instead of vanilla's (toggle in the config screen). The sun pose is always captured:
	// the moon is drawn in the same frame. Vanilla calls renderSun before renderMoon.
	@Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
	private void skybox$sun(float alpha, PoseStack poseStack, CallbackInfo ci) {
		skybox$celestials.captureSunPose(poseStack.last().pose());
		if (SkyboxConfig.customCelestials) {
			skybox$celestials.renderSun(alpha);
			ci.cancel();
		}
	}

	@Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
	private void skybox$moon(MoonPhase moonPhase, float alpha, PoseStack poseStack, CallbackInfo ci) {
		if (SkyboxConfig.customCelestials && skybox$celestials.renderMoon(moonPhase, alpha)) {
			ci.cancel();
		}
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
		skybox$celestials.close();
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
		if (state.skybox == Skybox.NONE || state.skybox == Skybox.END) {
			return;
		}
		SkyboxState.rainBrightness = state.rainBrightness;
		SkyboxState.sunAngle = state.sunAngle;
		state.skyColor = SkyPalette.sky(state.skyColor, state.sunAngle, level.getRainLevel(partialTicks));
	}
}
