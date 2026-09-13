# Ender's Journey Fixes (`ej_fixes`)

Addon mod for Forge 1.19.2 that collects compatibility/design fixes for the
mods used by **Ender's Journey — Beyond The End**. Fixes are implemented as
Mixins so they don't require patching or shading any other mod's jar.

## Requisitos

- Java 17
- Gradle 7.5.x (1.19.2 va emparejado con **ForgeGradle 5.1**, no con FG6 —
  FG6 exige Gradle 8.1+). Este proyecto no incluye `gradlew`/`gradlew.bat`/
  `gradle-wrapper.jar` binarios; cópialos de otro proyecto Forge 1.19.2 que
  ya tengas (con wrapper en 7.5.x), o genera el wrapper con
  `gradle wrapper --gradle-version 7.5.1` si tienes Gradle instalado
  localmente
- Copia en `libs/` los jars de cualquier mod al que apunte un `@Mixin`
  (ver `libs/README.txt`). El procesador de anotaciones de Mixin necesita
  encontrar la clase objetivo en el classpath de compilación para validar
  que el método existe, incluso usando `targets = "..."` por string — el
  string solo evita el `import` en tu código, no la necesidad de que el AP
  vea los bytes de la clase. Para los fixes actuales hace falta
  `dungeons_libraries-1.19.2-x.x.x.jar` y, como dependencia transitiva de
  `ArmorGear` (que extiende `GeoArmorItem`), también
  `geckolib-forge-1.19.2-x.x.x.jar`.

## Estructura

```
src/main/java/com/endersjourney/ejfixes/
├── EJFixes.java                     ← clase principal del mod (@Mod)
└── mixin/
    └── BuiltInEnchantmentsMixin.java ← fix #1: encantamientos innatos en
                                         todas las piezas de un set, no solo
                                         en la pieza cuyo slot coincide con
                                         la EnchantmentCategory del encantamiento

src/main/resources/
├── META-INF/mods.toml               ← metadatos del mod + dependencias
├── ej_fixes.mixins.json             ← registro de mixins
├── pack.mcmeta
└── data/dungeons_gear/gearconfig/armor/*.json
                                      ← overrides de datapack para dungeons_gear
                                        (Forge fusiona el data/ del jar de cada
                                        mod; ordering="AFTER" en mods.toml
                                        garantiza que estos ganan sobre los
                                        del propio dungeons_gear.jar)
```

## Compilar

```powershell
.\gradlew build
```

El jar queda en `build/libs/ej_fixes-1.0.0.jar`.

## Fix #1 — Built-in enchantments en todas las piezas del set

**Problema:** en Dungeons Gear, cada `gearconfig/armor/*.json` define sus
`built_in_enchantments` una sola vez para todo el set, pero
`BuiltInEnchantments` (en Dungeons Libraries) filtra esa lista por pieza con
`enchantment.canEnchant(itemStack)` — que depende de la `EnchantmentCategory`
con la que está registrado cada encantamiento en Java (p. ej. `ARMOR_LEGS`
para Melee Aura o Life Steal Aura). Resultado: solo la pieza cuyo tipo
coincide con esa categoría conserva el encantamiento; el resto lo pierde
silenciosamente — aunque el encantamiento está diseñado para activarse desde
cualquier slot de armadura (`ModEnchantmentTypes.ARMOR_SLOT` = casco,
pechera, pantalón, botas).

**Fix:** `BuiltInEnchantmentsMixin` redirige la llamada a `.filter(...)`
dentro del constructor de `BuiltInEnchantments` para que no filtre nada.
Cada pieza del set conserva ahora la lista completa de
`built_in_enchantments`, sin tocar ningún JSON de `dungeons_gear`.

Es un cambio cosmético/de consistencia: `EnchantmentHelper.getEnchantmentLevel`
ya toma el nivel MÁXIMO entre todas las piezas equipadas (no los suma), así
que duplicar el mismo encantamiento en varias piezas no cambia su potencia
ni permite acumularlo.

## Fix #2 — Bonus de set completo (nivel 1 → 2) + compatibilidad con Don't Break My Items

**Objetivo:** si el jugador lleva las 4 piezas del mismo set de Dungeons
Gear, el/los encantamiento(s) innato(s) de ese set suben de nivel 1 a nivel
2 mientras lo lleve puesto — sin tocar ningún JSON — y respetando el estado
"roto" que introduce el mod Don't Break My Items.

**Cómo:** todos los encantamientos "aura" de Dungeons Gear (Melee Aura,
Life Steal Aura, Potion Aura, etc.) consultan su propio nivel cada tick
llamando al método vanilla `EnchantmentHelper.getEnchantmentLevel(Enchantment,
LivingEntity)`. `FullSetBonusMixin` engancha ese único método y, para
encantamientos del namespace `dungeons_gear`, **recalcula el nivel desde
cero** en vez de solo ajustar el resultado de vanilla:

1. Recorre las 4 piezas de armadura equipadas.
2. Para cada pieza, consulta su nivel propio vía la cápsula pública
   `BuiltInEnchantmentsHelper` / `BuiltInEnchantments` de Dungeons
   Libraries — la misma que usa el propio mod internamente.
3. Si Don't Break My Items considera la pieza rota
   (`BrokenItemsEvents.isItemBroken(stack)`), esa pieza no aporta nada a su
   propio nivel base (antes seguía aplicándose aunque estuviera rota, ya
   que la cápsula de encantamientos innatos vive fuera del sistema NBT de
   encantamientos que Don't Break My Items anula).
4. Si las 4 piezas son del mismo set (mismo `armorSet`) **y ninguna está
   rota**, suma +1 al nivel base (antes, una sola pieza rota no impedía el
   bonus mientras las otras 3 siguieran puestas).
5. Aplica el tope del nivel máximo del propio encantamiento
   (`getMaxLevel()`, ya es 2 en casi todos estos casos).

Al enganchar el método de nivel en vez de cada clase de encantamiento, esto
aplica automáticamente a cualquier encantamiento innato actual o futuro de
`dungeons_gear`, incluidos los que añadamos nosotros mismos vía datapack.

**Nota:** el tooltip de cada pieza (p. ej. "Life Steal Aura I") sigue
mostrando el nivel estático guardado en el NBT del objeto — no se actualiza
para reflejar el bonus de set ni el estado roto. Es un detalle puramente
visual; el efecto real ya usa el nivel correcto. Si en algún momento se
quiere corregir también el texto, hace falta un Mixin aparte en el
renderizado de tooltips (lado cliente), no cubierto aquí.

## Fix #3 — Escalado de encantamientos en las recetas de upgrade (`bte_mobs`)

**Problema:** el mod `bte_mobs` (Beyond The End Mobs) define recetas de
herrero (`bte_mobs:blacksmith_upgrade`) que convierten un set de armadura de
Dungeons Gear en otro de rareza superior — p. ej. Spelunker (UNCOMMON) →
Cave Crawler (RARE, unique). En 5 de las 6 cadenas de upgrade de armadura
que existen, el encantamiento innato heredado se quedaba en el mismo nivel
tras el upgrade (o, en el peor caso, desaparecía del todo), en vez de subir
de nivel como cabría esperar de una mejora de equipo.

**Fix:** overrides de datapack en `data/dungeons_gear/gearconfig/armor/`,
igual que el resto de sets — no fue necesario tocar código, solo subir el
nivel del encantamiento compartido:

| Upgrade | Antes | Después |
|---|---|---|
| Spelunker → Cave Crawler | melee_aura I → melee_aura I | melee_aura I → **melee_aura II** |
| Grim → Wither | life_steal_aura I → life_steal_aura I | life_steal_aura I → **life_steal_aura II** |
| Emerald → Gilded Glory | lucky_explorer I → lucky_explorer I + death_barter I | lucky_explorer I → **lucky_explorer II** + death_barter I |
| Emerald → Opulent | lucky_explorer I → lucky_explorer I + opulent_shield I | lucky_explorer I → **lucky_explorer II** + opulent_shield I |
| Climbing → Rugged Climbing | melee_aura I → *(ninguno)* | melee_aura I → **melee_aura II** + explorer I *(del Fix de sets sin encantar)* |

Scale Mail → Reinforced Mail se dejó tal cual (pasa de 0 a 1 encantamiento,
que es progresión normal, no una regresión de nivel).

Todos los niveles resultantes están dentro del `getMaxLevel()` real de cada
encantamiento (melee_aura y life_steal_aura tope en II, lucky_explorer tope
en III), así que ninguno queda inflado por encima de lo que el propio juego
permite.

1. Crea la clase Mixin en `src/main/java/com/endersjourney/ejfixes/mixin/`.
2. Añade su nombre de clase al array `"mixins"` (o `"client"`/`"server"` si
   aplica solo a un lado) de `ej_fixes.mixins.json`.
3. Si el fix apunta a un mod nuevo, copia su jar en `libs/` (para que el
   annotation processor de Mixin pueda resolver el target en compilación) y
   añade un bloque `[[dependencies.${mod_id}]]` en `mods.toml` con
   `ordering="AFTER"` para asegurar que ese mod ya está cargado en runtime.
4. `.\gradlew build` y prueba.

No hace falta tocar `build.gradle` salvo que un fix necesite una dependencia
de compilación nueva (por ejemplo, si en vez de un `@Mixin(targets = "...")`
por string quieres importar directamente una clase de otro mod para tener
autocompletado/chequeo de tipos — en ese caso añade ese mod como dependencia
`compileOnly` apuntando a su jar).
