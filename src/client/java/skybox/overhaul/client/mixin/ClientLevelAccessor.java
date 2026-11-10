package skybox.overhaul.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Accesses the option-aware vanilla sky-flash timer, which is private on ClientLevel. */
@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
    @Invoker("getSkyFlashTime")
    int skybox$getSkyFlashTime();
}
