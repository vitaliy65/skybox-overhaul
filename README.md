# Skybox Overhaul

A client-side Fabric mod for Minecraft 26.1 that reimagines the sky. It ships sky layers in the
OptiFine format and lets [Skyboxify](https://modrinth.com/mod/skyboxify) render them. No custom render code.

## How it works

- `src/main/resources/assets/minecraft/optifine/sky/world0/skyN.properties`: one file per sky layer (Overworld).
  `world-1` = Nether, `world1` = End. Layers render in order, each on top of the previous. Docs: https://legacy-visuals-project.github.io/Skyboxify/
- `.../optifine/sky/skybox_overhaul/*.png`: the textures, a 3x2 grid of faces (Bottom/Top/South on the top row, West/North/East below).
- `tools/generate_night_sky.py`: generates the placeholder night sky (`pip install numpy pillow`, then `python tools/generate_night_sky.py`).

## Run

`./gradlew runClient`

## License

Template code is CC0. Set your own license in `fabric.mod.json` before publishing.
