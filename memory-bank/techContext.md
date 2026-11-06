# Tech Context

## Technologies Used
Java 25, Fabric Loom `net.fabricmc.fabric-loom` `1.19-SNAPSHOT`, Fabric Loader 0.20.1, Fabric API `0.145.1+26.1`, Minecraft **26.1** (без обфускации: имена mojmap прямо в коде, `Identifier` вместо `ResourceLocation`). Mixin (compat JAVA_21 в client-конфиге, `defaultRequire: 1`: любой несовпавший injector роняет игру на старте).

## Development Setup
`./gradlew runClient`. Source sets разделены (`splitEnvironmentSourceSets`): `src/main` (общий, почти пустой), `src/client` (весь код). Конфиг миксинов: `src/client/resources/skybox-overhaul.client.mixins.json`.
Самопроверки (чистая Java): скомпилировать `SkyPalette`, `GlowLayers`, `SkyboxConfig` вместе и запустить каждый `java -ea -cp out skybox.overhaul.client.<Имя>` (печатают `... OK`). Одиночный `java -ea X.java` не работает на JDK < 22, т.к. классы зависят друг от друга.

## Technical Constraints
- В среде Claude нет доступа к Maven Fabric/Mojang: **код мода ни разу не компилировался и не запускался Claude**. Проверяет только пользователь.
- Есть только декомпилированные (Fernflower) исходники: `SkyRenderer`, `LevelRenderer`, `RenderPipelines`, `AtmosphericFogEnvironment`, `EnvironmentAttributes`, `FogRenderer` (загружал пользователь). Шейдеров и `FogData`/`Camera` нет.

## Dependencies
Fabric API + Mod Menu (`modmenu_version`: 26.1 -> 18.0.2, 26.2 -> 20.0.3, 26.3 -> 21.0.0; репо `maven.terraformersmc.com/releases/`; только `suggests` в fabric.mod.json). `skyboxify` и `tools/generate_night_sky.py` удалены (v1.1.0).

## Факты о vanilla 26.1 (проверены по исходникам)
- Высота солнца: `sin(elev) = cos(SUN_ANGLE)`; 0 = полдень, угол в атрибуте в градусах, `state.sunAngle` в радианах.
- Диск неба: y=16, радиус 512, fan из 10 вершин, пайплайн `SKY` с туманом. Звёзды на расстоянии 100, 1500 штук.
- Fog UBO: цвет + environmentalStart/End + renderDistanceStart/End + skyEnd + cloudEnd. `renderDistanceEnd = chunks*16`, span = clamp(rd/10, 4, 64).
- `RenderPipelines.register(...)` и `MATRICES_PROJECTION_SNIPPET` публичные. Pipelines неба не включают depth state.
- Выделение буфера: `createBuffer(() -> name, 32 /*vertex*/, meshData.vertexBuffer())`.

## Подтверждено по исходникам (v1.1.0, загружены SkyRenderer, LevelRenderer, RenderPipelines, FogRenderer, AtmosphericFogEnvironment, EnvironmentAttributes)
- Сигнатуры наших миксинов совпадают: `renderStars(float,PoseStack)`, `renderSkyDisc(int)`, `renderSunriseAndSunset(PoseStack,float,int)`, `extractRenderState(...)`, `setupFog(...)`, `getBaseColor(...)`.
- `core/position_color` умножает на ColorModulator (так рисует ванильный SUNRISE_SUNSET). Sky pass идёт с матрицей вращения камеры (`camera.getViewRotationMatrix`), порядок: renderSkyDisc -> renderSunriseAndSunset -> renderSunMoonAndStars -> renderDarkDisc.
- `state.rainBrightness = 1 - rain`; sunAngle/moonAngle/starAngle в радианах; звёзды вращаются отдельным `starAngle`.
- Рецепт `getDynamicUniforms().writeTransform(Matrix4fc, Vector4f, Vector3f, Matrix4f)` и `getSequentialBuffer(Mode.QUADS)` как у ванили.
- Всё ещё не компилировалось Claude; синтаксис проверен (0 ошибок парсинга).

## НЕПРОВЕРЕННЫЕ предположения (первым делом смотреть в логе при ошибке)
`Mth.TWO_PI`, `Minecraft#getDeltaTracker()`, что `core/position_color` умножает на `ColorModulator`, что pipeline регистрируется вовремя до прекомпиляции шейдеров, что несколько `drawIndexed` с разными `setUniform` в одном pass работают, что `RenderSystem.getModelViewStack()` внутри `renderSkyDisc` = только поворот камеры.
