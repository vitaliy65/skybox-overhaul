
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
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;

import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import skybox.overhaul.client.cloud.CloudLayers;
import skybox.overhaul.client.cloud.CloudLighting;
import skybox.overhaul.client.cloud.CloudTextureOverride;
import skybox.overhaul.client.render.SkyboxState;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin implements CloudTextureOverride {

    @Shadow @Final
    private MappableRingBuffer ubo;

    @Unique
    private Identifier skybox$textureOverride;

    @Unique
    private static boolean skybox$warned;

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
     * CloudInfo std140 layout:
     *
     * vec4: offset 0
     * vec3: offset 16 (padding at 28)
     * vec3: offset 32 (padding at 44)
     *
     * The padding floats store custom cloud lighting data.
     *
     * This injection runs after vanilla writes CloudInfo
     * and closes its mapped view.
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
                ? CloudLighting.twilightStrength(
                SkyboxState.sunAngle,
                SkyboxState.rainLevel
        )
                : 0.0F;

        try (GpuBufferSlice.MappedView view =
                     this.ubo.currentBuffer().map(false, true)) {

            ByteBuffer data = view.data();

            // 26.3: CloudInfo layout/size changed -> mapped buffer can be < 48 bytes (IndexOutOfBounds crash).
            // Skip instead of crashing; log the real size once so the new layout can be ported.
            if (data.limit() < 48) {
                if (!skybox$warned) {
                    skybox$warned = true;
                    LoggerFactory.getLogger("skybox-overhaul").warn(
                            "Cloud UBO is {} bytes (expected >= 48): CloudInfo layout changed, cloud lighting disabled",
                            data.limit());
                }
                return;
            }

            data.putFloat(28, sunDirection);
            data.putFloat(44, twilight);
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
