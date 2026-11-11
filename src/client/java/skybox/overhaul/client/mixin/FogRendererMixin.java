package skybox.overhaul.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import skybox.overhaul.client.render.SkyboxState;
import skybox.overhaul.client.config.SkyboxConfig;
import skybox.overhaul.client.sky.FogMist;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
	/** Longest fade at fogTransition = 1, as a share of the render distance. */
	private static final float MAX_FADE_SHARE = 0.75F;
	/** Longest fade at full morning mist: fog starts almost at the camera (5% of the render distance). */
	private static final float MIST_FADE_SHARE = 0.95F;

	// Soft horizon: vanilla fades terrain out over only clamp(rd/10, 4, 64) blocks, which reads as a hard line.
	// renderDistanceEnd (where terrain is fully fogged, the "line") stays put; only the start moves closer
	// to the player, so the haze builds up gradually. Skipped under water/lava/snow: their own fog is closer anyway.
	// fogTransition = 0 and morningFog = 0 give vanilla's exact span.
	// Done on the span value INSIDE setupFog (not at RETURN): Sodium copies FogData for its terrain shader
	// before a RETURN injector of ours would run, so a late edit never reached Sodium's terrain.
	@ModifyExpressionValue(method = "setupFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"))
	private float skybox$softFogFade(float vanillaFade, @Local(argsOnly = true) Camera camera,
			@Local(argsOnly = true) int renderDistanceInChunks) {
		if (camera.getFluidInCamera() != FogType.NONE) {
			return vanillaFade;
		}
		float end = renderDistanceInChunks * 16;
		float fade = Mth.lerp(SkyboxConfig.fogTransition, vanillaFade, Math.max(vanillaFade, end * MAX_FADE_SHARE));
		// Morning / after-rain mist rides on the same hook: the ramp grows toward the camera, so the fog is closer
		// (and, like the soft edge, it reaches Sodium's terrain shader). Strength 0 = off.
		float mist = SkyboxState.skyboxActive
			? FogMist.strength(SkyboxState.sunAngle, SkyboxState.gameTime, SkyboxState.rainLevel) : 0.0F;
		if (mist > 0.0F) {
			fade = Mth.lerp(mist, fade, Math.max(fade, end * MIST_FADE_SHARE));
		} else if (!SkyboxState.skyboxActive) {
			FogMist.clear();
		}
		return Math.min(fade, end);
	}

	// Grab the exact fog colour terrain fades into, so the sky haze matches it perfectly.
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void skybox$captureFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		SkyboxState.FOG.set(cir.getReturnValue().color);
		SkyboxState.valid = true;
	}
}
