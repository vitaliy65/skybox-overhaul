package skybox.overhaul.client;

import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class SkyboxOverhaulClient implements ClientModInitializer {
	public static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("skybox-overhaul.properties");

	@Override
	public void onInitializeClient() {
		SkyboxConfig.load(CONFIG);
		SkyboxPipelines.init();
	}
}
