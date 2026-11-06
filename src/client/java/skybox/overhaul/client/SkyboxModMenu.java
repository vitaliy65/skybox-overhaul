package skybox.overhaul.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Only ever loaded by Mod Menu itself (fabric.mod.json "modmenu" entrypoint), so the mod works without it. */
public final class SkyboxModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SkyboxConfigScreen::new;
	}
}
