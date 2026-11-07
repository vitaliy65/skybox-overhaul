# Active Context

## Current Focus
v1.2.0: конфиг через Mod Menu (`SkyboxConfig` + `SkyboxConfigScreen`) и полировка. Ждём запуск в игре и правки пользователя.

## Latest (солнце/луна = обычные текстуры)
- Кодовая отрисовка светил убрана: удалены `Celestials`, миксины `renderSun/renderMoon`, пайплайн `CELESTIAL`, опция `customCelestials` (конфиг, экран, lang). Причина: в игре луна рисовалась поверх солнца.
- Вместо этого мод подменяет ванильные спрайты атласа celestials: `assets/minecraft/textures/environment/celestial/sun.png` и `.../moon/<фаза>.png` (1024x1024, свечение запечено). Позиции ванильные.
- Минус: квад ванильный (солнце 60, луна 40 блоков), тело на текстуре занимает 389/1024 и 263/1024, значит выглядит в ~2.6-3 раза меньше ванильного. Если мало: увеличить тело в PNG или вернуть код с квадом побольше.
- НЕ проверено: игра. Пути папки атласа (`environment/celestial`, `moon/`) взяты по id спрайтов `sun`, `moon/<фаза>`; если осталось ванильное солнце, проверить `atlases/celestials.json`.

## Previous (v1.5.0, змейки сияния: частота, волна высоты, ячейка 8..32)
- `auroraFrequency` (0..1, дефолт 0.4, ползунок «Частота змеек»): патч всегда генерит `MAX_RIBBONS`=8 цепочек, у каждой фиксированный порог `appear`; рисуется, если `appear < frequency`. Живой ползунок, раскладка не перетасовывается. `appearAt[x][z]` заменил `occupied` в `hasCell` (грани между скрытыми и видимыми цепочками не режутся).
- Волна высоты: `AuroraRibbon.heightWave(step, phase, time)` 0.6..1.2, своя длина (18 ячеек) и период (11 с), множится на старую связку высота/activity. Верх луча (а значит и место, где он становится прозрачным) ходит вверх-вниз по цепочке.
- `auroraGridCellSize`: 8..32, дефолт 16 (было 1.5..8, дефолт 3); `PATCH_CELLS` = 128 без изменений.
- Проверено Claude (javac + заглушки MC): `SkyboxConfig` самопроверка, `heightWave` (диапазон, гладкость), частота (0% = ничего, число вершин растёт, раскладка стабильна). НЕ проверено: игра, экран (новый ползунок, `AURORA_ROWS`=8).
- Тюнинг: `HEIGHT_WAVE_MIN/MAX/LENGTH_STEPS/PERIOD`, `MAX_RIBBONS` в блоке `EDIT AURORA HERE`.

## Previous (v1.4.0, анимация сияния + настройки)
- Сияние ожило: `AuroraRibbon.activity()` = бегущая волна вдоль цепочки (~12 ячеек между гребнями, ~7 с) + мерцание каждого луча (2-5 с, 20%) + вспышка раз в ~30 с (~4 с на луче) + «дыхание» высоты ±12%. Выбор пользователя: настроение «живое», один столб = один луч.
- Новые настройки (`SkyboxConfig`, экран в 2 колонки «Небо | Сияние», сияние-опции серые при выключенном): `auroraEnabled`, `auroraColdOnly`, `auroraBrightness` (0..1), `auroraSpeed` (0.25..2), `auroraFlares`. Булевы в конфиге читаются через `bool()`: мусор = дефолт.
- Проверено Claude: `SkyboxConfig` самопроверка + тесты `activity()`/`render` на заглушках Minecraft (границы 0.28..1, без скачков кадр-в-кадр, вспышка ~4 с/30 с). НЕ проверено: игра, `SkyboxConfigScreen` (меняли раскладку; новое: `AbstractWidget.active`, `StringWidget` шириной на обе колонки), визуальный вид в игре.
- Если в игре слишком тускло/резко: константы в блоке `EDIT AURORA HERE` (`WAVE_MIN`, `SHIMMER_DEPTH`, `FLARE_GAIN`, `FLARE_PERIOD` через `FLARE_CYCLE_STEPS/FLARE_SPEED`).

## Previous (v1.3.0, комета-полёт)
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
