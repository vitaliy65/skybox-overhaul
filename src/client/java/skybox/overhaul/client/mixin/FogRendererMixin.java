package skybox.overhaul.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import skybox.overhaul.client.render.SkyboxState;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
	// Grab the exact fog colour terrain fades into, so the sky haze matches it perfectly.
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void skybox$captureFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		SkyboxState.FOG.set(cir.getReturnValue().color);
		SkyboxState.valid = true;
	}
}
