package skybox.overhaul.client.mixin;

import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import skybox.overhaul.client.CloudLayers;
import skybox.overhaul.client.CloudTextureOverride;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin implements CloudTextureOverride {
    @Unique
    private Identifier skybox$textureOverride;

    @Override
    public void skybox$setTextureOverride(Identifier texture) {
        this.skybox$textureOverride = texture;
    }

    @Override
    public Identifier skybox$getTextureOverride() {
        return this.skybox$textureOverride;
    }

    @Redirect(
            method = "prepare",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/ResourceManager;open(Lnet/minecraft/resources/Identifier;)Ljava/io/InputStream;"
            )
    )
    private InputStream skybox$openLayerTexture(ResourceManager manager, Identifier vanillaTexture) throws IOException {
        Identifier texture = this.skybox$textureOverride;
        return manager.open(texture == null ? vanillaTexture : texture);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void skybox$renderAdditionalLayers(
            int color,
            CloudStatus cloudStatus,
            float bottomY,
            int range,
            Vec3 cameraPosition,
            long gameTime,
            float partialTicks,
            CallbackInfo ci
    ) {
        CloudRenderer self = (CloudRenderer) (Object) this;
        if (!CloudLayers.isAdditionalRenderer(self)) {
            CloudLayers.renderAdditional(
                    color,
                    cloudStatus,
                    bottomY,
                    range,
                    cameraPosition,
                    gameTime,
                    partialTicks
            );
        }
    }
}
