# Skybox Overhaul

Client-only Fabric mod for Minecraft 26.1. Keeps the vanilla look (square pixel stars and sun) but makes the sky
colourful and realistic:

- **Day/night gradient** that follows the sun's height (deep blue zenith, pale horizon, indigo night).
- **Wide sunrises and sunsets**: layered gold, orange, pink and violet bands that climb high up the sky and spread
  around it, plus a pink "Belt of Venus" band opposite the sun at dusk. Fades out in rain.
- **Soft horizon** with no seam between sky and fog.
- **Twinkling coloured stars** of different sizes (same constellations as vanilla) and rare comets.

Sources: `src/client/java/skybox/overhaul/client/` (`sky/`, `cloud/`, `config/`, `aurora/`, `mixin/`). Architecture and status: `memory-bank/`.

## Run

`./gradlew runClient`

## Config (Mod Menu)

Optional: install [Mod Menu](https://modrinth.com/mod/modmenu) and open Skybox Overhaul's settings. Changes apply live
and are saved to `config/skybox-overhaul.properties` when the screen closes.

- **Sky colours**: 0% = vanilla, 100% = pure palette.
- **Sunset glow**: overall strength of the sunrise/sunset layers.
- **Comet spawn chance, rolled every 5 s (a comet flies ~20 s, fades in and out)**: 0-100%.
- **Hide vanilla sunset fan**: off brings vanilla's own sunset fan back on top of ours.
- **Aurora grid cell**: horizontal X/Z spacing of the aurora grid, in blocks (default 3).
- **Aurora pillar height**: nominal height of the vertical prisms in blocks; each cell varies slightly around it.

Mod Menu versions: Minecraft 26.1 -> 18.0.2, 26.2 -> 20.0.3, 26.3 -> 21.0.0 (`modmenu_version` in `gradle.properties`).

## Aurora grid

The aurora is generated on a world-aligned X/Z grid. Each occupied cell contains one tall square prism, and adjacent occupied cells form 90-degree snake chains with varied shapes and lengths. The default grid cell is 3x3 blocks; cell spacing and pillar height can be changed live in Mod Menu. The lower section is bright green-cyan, transitions through blue into violet, and only the uppermost part fades smoothly to transparent. The aurora pipeline keeps depth testing enabled but does not write to the depth buffer, so it does not hide clouds behind its own depth writes.

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


## Layered clouds

Cloud rendering keeps Minecraft's vanilla `CloudRenderer` geometry, shader pipeline, fog and graphics-mode behavior, with two additional renderer instances registered through Fabric's client resource loader.

- Base layer: vanilla `minecraft:textures/environment/clouds.png`.
- Middle layer: `skybox-overhaul:textures/environment/clouds_layer_2.png`, +12 blocks.
- Upper layer: `skybox-overhaul:textures/environment/clouds_layer_3.png`, +24 blocks.

The extra masks are transparent RGBA textures and can be replaced by a resource pack.
