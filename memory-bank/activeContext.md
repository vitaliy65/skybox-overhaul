# Active Context

## Latest (насыщенный закат по референсу "Sunset of Seven Suns")
- `SkyPalette`: HORIZON/ZENITH ключи заката перекрашены: горизонт жёлто-оранжевый (0xFFC25A -> 0xFF9E2A -> 0xFF6A1E), верх малиново-красный -> пурпур (0xB93A5E -> 0x92306A -> 0x5A2B7A) -> индиго -> ночь. День и ночь не тронуты.
- `GlowLayers.ALL[0..3]`: насыщеннее и ярче (золото 0xFFD040, оранж 0xFF6A00, малиновый 0xE0305E -> 0x80309A, фиолетовый купол), sigma/alpha больше; Belt of Venus без изменений.
- Проверено Claude: самотесты SkyPalette/GlowLayers/BiomeSky/SkyboxConfig проходят; превью палитры по ключам. НЕ проверено: игра. Если слишком ярко: `sunsetIntensity` / `paletteStrength` в Mod Menu или alpha в `GlowLayers.ALL`.

## Latest (26.3: доп. слои облаков не рисовались)
- Причина: в 26.3 `LevelRenderer` рисует только свой `cloudRenderer`: `render(CloudStatus, RenderPass)` (classic) и `renderOit(CloudStatus, OitStage, GpuTextureView, OitRenderPassProvider.Parameters)` (improved transparency). `prepare(7 args)` вызывается из `prepareTranslucents`. У `MIDDLE`/`UPPER` вызывался только `prepare`.
- Фикс: `CloudRendererMixin.skybox$drawAdditionalLayers` / `...Oit` (HEAD обоих методов, пропуск для наших экземпляров) -> `CloudLayers.drawAdditional` / `drawAdditionalOit` (UPPER, потом MIDDLE, потом рисует ваниль = задний план первым).
- UBO `CloudInfo` в 26.3 = 44 байта (`vec4+vec3+vec3`, `Std140SizeCalculator`), padding по смещению 44 нет -> подсветка облаков закатом отключена (лог warn). `rendertype_clouds.*` в 26.3 не используются.
- НЕ проверено: игра; `renderOit` с общими `cloudParams` для 3 слоёв (общий cloud depth), порядок смешивания.

## Latest (краш 26.3: CloudRendererMixin.writeCloudLighting, IndexOutOfBounds)
- `data.putFloat(28/44)` в `skybox$writeCloudLighting` падал: смаппленный `ubo` в 26.3 короче 48 байт (раскладка `CloudInfo` изменилась). Временно: проверка `data.limit() < 48` -> лог один раз с реальным размером + return (освещение облаков выключено).
- Нужно: декомпилированный 26.3 `CloudRenderer` (+ шейдер `clouds.vsh`, раньше `rendertype_clouds`), чтобы портировать раскладку. Наш `rendertype_clouds.*` в 26.3 скорее всего не используется (переименован в `clouds.*`).

## Latest (краш 26.3: getBaseColor теперь Vector3f)
- Краш при входе в мир: `ClassCastException: Vector3f cannot be cast to Integer` в `AtmosphericFogEnvironmentMixin.skybox$palette`. В 26.3 `AtmosphericFogEnvironment.getBaseColor` возвращает `Vector3f` (RGB 0..1), раньше был `int` ARGB.
- Фикс: `CallbackInfoReturnable<Vector3f>`, конвертация Vector3f -> ARGB int -> `SkyPalette.fog` -> новый Vector3f. `SkyPalette` не менялся (всё ещё int).
- НЕ проверено: что компонентов именно 0..1 (не линейное/другое пространство), `FogData.color` всё ещё `Vector4f` (`FogRendererMixin`, `SkyboxState.FOG`), что `@Redirect Vector3fc.dot(FFF)F` в `getBaseColor` до сих пор то самое место (require=1 прошёл на старте, но смысл мог измениться). Если следующий краш в `FogRendererMixin` - смотреть `FogData`.

## Previous (порт на 26.3: renderpearl + общий RenderPass неба)
- Исправлены только: `render/SkyboxPipelines`, `sky/HorizonHaze`, `sky/TwilightGlow`, `night/NightSky`, `mixin/SkyRendererMixin`.
- 26.3: `com.mojang.blaze3d.*` (RenderPass, GpuBuffer(Slice), GpuTextureView, RenderPipeline, PrimitiveTopology, BlendFunction, DepthStencilState, CompareOp, IndexType, VertexFormat) переехал в `com.mojang.renderpearl.api.{commands,buffers,textures,pipeline,vertex}`. `RenderSystem`, `PoseStack`, `BufferBuilder`, `ByteBufferBuilder`, `MeshData`, `DefaultVertexFormat` остались в `blaze3d`.
- `BindGroupLayouts.MATRICES_PROJECTION` удалён: теперь `PROJECTION` + `DYNAMIC_TRANSFORMS`. `RenderPass.setPipeline` берёт `RenderSystem.getCompiledPipeline(pipeline)`. `RenderPipelines.register` не используем (так делают Iris/Sodium), `AURORA_TYPE` (мёртвый) удалён.
- `SkyRenderer` в 26.3 отдаёт один `RenderPass` во все методы: `renderSkyDisc(RenderPass, Vector3fc)`, `renderStars(RenderPass, float, PoseStack)`, `renderSunriseAndSunset(RenderPass, PoseStack, float, Vector4fc)` (сверено по Iris и Darker-Sky 26.3). Свой `createRenderPass` внутри нельзя: `NightSky/HorizonHaze/TwilightGlow.render(...)` теперь рисуют в переданный pass.
- Состояние неба (rain, sunAngle, биом, зенит) теперь считается в `@ModifyVariable renderSkyDisc` (раньше `extractRenderState`, сигнатура в 26.3 неизвестна). `SkyboxState.sunAngle` берётся из `camera.attributeProbe()` как в `AtmosphericFogEnvironmentMixin`.
- Индексы квадов: свой статический буфер `SkyboxPipelines.quadIndices(n)` (общий `getSequentialBuffer` в 26.3 растёт только в начале кадра). Флаги буферов `GpuBuffer.USAGE_VERTEX | USAGE_COPY_DST` вместо магического `32`.
- НЕ компилировалось (нет Maven). НЕ проверено: что `core/position_color` в 26.3 нужны ровно PROJECTION+DYNAMIC_TRANSFORMS; `IndexType.INT`; ленивая компиляция пайплайна без `register`; `Mth.TWO_PI`, `ByteBufferBuilder.exactlySized`.
- Остались на старом API (вне задачи): `aurora/AuroraRenderer` (blaze3d-импорты, `setPipeline(RenderPipeline)`, `info.indexBuffer()` -> `customIndexBuffer`), `mixin/CloudRendererMixin` (`GpuBufferSlice`, target `DynamicUniforms` -> `DynamicGpuData`), шейдеры `rendertype_clouds.*` -> в 26.3 `clouds.*`.

## Current Focus
v1.2.0: конфиг через Mod Menu (`SkyboxConfig` + `SkyboxConfigScreen`) и полировка. Ждём запуск в игре и правки пользователя.

## Latest (биомные цвета неба + структура пакетов)
- Новое: `sky/BiomeSky` (чистая Java, самотест) + `sky/BiomeGroups` (MC). Группы: SNOW (температура <= 0.05, как у сияния), OCEAN, DESERT, BADLANDS, SAVANNA, JUNGLE, SWAMP (+mangrove), CHERRY. Остальные без тинта. Цвета мягкие, сила `BiomeSky.STRENGTH` = 0.5 (константа, ползунка нет, добавить если попросят).
- Структура: файлы разложены по пакетам `config/ sky/ night/ cloud/ aurora/ render/ mixin/` (см. README, systemPatterns). `fabric.mod.json`: modmenu-entrypoint = `...client.config.SkyboxModMenu`. `SkyPalette.mix` стал public. Удалены мёртвые `src/main/java` (SkyboxOverhaul, ExampleMixin), `skybox-overhaul.mixins.json` (main), `ExampleClientMixin` (не были подключены).
- Проверено Claude: javac по всем исходникам (ошибок доступа/импортов между пакетами нет, остались только символы MC), самотесты `SkyboxConfig`, `SkyPalette`, `BiomeSky`, `GlowLayers` проходят. НЕ проверено: игра; сигнатуры MC в `BiomeGroups` (`Holder.is(ResourceKey)`, `Holder.is(TagKey)`, `BiomeTags.IS_OCEAN/IS_BADLANDS/IS_SAVANNA/IS_JUNGLE`, `Biomes.DESERT/SWAMP/MANGROVE_SWAMP/CHERRY_GROVE`), `camera`-less выбор биома через `mc.player.blockPosition()` (как у сияния).
- Предыдущее: переключатель солнца/луны = встроенный ресурспак `resourcepacks/celestials` (регистрация `ResourceLoader.registerBuiltinPack` в `SkyboxOverhaulClient`, тоже НЕ компилировалась); кодовая отрисовка светил удалена; тело светил ~2.6-3x меньше ванильного.

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
