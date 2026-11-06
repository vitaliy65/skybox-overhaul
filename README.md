# Skybox Overhaul

Клиентский Fabric-мод для Minecraft 26.1: цветной небесный градиент по высоте солнца, мерцающие разноцветные
пиксельные звёзды разного размера, редкие кометы, мягкий горизонт без шва.

Исходники: `src/client/java/skybox/overhaul/client/`. Архитектура и статус: `memory-bank/` (читать первым), для Claude: `CLAUDE.md`.

## Запуск

`./gradlew runClient`

## Настройка

- `SkyPalette`: цвета (`HORIZON`, `ZENITH`) и `STRENGTH` (0 = vanilla, 1 = полностью палитра).
- `NightSky`: `COMET_PERCENT`, `BUCKETS`, `STARS`.
- `HorizonHaze`: множитель `1.15F` в `render()` управляет высотой плоской зоны у горизонта.

## License

Template code is CC0. Set your own license in `fabric.mod.json` before publishing.
