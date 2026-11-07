package skybox.overhaul.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import skybox.overhaul.client.config.SkyboxConfig;
import skybox.overhaul.client.night.NightSky;
import skybox.overhaul.client.render.SkyboxState;
import skybox.overhaul.client.sky.BiomeGroups;
import skybox.overhaul.client.sky.BiomeSky;
import skybox.overhaul.client.sky.HorizonHaze;
import skybox.overhaul.client.sky.SkyPalette;
import skybox.overhaul.client.sky.TwilightGlow;

// 26.3: SkyRenderer draws into one RenderPass that it hands to every sky method, so all our sky layers draw into that pass.
// Signatures taken from Iris / Darker-Sky for 26.3: renderSkyDisc(RenderPass, Vector3fc), renderStars(RenderPass, float, PoseStack),
// renderSunriseAndSunset(RenderPass, PoseStack, float, Vector4fc).
@Mixin(SkyRenderer.class)
public class SkyRendererMixin {
	@Unique
	private NightSky skybox$night;
	@Unique
	private HorizonHaze skybox$haze;
	@Unique
	private TwilightGlow skybox$glow;
	/** Zenith colour (RGB) the sky disc was drawn with this frame; the haze base layer must match it. */
	@Unique
	private int skybox$zenith;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void skybox$initNight(CallbackInfo ci) {
		skybox$night = new NightSky();
		skybox$haze = new HorizonHaze();
		skybox$glow = new TwilightGlow();
	}

	// ponytail: vanilla star buffer is still built, just never drawn; cancel the build if the VRAM matters.
	@Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
	private void skybox$stars(RenderPass pass, float starBrightness, PoseStack poseStack, CallbackInfo ci) {
		skybox$night.render(pass, poseStack, starBrightness);
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
	private void skybox$haze(RenderPass pass, Vector3fc skyColor, CallbackInfo ci) {
		skybox$haze.render(pass, skybox$zenith);
		skybox$glow.render(pass);
	}

	// Vanilla's sunrise/sunset fan: 16-segment triangle fan, blocky next to the sun. TwilightGlow replaces it.
	@Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
	private void skybox$noFan(RenderPass pass, PoseStack poseStack, float sunAngle, Vector4fc sunriseAndSunsetColor, CallbackInfo ci) {
		if (SkyboxConfig.hideVanillaFan && SkyboxState.valid) {
			ci.cancel();
		}
	}

	// Sky disc colour (zenith). Fog (horizon) is handled in AtmosphericFogEnvironmentMixin;
	// vanilla's sky shader fades disc -> fog colour toward the horizon = the gradient.
	// 26.3 no longer gives us SkyRenderState in a hook we know, so the per-frame sky state is read here, at the start of the disc draw.
	@ModifyVariable(method = "renderSkyDisc", at = @At("HEAD"), argsOnly = true, index = 2)
	private Vector3fc skybox$zenith(Vector3fc skyColor) {
		int vanilla = skybox$pack(skyColor);
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			skybox$zenith = vanilla;
			return skyColor;
		}
		float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float rain = level.getRainLevel(partialTicks);
		float sunAngle = mc.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks) * ((float) Math.PI / 180.0F);
		SkyboxState.rainLevel = rain;
		SkyboxState.rainBrightness = 1.0F - rain;
		SkyboxState.sunAngle = sunAngle;
		if (mc.player != null) {
			BiomeSky.update(BiomeGroups.of(level.getBiome(mc.player.blockPosition())));
		}
		skybox$zenith = SkyPalette.sky(vanilla, sunAngle, rain);
		return new Vector3f(((skybox$zenith >> 16) & 0xFF) / 255.0F, ((skybox$zenith >> 8) & 0xFF) / 255.0F, (skybox$zenith & 0xFF) / 255.0F);
	}

	@Unique
	private static int skybox$pack(Vector3fc c) {
		return (skybox$to8(c.x()) << 16) | (skybox$to8(c.y()) << 8) | skybox$to8(c.z());
	}

	@Unique
	private static int skybox$to8(float f) {
		return Math.max(0, Math.min(255, Math.round(f * 255.0F)));
	}
}
