# System Patterns

## Architecture Overview
Всё в `src/client/java/skybox/overhaul/client/`. Миксины тонкие, логика в обычных классах.

| Файл | Роль |
|---|---|
| `SkyPalette` | Чистая Java (без MC): таблица цветов по высоте солнца `s = cos(sunAngle)`; `fog()` и `sky()`; `main()` = самопроверка (`java -ea SkyPalette.java`). `STRENGTH=0.75`. |
| `mixin/AtmosphericFogEnvironmentMixin` | (1) `@Redirect` убирает наклон цвета тумана по направлению взгляда (`Vector3fc.dot`), (2) `@Inject RETURN` на `getBaseColor` подмешивает палитру горизонта. |
| `mixin/SkyRendererMixin` | `extractRenderState` TAIL: цвет зенита (`state.skyColor`); `<init>` TAIL: создаёт `NightSky`+`HorizonHaze`; `renderStars` HEAD cancel -> `NightSky.render`; `renderSkyDisc` TAIL -> `HorizonHaze.render`; `close` HEAD. |
| `mixin/FogRendererMixin` | `setupFog` RETURN: копирует итоговый цвет тумана и дальность прорисовки в `SkyboxState`. |
| `SkyboxState` | Статика render-thread: `FOG` (Vector4f), `rainBrightness`, `valid`. |
| `SkyboxPipelines` | `NIGHT` (position_color, QUADS, blend OVERLAY=аддитивный, как у ванильных звёзд) и `HAZE` (position_color, QUADS, TRANSLUCENT). Регистрируются через `RenderPipelines.register`, `init()` вызывается из `SkyboxOverhaulClient`. |
| `NightSky` | Заменяет ванильные звёзды (см. ниже) + комета. |
| `HorizonHaze` | Рисует ВЕСЬ вертикальный градиент сам, поверх ванильного диска: (1) купол цвета зенита (непрозрачный до 55°, к зениту плавно исчезает), (2) купол цвета тумана с альфой `exp(-(elev/9°)^1.4)` (непрозрачный у/под горизонтом). Ванильная рампа тумана скрыта, линии нет. В дождь альфа = `rainBrightness` (возврат к vanilla). |

## Key Technical Decisions
- **Без своих шейдеров и без Skyboxify-рендера**: только штатные шейдеры `core/position_color`. Исходников ванильных шейдеров 26.1 у нас нет, поэтому их не трогаем.
- **Градиент = туман**: ванильный шейдер неба смешивает диск (цвет зенита) с цветом тумана к горизонту. Поэтому меняем цвет тумана (горизонт) и цвет диска (зенит), а не геометрию.
- **Мерцание без шейдера**: 16 «корзин» звёзд, у каждой свои freq/phase/depth; одна `RenderPass`, на корзину свой `ColorModulator` (alpha) через `setUniform("DynamicTransforms", slice)` + `drawIndexed(baseVertex,...)`. Слайсы uniform создаются ДО открытия pass.
- **Звёзды**: те же позиции, что в vanilla (RNG seed 10842, тот же порядок вызовов: x,y,z,size, затем `nextDouble` для zRot только в валидной ветке). Размер/яркость/цвет/корзина из второго `Random(4242)`.
- **Комета**: статичный меш (гало, голова, 14 квадратов хвоста с затуханием), положение через матрицу: `rotate(cometOrient).rotateX((frac-0.5)*0.9).rotateY(cometTail)`. Появляется в 30% игровых суток (`SplittableRandom(day * golden)`), день = `gameTime / 24000`.
- **HorizonHaze**: сферы радиуса 100, 48 сегментов; кольца по высоте с линейной альфой между ними (шаг 1° у горизонта). Цвета берутся из ванильных значений (`state.skyColor` и `SkyboxState.FOG`), т.е. уже зависят от времени суток. Первая версия (плато + smootherstep, масштаб по дальности прорисовки) убрала линию, но давала слишком высокую бледную зону (~25°), поэтому заменена.

## Critical Implementation Paths
Порядок в `LevelRenderer.addSkyPass`: `renderSkyDisc` -> `renderSunriseAndSunset` -> `renderSunMoonAndStars` (внутри `renderSun`, `renderMoon`, `renderStars`) -> `renderDarkDisc`. Haze рисуется сразу после диска (под закатным свечением, солнцем, луной, звёздами).
Туман: `FogRenderer.setupFog` -> `computeFogColor` -> `AtmosphericFogEnvironment.getBaseColor` (наш inject).
