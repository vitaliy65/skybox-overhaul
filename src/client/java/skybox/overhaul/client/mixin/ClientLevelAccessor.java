package skybox.overhaul.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
    @Invoker("getSkyFlashTime")
    int skybox$getSkyFlashTime();
}
