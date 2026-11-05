package skybox.overhaul.client.mixin;

import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
}
