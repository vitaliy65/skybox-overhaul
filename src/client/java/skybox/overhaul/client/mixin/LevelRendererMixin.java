package skybox.overhaul.client.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import skybox.overhaul.client.CloudLayers;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void skybox$registerCloudLayers(CallbackInfo ci) {
        CloudLayers.register();
    }

    @Inject(method = "endFrame", at = @At("TAIL"))
    private void skybox$endCloudLayersFrame(CallbackInfo ci) {
        CloudLayers.endFrame();
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void skybox$closeCloudLayers(CallbackInfo ci) {
        CloudLayers.close();
    }
}
