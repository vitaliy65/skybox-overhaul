package skybox.overhaul.client.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import skybox.overhaul.client.cloud.CloudLayers;
import skybox.overhaul.client.cloud.CloudLighting;
import skybox.overhaul.client.cloud.CloudMotion;
import skybox.overhaul.client.cloud.CloudTextureOverride;
import skybox.overhaul.client.config.SkyboxConfig;
import skybox.overhaul.client.render.SkyboxState;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin implements CloudTextureOverride {
    @Shadow @Final private MappableRingBuffer ubo;
    @Unique
    private Identifier skybox$textureOverride;

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float skybox$vanillaCloudHeight(float bottomY) {
        if (CloudLayers.isAdditionalRenderer((CloudRenderer) (Object) this)) return bottomY;
        return bottomY + SkyboxConfig.globalCloudHeightOffset + SkyboxConfig.vanillaCloudLayerOffset;
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int skybox$cloudOpacity(int color) {
        return CloudLayers.isAdditionalRenderer((CloudRenderer) (Object) this)
                ? color : CloudLighting.applyOpacity(color, SkyboxConfig.cloudOpacity);
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private long skybox$cloudSpeed(long gameTime) {
        if (CloudLayers.isAdditionalRenderer((CloudRenderer) (Object) this)) return gameTime;
        CloudMotion.seenTick(gameTime);
        return CloudMotion.time(gameTime);
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float skybox$cloudPartialTickSpeed(float partialTicks) {
        if (CloudLayers.isAdditionalRenderer((CloudRenderer) (Object) this)) return partialTicks;
        CloudMotion.seenPartial(partialTicks);
        return CloudMotion.partial(partialTicks);
    }

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


    /**
     * CloudInfo is std140: vec4 (16 bytes), vec3 + 4-byte padding, vec3 + 4-byte padding.
     * The two padding floats are unused by vanilla. The mod shader reads them as packed sun
     * direction/warmth phase and 3-channel twilight/moonlight/lightning values.
     * This injection runs after vanilla has filled CloudInfo but before createRenderPass; mapping
     * the UBO after the pass opens is illegal in the 26.2 command encoder and crashes the client.
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDynamicUniforms()Lnet/minecraft/client/renderer/DynamicUniforms;"
            ),
            require = 1
    )
    private void skybox$writeCloudLighting(
            int color,
            CloudStatus cloudStatus,
            float bottomY,
            int range,
            Vec3 cameraPosition,
            long gameTime,
            float partialTicks,
            CallbackInfo ci
    ) {
        float sunDirection = SkyboxState.valid ? CloudLighting.packedSunDirection(SkyboxState.sunAngle) : 1.0F;
        float twilight = SkyboxState.valid
                ? CloudLighting.twilightStrength(SkyboxState.sunAngle, SkyboxState.rainLevel)
                : 0.0F;
        float moonlight = SkyboxState.valid
                ? CloudLighting.moonlightStrength(SkyboxState.sunAngle, SkyboxState.rainLevel)
                : 0.0F;
        float lightning = SkyboxState.valid ? SkyboxState.lightningFlash : 0.0F;

        try (GpuBufferSlice.MappedView view = this.ubo.currentBuffer().map(false, true)) {
            ByteBuffer data = view.data();
            data.putFloat(28, sunDirection);
            data.putFloat(44, CloudLighting.packLighting(twilight, moonlight, lightning));
        }
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
            long worldGameTime = Minecraft.getInstance().level == null
                    ? gameTime : Minecraft.getInstance().level.getGameTime();
            CloudLayers.renderAdditional(
                    color,
                    cloudStatus,
                    bottomY - SkyboxConfig.globalCloudHeightOffset - SkyboxConfig.vanillaCloudLayerOffset,
                    range,
                    cameraPosition,
                    gameTime,
                    partialTicks,
                    worldGameTime
            );
        }
    }
}
