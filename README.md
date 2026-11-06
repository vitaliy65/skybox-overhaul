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

## Config (Mod Menu)

Optional: install [Mod Menu](https://modrinth.com/mod/modmenu) and open Skybox Overhaul's settings. Changes apply live
and are saved to `config/skybox-overhaul.properties` when the screen closes.

- **Sky colours**: 0% = vanilla, 100% = pure palette.
- **Sunset glow**: overall strength of the sunrise/sunset layers.
- **Comet chance per night**: 0-100%.
- **Hide vanilla sunset fan**: off brings vanilla's own sunset fan back on top of ours.

Mod Menu versions: Minecraft 26.1 -> 18.0.2, 26.2 -> 20.0.3, 26.3 -> 21.0.0 (`modmenu_version` in `gradle.properties`).

## Tuning (in code)

- `SkyPalette`: the colour tables (`HORIZON`, `ZENITH`).
- `GlowLayers`: the sunset itself. Each layer in `ALL` has `sigma` (how wide around the sun), `time` (sun elevation
  window), and keyframes `elev`/`rgb`/`alpha` (how high it reaches and its colours).
  Make the sunset reach further: raise the last `elev` values or the `sigma` of a layer.
- `NightSky`: `BUCKETS`, `STARS`.
- `HorizonHaze`: `9.0` = haze height at the horizon (degrees), `1.4` = softness.

Self-checks (no Minecraft needed): compile `SkyPalette`, `GlowLayers`, `SkyboxConfig` with `javac` and run each with `java -ea`.

## License

CC0-1.0 (see `LICENSE`). Change it in `fabric.mod.json` and `LICENSE` if you want something else.
