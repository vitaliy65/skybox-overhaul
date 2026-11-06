# Skybox Overhaul

Client-only Fabric mod for Minecraft 26.1. Keeps the vanilla look (square pixel stars and sun) but makes the sky
colourful and realistic:

- **Day/night gradient** that follows the sun's height (deep blue zenith, pale horizon, indigo night).
- **Wide sunrises and sunsets**: layered gold, orange, pink and violet bands that climb high up the sky and spread
  around it, plus a pink "Belt of Venus" band opposite the sun at dusk. Fades out in rain.
- **Soft horizon** with no seam between sky and fog.
- **Twinkling coloured stars** of different sizes (same constellations as vanilla) and rare comets.

Sources: `src/client/java/skybox/overhaul/client/`. Architecture and status: `memory-bank/`.

## Run

`./gradlew runClient`

## Tuning

- `SkyPalette`: `STRENGTH` (0 = vanilla, 1 = pure palette) and the colour tables (`HORIZON`, `ZENITH`).
- `GlowLayers`: the sunset itself. `INTENSITY` = overall strength; each layer in `ALL` has `sigma` (how wide around
  the sun), `time` (sun elevation window), and keyframes `elev`/`rgb`/`alpha` (how high it reaches and its colours).
  Make the sunset reach further: raise the last `elev` values or the `sigma` of a layer.
  `HIDE_VANILLA_FAN = false` brings back vanilla's own sunset fan on top.
- `NightSky`: `COMET_PERCENT`, `BUCKETS`, `STARS`.
- `HorizonHaze`: `9.0` = haze height at the horizon (degrees), `1.4` = softness.

Self-checks (no Minecraft needed): `java -ea SkyPalette.java` in the client package folder.

## License

Template code is CC0. Set your own license in `fabric.mod.json` before publishing.
