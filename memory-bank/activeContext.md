# Active Context

## Current Focus
v1.2.0: конфиг через Mod Menu (`SkyboxConfig` + `SkyboxConfigScreen`) и полировка. Ждём запуск в игре и правки пользователя.

## Latest (v1.3.0, комета-полёт)
- Комета: слот `COMET_SLOT`=100 тиков, шанс `cometPercent` (дефолт 5) на слот, полёт `COMET_LIFE`=400 тиков, дуга 2.4 рад, плавный smoothstep fade (30% вход, 40% выход). Живые кометы из последних `COMET_SLOTS_BACK` слотов рисуются вместе. Без состояния (SplittableRandom от номера слота).
- `cometPercent` теперь «шанс за минуту», дефолт 15 (был 30 «за сутки»). Хвост всегда позади движения (rotateY(pi/2)).
- НЕ компилировалось Claude (только `java -ea SkyboxConfig.java` прошёл). Проверить в игре: скорость, длину хвоста, частоту.

## Previous (v1.2.0)
- Новое: `SkyboxConfig` (чистый JDK, `config/skybox-overhaul.properties`, 4 опции: `paletteStrength`, `sunsetIntensity`, `hideVanillaFan`, `cometPercent`), `SkyboxConfigScreen` (ванильные виджеты, без Cloth Config, применяет вживую, сохраняет в `onClose`), `SkyboxModMenu` (entrypoint `modmenu`), `lang/en_us.json`.
- Убрано: `SkyPalette.STRENGTH`, `GlowLayers.INTENSITY`/`HIDE_VANILLA_FAN`, `NightSky.COMET_PERCENT` (теперь конфиг); мёртвый `main`-entrypoint `SkyboxOverhaul` (src/main/java удалён); заглушки в `fabric.mod.json` (contact, authors).
- Mod Menu: `implementation` в build.gradle + репо Terraformers; в `fabric.mod.json` только `suggests`. Версии: MC 26.1 -> 18.0.2, 26.2 -> 20.0.3, 26.3 -> 21.0.0 (`modmenu_version`).
- Комета: `cometRoll` кэшируется на сутки, порог из конфига сравнивается каждый кадр (ползунок работает сразу).
- НЕ компилировалось Claude: экран использует `AbstractSliderButton`, `StringWidget(...).alignCenter()`, `Button.builder`, `Screen#rebuildWidgets`, поля `font/minecraft/title`, `CommonComponents.GUI_DONE`. Если сборка падает, смотреть сначала их.

## Recent Changes
- Новое: `GlowLayers`, `TwilightGlow`, `SkyboxState.sunAngle`, вызов в `SkyRendererMixin` после haze.
- `SkyPalette`: 10 ключей вместо 9, насыщеннее зенит днём, STRENGTH 0.8.
- Удалено: `ExampleMixin`, `ExampleClientMixin`, main mixin json, Skyboxify (build.gradle, gradle.properties, fabric.mod.json), `tools/`. Добавлена иконка. README переписан.

## Next Steps
1. Пользователь запускает игру: нет ли краша при старте (лог), как выглядит закат. Сторона солнца ПОДТВЕРЖДЕНА по исходникам (FLIP_SIDE=false верно).
2. Тюнинг по скриншотам: `sigma`, `elev` ключи, `alpha` в `GlowLayers.ALL`.
3. Ванильный веер заката отменён миксином `skybox$noFan` (`renderSunriseAndSunset(PoseStack,float,int)`), переключатель `SkyboxConfig.hideVanillaFan`.
4. Подкрутить яркость мелких звёзд и частоту комет.

## Active Decisions & Considerations
- Не писать шейдеры. Не вставлять миксины по неизвестным сигнатурам (`defaultRequire: 1` роняет игру).
- Сторона солнца: sin(sunAngle) > 0 = запад (-X), иначе восток (+X). Подтверждено по `AtmosphericFogEnvironment` и `SkyRenderer.renderSunMoonAndStars` (YP(-90), XP(sunAngle)).

## Important Patterns & Preferences
Новый код минимальный; упрощения помечать `ponytail:`; результат отдавать zip в `/mnt/user-data/outputs/`. Последние сообщения пользователь писал по-английски (отвечать на языке пользователя).

## Learnings & Insights
Полоса зависит от дальности прорисовки, а не от skyEnd. Превью неба удобно считать в Python (формулы те же, что в `GlowLayers`) и смотреть картинкой перед правкой Java.
