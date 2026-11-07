package skybox.overhaul.client;

import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import skybox.overhaul.client.aurora.AuroraRenderer;
import skybox.overhaul.client.config.SkyboxConfig;
import skybox.overhaul.client.night.NightSky;
import skybox.overhaul.client.render.SkyboxPipelines;

public class SkyboxOverhaulClient implements ClientModInitializer {
	public static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("skybox-overhaul.properties");

	@Override
	public void onInitializeClient() {
		SkyboxConfig.load(CONFIG);
		SkyboxPipelines.init();
		AuroraRenderer.init();
		LevelRenderEvents.END_EXTRACTION.register(context -> NightSky.prepare());

		// Custom sun and moon textures live in a built-in resource pack (resourcepacks/celestials in the jar):
		// on by default, switch off in Options > Resource Packs to get vanilla's sun and moon back.
		// If this does not compile, the Fabric API signature moved: check ResourceLoader#registerBuiltinPack.
		ResourceLoader.registerBuiltinPack(
			Identifier.fromNamespaceAndPath("skybox-overhaul", "celestials"),
			FabricLoader.getInstance().getModContainer("skybox-overhaul").orElseThrow(),
			Component.literal("Skybox Overhaul: sun & moon"),
			PackActivationType.DEFAULT_ENABLED);
	}
}
