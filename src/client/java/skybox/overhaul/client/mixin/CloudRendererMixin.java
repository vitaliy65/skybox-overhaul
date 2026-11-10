
package skybox.overhaul.client.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;

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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import skybox.overhaul.client.cloud.CloudLayers;
import skybox.overhaul.client.cloud.CloudLighting;
import skybox.overhaul.client.cloud.CloudTextureOverride;
import skybox.overhaul.client.config.SkyboxConfig;
import skybox.overhaul.client.render.SkyboxState;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin implements CloudTextureOverride {

    @Shadow @Final
    private MappableRingBuffer ubo;

    @Unique
    private Identifier skybox$textureOverride;

    @Unique
    private static boolean skybox$warned;

    @ModifyVariable(
            method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("HEAD"), argsOnly = true
    )
    private long skybox$cloudSpeed(long gameTime) {
        return CloudLayers.isAdditionalRenderer((CloudRenderer) (Object) this)
                ? gameTime : Math.round(gameTime * (double) SkyboxConfig.cloudSpeed);
    }

    @ModifyVariable(
            method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1
    )
    private float skybox$cloudPartialTickSpeed(float partialTicks) {
        return CloudLayers.isAdditionalRenderer((CloudRenderer) (Object) this)
                ? partialTicks : partialTicks * SkyboxConfig.cloudSpeed;
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
            method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Optional;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/ResourceManager;open(Lnet/minecraft/resources/Identifier;)Ljava/io/InputStream;"
            )
    )
    private InputStream skybox$openLayerTexture(
            ResourceManager manager,
            Identifier vanillaTexture
    ) throws IOException {
        Identifier texture = this.skybox$textureOverride;
        return manager.open(texture == null ? vanillaTexture : texture);
    }

    /**
     * 26.3 CloudInfo is 44 bytes: vec4 CloudColor (0), vec3 CloudOffset (16), vec3 CellSize (32).
     * Offset 28 is the only spare float. CloudColor.a (12) carries four packed lighting channels.
     * Never write at offset 44: it is outside the mapped UBO in this version.
     */
    @Inject(
            method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("TAIL"),
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
        float sunDirection = SkyboxState.valid
                ? CloudLighting.packedSunDirection(SkyboxState.sunAngle)
                : 1.0F;

        float twilight = SkyboxState.valid
                ? CloudLighting.twilightStrength(SkyboxState.sunAngle, SkyboxState.rainLevel)
                : 0.0F;
        float moonlight = SkyboxState.valid
                ? CloudLighting.moonlightStrength(SkyboxState.sunAngle, SkyboxState.rainLevel)
                : 0.0F;
        float lightning = SkyboxState.valid ? SkyboxState.lightningFlash : 0.0F;

        try (GpuBufferSlice.MappedView view = this.ubo.currentBuffer().map(false, true)) {
            ByteBuffer data = view.data();
            if (data.limit() < 44) {
                if (!skybox$warned) {
                    skybox$warned = true;
                    org.slf4j.LoggerFactory.getLogger("skybox-overhaul").warn(
                            "Cloud UBO is {} bytes (expected 44): custom cloud lighting skipped", data.limit());
                }
                return;
            }
            int cloudColor = CloudLighting.applyOpacity(color, SkyboxConfig.cloudOpacity);
            data.putFloat(12, CloudLighting.packAlphaAndLighting(cloudColor, twilight, moonlight, lightning));
            data.putFloat(28, sunDirection);
        }
    }

    @Inject(
            method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
            at = @At("TAIL"),
            require = 1
    )
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
            Minecraft mc = Minecraft.getInstance();
            long worldGameTime = mc.level == null ? gameTime : mc.level.getGameTime();
            long cloudGameTime = Math.round(worldGameTime * (double) SkyboxConfig.cloudSpeed);
            CloudLayers.renderAdditional(
                    color,
                    cloudStatus,
                    bottomY,
                    range,
                    cameraPosition,
                    cloudGameTime,
                    partialTicks,
                    worldGameTime
            );
        }
    }

    // 26.3: draw the extra layers right before vanilla draws its own (classic transparency path).
    @Inject(
            method = "render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
            at = @At("HEAD"),
            require = 1
    )
    private void skybox$drawAdditionalLayers(CloudStatus cloudStatus, RenderPass renderPass, CallbackInfo ci) {
        if (!CloudLayers.isAdditionalRenderer((CloudRenderer) (Object) this)) {
            CloudLayers.drawAdditional(cloudStatus, renderPass);
        }
    }

    // 26.3: same for improved transparency (OIT), once per stage.
    @Inject(
            method = "renderOit(Lnet/minecraft/client/CloudStatus;Lnet/minecraft/client/renderer/oit/OitStage;"
                    + "Lcom/mojang/renderpearl/api/textures/GpuTextureView;"
                    + "Lnet/minecraft/client/renderer/oit/OitRenderPassProvider$Parameters;)V",
            at = @At("HEAD"),
            require = 1
    )
    private void skybox$drawAdditionalLayersOit(CloudStatus cloudStatus, OitStage stage, GpuTextureView mainDepth,
                                                OitRenderPassProvider.Parameters params, CallbackInfo ci) {
        if (!CloudLayers.isAdditionalRenderer((CloudRenderer) (Object) this)) {
            CloudLayers.drawAdditionalOit(cloudStatus, stage, mainDepth, params);
        }
    }
}
