package skybox.overhaul.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import skybox.overhaul.client.sky.SkyPalette;

@Mixin(AtmosphericFogEnvironment.class)
public class AtmosphericFogEnvironmentMixin {
	// Vanilla tints fog color by camera.forward . sunDir, so the whole sky
	// (fogged toward fog color) shifts as you turn. Return 0 -> tint branch
	// (factor > 0) never runs -> fog color no longer depends on view direction.
	// Sunset glow still drawn by SkyRenderer's sunrise fan.
	@Redirect(method = "getBaseColor", require = 1,
		at = @At(value = "INVOKE", target = "Lorg/joml/Vector3fc;dot(FFF)F"))
	private float skybox$noViewTint(Vector3fc forward, float x, float y, float z) {
		return 0.0F;
	}

	// Horizon colour by sun height. Fog colour = what far terrain fades into AND what the sky
	// disc fades to at the horizon, so using it here keeps terrain and sky seamless.
	@Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
	private void skybox$palette(ClientLevel level, Camera camera, int renderDistance, float partialTicks, CallbackInfoReturnable<Integer> cir) {
		float sunAngle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks) * ((float) Math.PI / 180.0F);
		cir.setReturnValue(SkyPalette.fog(cir.getReturnValue(), sunAngle, level.getRainLevel(partialTicks)));
	}
}
