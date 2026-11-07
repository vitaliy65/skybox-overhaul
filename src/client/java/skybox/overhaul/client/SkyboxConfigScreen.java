package skybox.overhaul.client;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Mod Menu config screen on vanilla widgets only (no Cloth Config). Two columns: sky on the left, aurora on the
 * right. Changes apply live, saved on close. The aurora options grey out while the aurora is switched off.
 */
public final class SkyboxConfigScreen extends Screen {
	private static final int COLUMN_WIDTH = 170;
	private static final int ROW = 24;
	private static final int TOP = 40;
	private static final int AURORA_ROWS = 8;

	private final Screen parent;

	public SkyboxConfigScreen(Screen parent) {
		super(Component.translatable("skybox-overhaul.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int w = COLUMN_WIDTH;
		int left = this.width / 2 - w - 4;
		int right = this.width / 2 + 4;
		addRenderableWidget(new StringWidget(left, 10, 2 * w + 8, 9, this.title, this.font));
		addRenderableWidget(new StringWidget(left, 26, w, 9,
			Component.translatable("skybox-overhaul.config.section.sky"), this.font));
		addRenderableWidget(new StringWidget(right, 26, w, 9,
			Component.translatable("skybox-overhaul.config.section.aurora"), this.font));

		// Sky column.
		addRenderableWidget(new Percent(left, TOP, w, "skybox-overhaul.config.palette", SkyboxConfig.paletteStrength,
			v -> SkyboxConfig.paletteStrength = (float) v));
		addRenderableWidget(new Percent(left, TOP + ROW, w, "skybox-overhaul.config.sunset", SkyboxConfig.sunsetIntensity,
			v -> SkyboxConfig.sunsetIntensity = (float) v));
		addRenderableWidget(new Percent(left, TOP + 2 * ROW, w, "skybox-overhaul.config.comets",
			SkyboxConfig.cometPercent / 100.0, v -> SkyboxConfig.cometPercent = (int) Math.round(v * 100.0)));
		addRenderableWidget(toggle(left, TOP + 3 * ROW, w, "skybox-overhaul.config.fan",
			() -> SkyboxConfig.hideVanillaFan, v -> SkyboxConfig.hideVanillaFan = v, () -> {}));

		// Aurora column: the switch first, the rest is dimmed while it is off.
		boolean on = SkyboxConfig.auroraEnabled;
		addRenderableWidget(toggle(right, TOP, w, "skybox-overhaul.config.aurora",
			() -> SkyboxConfig.auroraEnabled, v -> SkyboxConfig.auroraEnabled = v, this::rebuildWidgets));
		addAurora(toggle(right, TOP + ROW, w, "skybox-overhaul.config.aurora-cold",
			() -> SkyboxConfig.auroraColdOnly, v -> SkyboxConfig.auroraColdOnly = v, () -> {}), on);
		addAurora(new Percent(right, TOP + 2 * ROW, w, "skybox-overhaul.config.aurora-brightness",
			SkyboxConfig.auroraBrightness, v -> SkyboxConfig.auroraBrightness = (float) v), on);
		addAurora(new NumberSlider(right, TOP + 3 * ROW, w, "skybox-overhaul.config.aurora-speed",
			SkyboxConfig.auroraSpeed, 0.25, 2.0, "x", 2, v -> SkyboxConfig.auroraSpeed = (float) v), on);
		addAurora(toggle(right, TOP + 4 * ROW, w, "skybox-overhaul.config.aurora-flares",
			() -> SkyboxConfig.auroraFlares, v -> SkyboxConfig.auroraFlares = v, () -> {}), on);
		addAurora(new NumberSlider(right, TOP + 5 * ROW, w, "skybox-overhaul.config.aurora-grid",
			SkyboxConfig.auroraGridCellSize, 8.0, 32.0, " blocks", 0, v -> SkyboxConfig.auroraGridCellSize = (float) v), on);
		addAurora(new NumberSlider(right, TOP + 6 * ROW, w, "skybox-overhaul.config.aurora-height",
			SkyboxConfig.auroraHeight, 40.0, 180.0, " blocks", 0, v -> SkyboxConfig.auroraHeight = (float) v), on);
		addAurora(new Percent(right, TOP + 7 * ROW, w, "skybox-overhaul.config.aurora-frequency",
			SkyboxConfig.auroraFrequency, v -> SkyboxConfig.auroraFrequency = (float) v), on);

		int bottom = Math.max(this.height - 28, TOP + AURORA_ROWS * ROW + 4);
		addRenderableWidget(Button.builder(Component.translatable("skybox-overhaul.config.reset"), b -> {
			SkyboxConfig.reset();
			rebuildWidgets();
		}).bounds(this.width / 2 - 102, bottom, 100, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
			.bounds(this.width / 2 + 2, bottom, 100, 20).build());
	}

	private <T extends AbstractWidget> void addAurora(T widget, boolean active) {
		widget.active = active;
		addRenderableWidget(widget);
	}

	/** ON/OFF button; the label is the lang key with the state as %s. {@code after} runs once the value changed. */
	private static Button toggle(int x, int y, int w, String key, BooleanSupplier get, Consumer<Boolean> set,
			Runnable after) {
		return Button.builder(toggleLabel(key, get.getAsBoolean()), b -> {
			set.accept(!get.getAsBoolean());
			b.setMessage(toggleLabel(key, get.getAsBoolean()));
			after.run();
		}).bounds(x, y, w, 20).build();
	}

	private static Component toggleLabel(String key, boolean on) {
		return Component.translatable(key, Component.translatable(on ? "options.on" : "options.off"));
	}

	@Override
	public void onClose() {
		SkyboxConfig.save(SkyboxOverhaulClient.CONFIG);
		this.minecraft.setScreenAndShow(this.parent);
	}

	/** Slider whose displayed/stored value is mapped to a numeric range, shown with a fixed number of decimals. */
	private static final class NumberSlider extends AbstractSliderButton {
		private final String key;
		private final double min;
		private final double max;
		private final String suffix;
		private final int decimals;
		private final DoubleConsumer apply;

		NumberSlider(int x, int y, int w, String key, double value, double min, double max,
				String suffix, int decimals, DoubleConsumer apply) {
			super(x, y, w, 20, Component.empty(), (value - min) / (max - min));
			this.key = key;
			this.min = min;
			this.max = max;
			this.suffix = suffix;
			this.decimals = decimals;
			this.apply = apply;
			updateMessage();
		}

		private double actualValue() {
			return min + value * (max - min);
		}

		@Override
		protected void updateMessage() {
			String formatted = String.format(Locale.ROOT, "%." + decimals + "f", actualValue());
			setMessage(Component.translatable(key, formatted + suffix));
		}

		@Override
		protected void applyValue() {
			apply.accept(actualValue());
		}
	}

	/** 0..100 % slider; label is the lang key with the percentage as %s. */
	private static final class Percent extends AbstractSliderButton {
		private final String key;
		private final DoubleConsumer apply;

		Percent(int x, int y, int w, String key, double value, DoubleConsumer apply) {
			super(x, y, w, 20, Component.empty(), value);
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
