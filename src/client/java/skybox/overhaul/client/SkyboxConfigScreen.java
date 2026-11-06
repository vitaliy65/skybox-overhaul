package skybox.overhaul.client;

import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Mod Menu config screen on vanilla widgets only (no Cloth Config). Changes apply live, saved on close. */
public final class SkyboxConfigScreen extends Screen {
	private final Screen parent;

	public SkyboxConfigScreen(Screen parent) {
		super(Component.translatable("skybox-overhaul.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = this.width / 2 - 100;
		int y = this.height / 4;
		addRenderableWidget(new StringWidget(x, 20, 200, 9, this.title, this.font));
		addRenderableWidget(new Percent(x, y, "skybox-overhaul.config.palette", SkyboxConfig.paletteStrength,
			v -> SkyboxConfig.paletteStrength = (float) v));
		addRenderableWidget(new Percent(x, y + 24, "skybox-overhaul.config.sunset", SkyboxConfig.sunsetIntensity,
			v -> SkyboxConfig.sunsetIntensity = (float) v));
		addRenderableWidget(new Percent(x, y + 48, "skybox-overhaul.config.comets", SkyboxConfig.cometPercent / 100.0,
			v -> SkyboxConfig.cometPercent = (int) Math.round(v * 100.0)));
		addRenderableWidget(Button.builder(fanLabel(), b -> {
			SkyboxConfig.hideVanillaFan = !SkyboxConfig.hideVanillaFan;
			b.setMessage(fanLabel());
		}).bounds(x, y + 72, 200, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("skybox-overhaul.config.reset"), b -> {
			SkyboxConfig.reset();
			rebuildWidgets();
		}).bounds(x, y + 104, 98, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x + 102, y + 104, 98, 20).build());
	}

	private static Component fanLabel() {
		return Component.translatable("skybox-overhaul.config.fan",
			Component.translatable(SkyboxConfig.hideVanillaFan ? "options.on" : "options.off"));
	}

	@Override
	public void onClose() {
		SkyboxConfig.save(SkyboxOverhaulClient.CONFIG);
		this.minecraft.setScreen(this.parent);
	}

	/** 0..100 % slider; label is the lang key with the percentage as %s. */
	private static final class Percent extends AbstractSliderButton {
		private final String key;
		private final DoubleConsumer apply;

		Percent(int x, int y, String key, double value, DoubleConsumer apply) {
			super(x, y, 200, 20, Component.empty(), value);
			this.key = key;
			this.apply = apply;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable(this.key, Math.round(this.value * 100.0) + "%"));
		}

		@Override
		protected void applyValue() {
			this.apply.accept(this.value);
		}
	}
}
