package skybox.overhaul.client.config;

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
	/** Horizontal size of one aurora grid cell in blocks, 8..32. */
	public static float auroraGridCellSize = 16.0F;
	/** Nominal height of aurora prisms in blocks; individual cells vary around this value. */
	public static float auroraHeight = 90.0F;
	/** Master switch for the aurora (it fades out/in over a few seconds). */
	public static boolean auroraEnabled = true;
	/** true = aurora only in cold biomes (vanilla-like); false = in every biome at night. */
	public static boolean auroraColdOnly = true;
	/** Overall aurora opacity, 0..1. */
	public static float auroraBrightness = 1.0F;
	/** Animation speed multiplier, 0.25..2. */
	public static float auroraSpeed = 1.0F;
	/** Occasional bright pulses running along the aurora ribbons. */
	public static boolean auroraFlares = true;
	/** How many of the generated aurora snakes are shown, 0..1 (0 = none, 1 = all). */
	public static float auroraFrequency = 0.4F;
	/** Number of stars in the night sky, 1500..8000. */
	public static int starCount = 1500;

	/** Shared vertical adjustment for all CloudLayers-managed layers, in blocks (-256..256). */
	public static float globalCloudHeightOffset = 0.0F;
	/** Whether the additional middle cloud layer is rendered. */
	public static boolean middleCloudLayerEnabled = true;
	/** Per-layer vertical adjustment, in blocks (-256..256). */
	public static float middleCloudLayerOffset = 0.0F;
	/** Whether the additional upper cloud layer is rendered. */
	public static boolean upperCloudLayerEnabled = true;
	/** Per-layer vertical adjustment, in blocks (-256..256). */
	public static float upperCloudLayerOffset = 0.0F;

	private SkyboxConfig() {}

	public static void reset() {
		paletteStrength = 0.8F;
		sunsetIntensity = 1.0F;
		hideVanillaFan = true;
		cometPercent = 30;
		auroraGridCellSize = 16.0F;
		auroraHeight = 90.0F;
		auroraEnabled = true;
		auroraColdOnly = true;
		auroraBrightness = 1.0F;
		auroraSpeed = 1.0F;
		auroraFlares = true;
		auroraFrequency = 0.4F;
		globalCloudHeightOffset = 0.0F;
		middleCloudLayerEnabled = true;
		middleCloudLayerOffset = 0.0F;
		upperCloudLayerEnabled = true;
		upperCloudLayerOffset = 0.0F;
		starCount = 1500;
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
		auroraGridCellSize = rangedNum(p, "auroraGridCellSize", auroraGridCellSize, 8.0F, 32.0F);
		auroraFrequency = rangedNum(p, "auroraFrequency", auroraFrequency, 0.0F, 1.0F);
		auroraHeight = rangedNum(p, "auroraHeight", auroraHeight, 40.0F, 180.0F);
		auroraBrightness = rangedNum(p, "auroraBrightness", auroraBrightness, 0.0F, 1.0F);
		auroraSpeed = rangedNum(p, "auroraSpeed", auroraSpeed, 0.25F, 2.0F);
		globalCloudHeightOffset = rangedNum(p, "globalCloudHeightOffset", globalCloudHeightOffset, -256.0F, 256.0F);
		middleCloudLayerEnabled = bool(p, "middleCloudLayerEnabled", middleCloudLayerEnabled);
		middleCloudLayerOffset = rangedNum(p, "middleCloudLayerOffset", middleCloudLayerOffset, -256.0F, 256.0F);
		upperCloudLayerEnabled = bool(p, "upperCloudLayerEnabled", upperCloudLayerEnabled);
		upperCloudLayerOffset = rangedNum(p, "upperCloudLayerOffset", upperCloudLayerOffset, -256.0F, 256.0F);
		hideVanillaFan = bool(p, "hideVanillaFan", hideVanillaFan);
		auroraEnabled = bool(p, "auroraEnabled", auroraEnabled);
		auroraColdOnly = bool(p, "auroraColdOnly", auroraColdOnly);
		auroraFlares = bool(p, "auroraFlares", auroraFlares);
		starCount = Math.round(rangedNum(p, "starCount", starCount, 1500.0F, 8000.0F));
	}

	public static void save(Path file) {
		Properties p = new Properties();
		p.setProperty("paletteStrength", String.valueOf(paletteStrength));
		p.setProperty("sunsetIntensity", String.valueOf(sunsetIntensity));
		p.setProperty("hideVanillaFan", String.valueOf(hideVanillaFan));
		p.setProperty("cometPercent", String.valueOf(cometPercent));
		p.setProperty("auroraGridCellSize", String.valueOf(auroraGridCellSize));
		p.setProperty("auroraHeight", String.valueOf(auroraHeight));
		p.setProperty("auroraEnabled", String.valueOf(auroraEnabled));
		p.setProperty("auroraColdOnly", String.valueOf(auroraColdOnly));
		p.setProperty("auroraBrightness", String.valueOf(auroraBrightness));
		p.setProperty("auroraSpeed", String.valueOf(auroraSpeed));
		p.setProperty("auroraFlares", String.valueOf(auroraFlares));
		p.setProperty("auroraFrequency", String.valueOf(auroraFrequency));
		p.setProperty("globalCloudHeightOffset", String.valueOf(globalCloudHeightOffset));
		p.setProperty("middleCloudLayerEnabled", String.valueOf(middleCloudLayerEnabled));
		p.setProperty("middleCloudLayerOffset", String.valueOf(middleCloudLayerOffset));
		p.setProperty("upperCloudLayerEnabled", String.valueOf(upperCloudLayerEnabled));
		p.setProperty("upperCloudLayerOffset", String.valueOf(upperCloudLayerOffset));
		p.setProperty("starCount", String.valueOf(starCount));
		try (Writer w = Files.newBufferedWriter(file)) {
			p.store(w, "Skybox Overhaul");
		} catch (IOException e) {
			System.err.println("[skybox-overhaul] could not save config: " + e); // settings still apply this session
		}
	}

	/** Only "true"/"false" count; anything else (typo, empty) keeps the default instead of silently meaning false. */
	private static boolean bool(Properties p, String key, boolean def) {
		String v = p.getProperty(key);
		return v == null || !(v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) ? def : Boolean.parseBoolean(v);
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
		auroraGridCellSize = 20.5F;
		auroraHeight = 120.0F;
		auroraEnabled = false;
		auroraColdOnly = false;
		auroraBrightness = 0.4F;
		auroraSpeed = 1.5F;
		auroraFlares = false;
		auroraFrequency = 0.75F;
		starCount = 4200;
		save(f);
		reset();
		load(f);
		check(paletteStrength == 0.25F && sunsetIntensity == 0.5F && !hideVanillaFan && cometPercent == 77
			&& auroraGridCellSize == 20.5F && auroraHeight == 120.0F
			&& !auroraEnabled && !auroraColdOnly && auroraBrightness == 0.4F && auroraSpeed == 1.5F
			&& !auroraFlares && auroraFrequency == 0.75F && starCount == 4200, "round trip");

		Files.writeString(f, "paletteStrength=9\nsunsetIntensity=abc\ncometPercent=-5\nauroraGridCellSize=0.2\nauroraHeight=999\n"
			+ "auroraEnabled=maybe\nauroraBrightness=9\nauroraSpeed=0.01\nauroraFlares=\nauroraFrequency=7\nstarCount=99999\n");
		load(f);
		check(paletteStrength == 1.0F && sunsetIntensity == 1.0F && cometPercent == 0 && hideVanillaFan
			&& auroraGridCellSize == 8.0F && auroraHeight == 180.0F
			&& auroraEnabled && auroraColdOnly && auroraBrightness == 1.0F && auroraSpeed == 0.25F
			&& auroraFlares && auroraFrequency == 1.0F && starCount == 8000, "clamp + defaults");

		Files.delete(f);
		load(f);
		check(paletteStrength == 0.8F && cometPercent == 30 && auroraGridCellSize == 16.0F
			&& auroraHeight == 90.0F && auroraEnabled && auroraColdOnly && auroraBrightness == 1.0F
			&& auroraSpeed == 1.0F && auroraFlares && auroraFrequency == 0.4F && starCount == 1500, "missing file = defaults");
		System.out.println("SkyboxConfig OK");
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError(what);
	}
}
