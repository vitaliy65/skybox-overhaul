package skybox.overhaul.client;

import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

public class SkyboxOverhaulClient implements ClientModInitializer {
	public static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("skybox-overhaul.properties");

	@Override
	public void onInitializeClient() {
		SkyboxConfig.load(CONFIG);
		SkyboxPipelines.init();
		LevelRenderEvents.END_EXTRACTION.register(context -> NightSky.prepare());
		skybox.overhaul.client.aurora.AuroraRenderer.init();

		var container = FabricLoader.getInstance()
				.getModContainer("skybox-overhaul")
				.orElseThrow();

		ResourceManagerHelper.registerBuiltinResourcePack(
				Identifier.fromNamespaceAndPath(
						"skybox-overhaul",
						"celestials"
				),
				container,
				ResourcePackActivationType.NORMAL
		);
	}
}
