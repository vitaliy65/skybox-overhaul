# Tech Context

## Technologies Used
Java 25, Fabric Loom `net.fabricmc.fabric-loom` `1.19-SNAPSHOT`, Fabric Loader 0.20.1, Fabric API `0.145.1+26.1`, Minecraft **26.1** (без обфускации: имена mojmap прямо в коде, `Identifier` вместо `ResourceLocation`). Mixin (compat JAVA_21 в client-конфиге, `defaultRequire: 1`: любой несовпавший injector роняет игру на старте).

## Development Setup
`./gradlew runClient`. Source sets разделены (`splitEnvironmentSourceSets`): `src/main` (общий, почти пустой), `src/client` (весь код). Конфиг миксинов: `src/client/resources/skybox-overhaul.client.mixins.json`.
Самопроверка палитры: `cd src/client/java/skybox/overhaul/client && java -ea SkyPalette.java` (должно напечатать `SkyPalette OK`).

## Technical Constraints
- В среде Claude нет доступа к Maven Fabric/Mojang: **код мода ни разу не компилировался и не запускался Claude**. Проверяет только пользователь.
- Есть только декомпилированные (Fernflower) исходники: `SkyRenderer`, `LevelRenderer`, `RenderPipelines`, `AtmosphericFogEnvironment`, `EnvironmentAttributes`, `FogRenderer` (загружал пользователь). Шейдеров и `FogData`/`Camera` нет.

## Dependencies
`skyboxify` (Modrinth maven `DWuwk8aA:aK1qWuhY`, 2.7) подключён в build.gradle и `depends` в fabric.mod.json, но текущий код его НЕ использует (остаток от первой идеи с ночным кубмапом, см. `tools/generate_night_sky.py`). Можно убрать.

## Факты о vanilla 26.1 (проверены по исходникам)
- Высота солнца: `sin(elev) = cos(SUN_ANGLE)`; 0 = полдень, угол в атрибуте в градусах, `state.sunAngle` в радианах.
- Диск неба: y=16, радиус 512, fan из 10 вершин, пайплайн `SKY` с туманом. Звёзды на расстоянии 100, 1500 штук.
- Fog UBO: цвет + environmentalStart/End + renderDistanceStart/End + skyEnd + cloudEnd. `renderDistanceEnd = chunks*16`, span = clamp(rd/10, 4, 64).
- `RenderPipelines.register(...)` и `MATRICES_PROJECTION_SNIPPET` публичные. Pipelines неба не включают depth state.
- Выделение буфера: `createBuffer(() -> name, 32 /*vertex*/, meshData.vertexBuffer())`.

## НЕПРОВЕРЕННЫЕ предположения (первым делом смотреть в логе при ошибке)
`Mth.TWO_PI`, `Minecraft#getDeltaTracker()`, что `core/position_color` умножает на `ColorModulator`, что pipeline регистрируется вовремя до прекомпиляции шейдеров, что несколько `drawIndexed` с разными `setUniform` в одном pass работают, что `RenderSystem.getModelViewStack()` внутри `renderSkyDisc` = только поворот камеры.
