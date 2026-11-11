package skybox.overhaul.client.config;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import skybox.overhaul.client.SkyboxOverhaulClient;


/**
 * ModMenu configuration screen. Custom controls and panel backgrounds keep the UI visually grouped without
 * introducing a separate widget library. Changes apply live and are persisted when the screen is closed.
 */
public final class SkyboxConfigScreen extends Screen {
	private static final int TOP = 28;
	private static final int ROW = 17;
	private static final int CONTROL_HEIGHT = 16;
	private static final int AURORA_ROWS = 8;
	private static final int CLOUD_ROW_GAP = 18;

	private final Screen parent;

	public SkyboxConfigScreen(Screen parent) {
		super(Component.translatable("skybox-overhaul.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int columnWidth = Math.min(170, Math.max(120, (this.width - 32) / 2));
		int left = (this.width - (2 * columnWidth + 16)) / 2;
		int right = left + columnWidth + 16;
		int skyRows = 5;
		int cloudHeadingY = TOP + skyRows * ROW + 3;
		int globalY = cloudHeadingY + 13;
		int middleY = globalY + CLOUD_ROW_GAP;
		int upperY = middleY + CLOUD_ROW_GAP;
		int cloudAppearanceY = upperY + CLOUD_ROW_GAP;
		int cloudAppearanceControlsY = cloudAppearanceY + 12;
		int skyBottom = cloudAppearanceControlsY + CONTROL_HEIGHT + 2;
		int fogHeadingY = TOP + AURORA_ROWS * ROW + 3;
		int fogY = fogHeadingY + 13;
		int auroraBottom = fogY + CONTROL_HEIGHT + 2;
		int contentBottom = Math.max(skyBottom, auroraBottom);
		int footerY = Math.max(this.height - 24, contentBottom + 5);

		// Background is a non-interactive widget so it is rendered behind all controls.
		addRenderableWidget(new PanelBackground(this.width, footerY + 22, left, right, columnWidth,
			cloudHeadingY, globalY, cloudAppearanceControlsY, fogHeadingY, fogY, footerY));

		addRenderableWidget(new StringWidget(8, 2, this.width - 16, 12, this.title, this.font));
		addRenderableWidget(new StringWidget(left, 16, columnWidth, 10,
			Component.translatable("skybox-overhaul.config.section.sky"), this.font));
		addRenderableWidget(new StringWidget(right, 16, columnWidth, 10,
			Component.translatable("skybox-overhaul.config.section.aurora"), this.font));

		// Sky settings retain the controls specific to this project version.
		addRenderableWidget(new StyledSlider(left, TOP, columnWidth, CONTROL_HEIGHT,
			"skybox-overhaul.config.palette", SkyboxConfig.paletteStrength, 0.0, 1.0, "", 0, false,
			v -> SkyboxConfig.paletteStrength = (float) v, false));
		addRenderableWidget(new StyledSlider(left, TOP + ROW, columnWidth, CONTROL_HEIGHT,
			"skybox-overhaul.config.sunset", SkyboxConfig.sunsetIntensity, 0.0, 1.0, "", 0, false,
			v -> SkyboxConfig.sunsetIntensity = (float) v, false));
		addRenderableWidget(new StyledSlider(left, TOP + 2 * ROW, columnWidth, CONTROL_HEIGHT,
			"skybox-overhaul.config.comets", SkyboxConfig.cometPercent / 100.0, 0.0, 1.0, "%", 0, true,
			v -> SkyboxConfig.cometPercent = (int) Math.round(v * 100.0), false));
		addRenderableWidget(toggle(left, TOP + 3 * ROW, columnWidth, "skybox-overhaul.config.fan",
			() -> SkyboxConfig.hideVanillaFan, v -> SkyboxConfig.hideVanillaFan = v, () -> {}));
		addRenderableWidget(new StyledSlider(left, TOP + 4 * ROW, columnWidth, CONTROL_HEIGHT,
			"skybox-overhaul.config.stars", SkyboxConfig.starCount, 1500.0, 8000.0, "", 0, false,
			v -> SkyboxConfig.starCount = (int) Math.round(v), false));

		// CloudLayers owns two additional layers. Global and per-layer offsets are additive.
		addRenderableWidget(new StringWidget(left + 4, cloudHeadingY, columnWidth - 8, 10,
			Component.translatable("skybox-overhaul.config.section.clouds"), this.font));
		addRenderableWidget(new StyledSlider(left + 3, globalY, columnWidth - 6, CONTROL_HEIGHT,
			"skybox-overhaul.config.cloud.global", SkyboxConfig.globalCloudHeightOffset,
			-256.0, 256.0, " blocks", 0, false,
			v -> SkyboxConfig.globalCloudHeightOffset = (float) Math.round(v), false));
		int switchWidth = Math.min(88, columnWidth / 2 + 14);
		int localSliderWidth = columnWidth - switchWidth - 10;
		addRenderableWidget(toggle(left + 3, middleY, switchWidth, "skybox-overhaul.config.cloud.middle",
			() -> SkyboxConfig.middleCloudLayerEnabled, v -> SkyboxConfig.middleCloudLayerEnabled = v,
			this::rebuildWidgets));
		StyledSlider middleOffset = new StyledSlider(left + switchWidth + 7, middleY, localSliderWidth,
			CONTROL_HEIGHT, "skybox-overhaul.config.cloud.offset", SkyboxConfig.middleCloudLayerOffset,
			-256.0, 256.0, " blocks", 0, false,
			v -> SkyboxConfig.middleCloudLayerOffset = (float) Math.round(v), true);
		middleOffset.active = SkyboxConfig.middleCloudLayerEnabled;
		addRenderableWidget(middleOffset);
		addRenderableWidget(toggle(left + 3, upperY, switchWidth, "skybox-overhaul.config.cloud.upper",
			() -> SkyboxConfig.upperCloudLayerEnabled, v -> SkyboxConfig.upperCloudLayerEnabled = v,
			this::rebuildWidgets));
		StyledSlider upperOffset = new StyledSlider(left + switchWidth + 7, upperY, localSliderWidth,
			CONTROL_HEIGHT, "skybox-overhaul.config.cloud.offset", SkyboxConfig.upperCloudLayerOffset,
			-256.0, 256.0, " blocks", 0, false,
			v -> SkyboxConfig.upperCloudLayerOffset = (float) Math.round(v), true);
		upperOffset.active = SkyboxConfig.upperCloudLayerEnabled;
		addRenderableWidget(upperOffset);

		// Global appearance controls affect vanilla clouds and both additional layers.
		addRenderableWidget(new StringWidget(left + 4, cloudAppearanceY, columnWidth - 8, 10,
			Component.translatable("skybox-overhaul.config.cloud.appearance"), this.font));
		int appearanceGap = 6;
		int appearanceSliderWidth = (columnWidth - 12) / 2;
		addRenderableWidget(new StyledSlider(left + 3, cloudAppearanceControlsY, appearanceSliderWidth,
			CONTROL_HEIGHT, "skybox-overhaul.config.cloud.opacity", SkyboxConfig.cloudOpacity,
			0.0, 1.0, "", 0, true, v -> SkyboxConfig.cloudOpacity = (float) v, false));
		addRenderableWidget(new StyledSlider(left + 3 + appearanceSliderWidth + appearanceGap,
			cloudAppearanceControlsY, appearanceSliderWidth, CONTROL_HEIGHT,
			"skybox-overhaul.config.cloud.speed", SkyboxConfig.cloudSpeed,
			0.0, 3.0, "x", 1, false, v -> SkyboxConfig.cloudSpeed = (float) v, false));

		// Aurora column: subordinate controls are visually disabled while the master switch is off.
		boolean auroraOn = SkyboxConfig.auroraEnabled;
		addRenderableWidget(toggle(right, TOP, columnWidth, "skybox-overhaul.config.aurora",
			() -> SkyboxConfig.auroraEnabled, v -> SkyboxConfig.auroraEnabled = v, this::rebuildWidgets));
		addAurora(toggle(right, TOP + ROW, columnWidth, "skybox-overhaul.config.aurora-cold",
			() -> SkyboxConfig.auroraColdOnly, v -> SkyboxConfig.auroraColdOnly = v, () -> {}), auroraOn);
		addAurora(new StyledSlider(right, TOP + 2 * ROW, columnWidth, CONTROL_HEIGHT,
			"skybox-overhaul.config.aurora-brightness", SkyboxConfig.auroraBrightness, 0.0, 1.0,
			"%", 0, true, v -> SkyboxConfig.auroraBrightness = (float) v, false), auroraOn);
		addAurora(new StyledSlider(right, TOP + 3 * ROW, columnWidth, CONTROL_HEIGHT,
			"skybox-overhaul.config.aurora-speed", SkyboxConfig.auroraSpeed, 0.25, 2.0,
			"x", 2, false, v -> SkyboxConfig.auroraSpeed = (float) v, false), auroraOn);
		addAurora(toggle(right, TOP + 4 * ROW, columnWidth, "skybox-overhaul.config.aurora-flares",
			() -> SkyboxConfig.auroraFlares, v -> SkyboxConfig.auroraFlares = v, () -> {}), auroraOn);
		addAurora(new StyledSlider(right, TOP + 5 * ROW, columnWidth, CONTROL_HEIGHT,
			"skybox-overhaul.config.aurora-grid", SkyboxConfig.auroraGridCellSize, 8.0, 32.0,
			" blocks", 0, false, v -> SkyboxConfig.auroraGridCellSize = (float) v, false), auroraOn);
		addAurora(new StyledSlider(right, TOP + 6 * ROW, columnWidth, CONTROL_HEIGHT,
			"skybox-overhaul.config.aurora-height", SkyboxConfig.auroraHeight, 40.0, 180.0,
			" blocks", 0, false, v -> SkyboxConfig.auroraHeight = (float) v, false), auroraOn);
		addAurora(new StyledSlider(right, TOP + 7 * ROW, columnWidth, CONTROL_HEIGHT,
			"skybox-overhaul.config.aurora-frequency", SkyboxConfig.auroraFrequency, 0.0, 1.0,
			"%", 0, true, v -> SkyboxConfig.auroraFrequency = (float) v, false), auroraOn);

		// Fog sits under the aurora options and stays active whatever the aurora switch says.
		addRenderableWidget(new StringWidget(right + 4, fogHeadingY, columnWidth - 8, 10,
			Component.translatable("skybox-overhaul.config.section.fog"), this.font));
		addRenderableWidget(new StyledSlider(right + 3, fogY, columnWidth - 6, CONTROL_HEIGHT,
			"skybox-overhaul.config.fog", SkyboxConfig.fogTransition, 0.0, 1.0,
			"", 0, true, v -> SkyboxConfig.fogTransition = (float) v, false));

		int buttonWidth = Math.min(100, columnWidth - 4);
		int buttonGap = 6;
		int buttonTotal = buttonWidth * 2 + buttonGap;
		int buttonLeft = (this.width - buttonTotal) / 2;
		addRenderableWidget(new ActionControl(buttonLeft, footerY, buttonWidth, 19,
			Component.translatable("skybox-overhaul.config.reset"), () -> {
				SkyboxConfig.reset();
				rebuildWidgets();
			}, false));
		addRenderableWidget(new ActionControl(buttonLeft + buttonWidth + buttonGap, footerY,
			buttonWidth, 19, CommonComponents.GUI_DONE, this::onClose, true));
	}

	private <T extends AbstractWidget> void addAurora(T widget, boolean enabled) {
		widget.active = enabled;
		addRenderableWidget(widget);
	}

	private static StyledToggle toggle(int x, int y, int width, String key, BooleanSupplier get,
			Consumer<Boolean> set, Runnable after) {
		return new StyledToggle(x, y, width, CONTROL_HEIGHT, key, get, set, after);
	}

	@Override
	public void onClose() {
		SkyboxConfig.save(SkyboxOverhaulClient.CONFIG);
		this.minecraft.setScreen(this.parent);
	}

	private static final class PanelBackground extends AbstractWidget {
		private final int left;
		private final int right;
		private final int columnWidth;
		private final int cloudHeadingY;
		private final int globalY;
		private final int cloudAppearanceControlsY;
		private final int fogHeadingY;
		private final int fogY;
		private final int footerY;

		PanelBackground(int screenWidth, int screenHeight, int left, int right, int columnWidth,
				int cloudHeadingY, int globalY, int cloudAppearanceControlsY, int fogHeadingY, int fogY,
				int footerY) {
			super(0, 0, screenWidth, screenHeight, Component.empty());
			this.left = left;
			this.right = right;
			this.columnWidth = columnWidth;
			this.cloudHeadingY = cloudHeadingY;
			this.globalY = globalY;
			this.cloudAppearanceControlsY = cloudAppearanceControlsY;
			this.fogHeadingY = fogHeadingY;
			this.fogY = fogY;
			this.footerY = footerY;
			this.active = false;
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			int panelBottom = footerY - 5;
			drawPanel(graphics, left - 5, 14, left + columnWidth + 5, panelBottom);
			drawPanel(graphics, right - 5, 14, right + columnWidth + 5, panelBottom);
			drawPanel(graphics, left + 1, cloudHeadingY - 3, left + columnWidth - 1,
				cloudAppearanceControlsY + CONTROL_HEIGHT + 2);
			drawPanel(graphics, left + 3, globalY - 1, left + columnWidth - 3, globalY + CONTROL_HEIGHT + 1);
			drawPanel(graphics, right + 1, fogHeadingY - 3, right + columnWidth - 1, fogY + CONTROL_HEIGHT + 2);
		}

		private static void drawPanel(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
			graphics.fill(x1, y1, x2, y2, 0xC9121B2B);
			graphics.outline(x1, y1, x2 - x1, y2 - y1, 0xFF34465F);
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput narration) {}
	}

	private static final class StyledToggle extends AbstractWidget {
		private final String key;
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;
		private final Runnable after;

		StyledToggle(int x, int y, int width, int height, String key, BooleanSupplier get,
				Consumer<Boolean> set, Runnable after) {
			super(x, y, width, height, Component.translatable(key));
			this.key = key;
			this.get = get;
			this.set = set;
			this.after = after;
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			int background = !active ? 0xFF202936 : isHovered() ? 0xFF30465D : 0xFF243449;
			int foreground = active ? 0xFFE5EDF7 : 0xFF7E8998;
			graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
			graphics.outline(getX(), getY(), width, height,
				active ? 0xFF465D78 : 0xFF303B49);
			var font = Minecraft.getInstance().font;
			graphics.text(font, Component.translatable(key), getX() + 5, getY() + 5, foreground);
			int chipX = getX() + width - 31;
			int chipY = getY() + 3;
			int chipColor = !active ? 0xFF303B49 : get.getAsBoolean() ? 0xFF225E50 : 0xFF3B4757;
			graphics.fill(chipX, chipY, chipX + 27, chipY + 12, chipColor);
			Component state = Component.translatable(get.getAsBoolean() ? "options.on" : "options.off");
			int stateX = chipX + (27 - font.width(state)) / 2;
			graphics.text(font, state, stateX, chipY + 2, foreground);
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			if (!active) return;
			set.accept(!get.getAsBoolean());
			after.run();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput narration) {
			defaultButtonNarrationText(narration);
		}
	}

	private static final class ActionControl extends AbstractWidget {
		private final Runnable action;
		private final boolean primary;

		ActionControl(int x, int y, int width, int height, Component label, Runnable action, boolean primary) {
			super(x, y, width, height, label);
			this.action = action;
			this.primary = primary;
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			int background = primary ? (isHovered() ? 0xFF397F9D : 0xFF28647F)
				: (isHovered() ? 0xFF35475D : 0xFF26364A);
			graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
			graphics.outline(getX(), getY(), width, height, 0xFF58718B);
			var font = Minecraft.getInstance().font;
			int textX = getX() + (width - font.width(getMessage())) / 2;
			graphics.text(font, getMessage(), textX, getY() + 5, 0xFFF2F6FA);
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			if (active) action.run();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput narration) {
			defaultButtonNarrationText(narration);
		}
	}

	private static final class StyledSlider extends AbstractSliderButton {
		private final String key;
		private final double min;
		private final double max;
		private final String suffix;
		private final int decimals;
		private final boolean percent;
		private final DoubleConsumer apply;
		private final boolean compact;

		StyledSlider(int x, int y, int width, int height, String key, double initialValue,
				double min, double max, String suffix, int decimals, boolean percent,
				DoubleConsumer apply, boolean compact) {
			super(x, y, width, height, Component.empty(), (initialValue - min) / (max - min));
			this.key = key;
			this.min = min;
			this.max = max;
			this.suffix = suffix;
			this.decimals = decimals;
			this.percent = percent;
			this.apply = apply;
			this.compact = compact;
			updateMessage();
		}

		private double actualValue() {
			return min + value * (max - min);
		}

		@Override
		protected void updateMessage() {
			String formatted = percent ? Math.round(value * 100.0) + "%"
				: String.format(Locale.ROOT, "%." + decimals + "f", actualValue()) + suffix;
			setMessage(Component.translatable(key, formatted));
		}

		@Override
		protected void applyValue() {
			apply.accept(actualValue());
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			int background = !active ? 0xFF202936 : isHovered() ? 0xFF30465D : 0xFF243449;
			int foreground = active ? 0xFFE5EDF7 : 0xFF7E8998;
			graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
			graphics.outline(getX(), getY(), width, height,
				active ? 0xFF465D78 : 0xFF303B49);
			Component visibleText = compact
				? Component.literal("Δ " + String.format(Locale.ROOT, "%+.0f", actualValue()) + "b")
				: getMessage();
			var font = Minecraft.getInstance().font;
			int textX = getX() + (width - font.width(visibleText)) / 2;
			graphics.text(font, visibleText, textX, getY() + 1, foreground);

			int trackLeft = getX() + 6;
			int trackRight = getX() + width - 6;
			int trackY = getY() + height - 5;
			graphics.fill(trackLeft, trackY, trackRight, trackY + 2, 0xFF172230);
			int thumbX = trackLeft + (int) Math.round((trackRight - trackLeft) * value);
			if (thumbX > trackLeft) graphics.fill(trackLeft, trackY, thumbX, trackY + 2, active ? 0xFF54B8C8 : 0xFF596A7D);
			graphics.fill(thumbX - 2, trackY - 2, thumbX + 3, trackY + 4,
				active ? 0xFFE7F4F6 : 0xFF758397);
		}
	}
}
