package skybox.overhaul.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * User settings (edited in Mod Menu, stored as config/skybox-overhaul.properties). Plain static fields read live
 * by the renderers. JDK only on purpose, so it self-checks: javac + java -ea skybox.overhaul.client.SkyboxConfig
 */
public final class SkyboxConfig {
	/** 0 = vanilla sky colours, 1 = pure palette (SkyPalette). */
	public static float paletteStrength = 0.8F;
	/** Overall strength of the sunrise/sunset glow (GlowLayers), 0..1. */
	public static float sunsetIntensity = 1.0F;
	/** Hide vanilla's blocky sunrise/sunset fan (our layers replace it). */
	public static boolean hideVanillaFan = true;
	/** Chance (0..100) that a given in-game day has a comet. */
	public static int cometPercent = 30;
	/** Horizontal size of one aurora grid cell in blocks. */
	public static float auroraGridCellSize = 3.0F;
	/** Nominal height of aurora prisms in blocks; individual cells vary around this value. */
	public static float auroraHeight = 90.0F;

	private SkyboxConfig() {}

	public static void reset() {
		paletteStrength = 0.8F;
		sunsetIntensity = 1.0F;
		hideVanillaFan = true;
		cometPercent = 30;
		auroraGridCellSize = 3.0F;
		auroraHeight = 90.0F;
	}

	/** Missing file, bad numbers or out-of-range values fall back to the defaults / get clamped. */
	public static void load(Path file) {
		reset();
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(file)) {
			p.load(r);
		} catch (IOException | IllegalArgumentException e) {
			return; // no file yet (first start) or unreadable: keep defaults
		}
		paletteStrength = num(p, "paletteStrength", paletteStrength, 1.0F);
		sunsetIntensity = num(p, "sunsetIntensity", sunsetIntensity, 1.0F);
		cometPercent = Math.round(num(p, "cometPercent", cometPercent, 100.0F));
		auroraGridCellSize = rangedNum(p, "auroraGridCellSize", auroraGridCellSize, 1.5F, 8.0F);
		auroraHeight = rangedNum(p, "auroraHeight", auroraHeight, 40.0F, 180.0F);
		hideVanillaFan = Boolean.parseBoolean(p.getProperty("hideVanillaFan", String.valueOf(hideVanillaFan)));
	}

	public static void save(Path file) {
		Properties p = new Properties();
		p.setProperty("paletteStrength", String.valueOf(paletteStrength));
		p.setProperty("sunsetIntensity", String.valueOf(sunsetIntensity));
		p.setProperty("hideVanillaFan", String.valueOf(hideVanillaFan));
		p.setProperty("cometPercent", String.valueOf(cometPercent));
		p.setProperty("auroraGridCellSize", String.valueOf(auroraGridCellSize));
		p.setProperty("auroraHeight", String.valueOf(auroraHeight));
		try (Writer w = Files.newBufferedWriter(file)) {
			p.store(w, "Skybox Overhaul");
		} catch (IOException e) {
			System.err.println("[skybox-overhaul] could not save config: " + e); // settings still apply this session
		}
	}

	private static float rangedNum(Properties p, String key, float def, float min, float max) {
		try {
			float v = Float.parseFloat(p.getProperty(key));
			return Float.isNaN(v) || Float.isInfinite(v) ? def : Math.max(min, Math.min(max, v));
		} catch (RuntimeException e) {
			return def;
		}
	}

	private static float num(Properties p, String key, float def, float max) {
		try {
			float v = Float.parseFloat(p.getProperty(key));
			return Float.isNaN(v) ? def : Math.max(0.0F, Math.min(max, v));
		} catch (RuntimeException e) { // missing key (NPE) or not a number
			return def;
		}
	}

	public static void main(String[] args) throws IOException {
		Path f = Files.createTempFile("skybox", ".properties");
		paletteStrength = 0.25F;
		sunsetIntensity = 0.5F;
		hideVanillaFan = false;
		cometPercent = 77;
		auroraGridCellSize = 4.5F;
		auroraHeight = 120.0F;
		save(f);
		reset();
		load(f);
		check(paletteStrength == 0.25F && sunsetIntensity == 0.5F && !hideVanillaFan && cometPercent == 77
			&& auroraGridCellSize == 4.5F && auroraHeight == 120.0F, "round trip");

		Files.writeString(f, "paletteStrength=9\nsunsetIntensity=abc\ncometPercent=-5\nauroraGridCellSize=0.2\nauroraHeight=999\n");
		load(f);
		check(paletteStrength == 1.0F && sunsetIntensity == 1.0F && cometPercent == 0 && hideVanillaFan
			&& auroraGridCellSize == 1.5F && auroraHeight == 180.0F, "clamp + defaults");

		Files.delete(f);
		load(f);
		check(paletteStrength == 0.8F && cometPercent == 30 && auroraGridCellSize == 3.0F
			&& auroraHeight == 90.0F, "missing file = defaults");
		System.out.println("SkyboxConfig OK");
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
