package skybox.overhaul.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3f;
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
	// 26.3: getBaseColor returns Vector3f (RGB 0..1), not ARGB int (<= 26.2). SkyPalette still works in
	// ARGB ints, so convert in/out. Return a fresh vector: never mutate the one vanilla gave us.
	@Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
	private void skybox$palette(ClientLevel level, Camera camera, int renderDistance, float partialTicks, CallbackInfoReturnable<Vector3f> cir) {
		float sunAngle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks) * ((float) Math.PI / 180.0F);
		Vector3fc v = cir.getReturnValue();
		int vanilla = 0xFF000000 | to8(v.x()) << 16 | to8(v.y()) << 8 | to8(v.z());
		int out = SkyPalette.fog(vanilla, sunAngle, level.getRainLevel(partialTicks));
		cir.setReturnValue(new Vector3f((out >> 16 & 0xFF) / 255.0F, (out >> 8 & 0xFF) / 255.0F, (out & 0xFF) / 255.0F));
	}

	private static int to8(float c) {
		return Math.round(Math.max(0.0F, Math.min(1.0F, c)) * 255.0F);
	}
}
