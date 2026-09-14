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
├── DungeonsGearGearTypes.java       ← utilidad: es esto un item propio de
│                                        Dungeons Gear (armadura o arma)
├── MigratedGearTag.java             ← utilidad: rastrea el origen y los
│                                        encantamientos innatos de una
│                                        pieza migrada (armadura o arma)
├── BuiltInEnchantmentTooltipFix.java ← fix #5: tooltip consistente
│                                        (color/descripción/nivel real)
└── mixin/
    ├── BuiltInEnchantmentsMixin.java ← fix #1: encantamientos innatos en
    │                                    todas las piezas de un set, no solo
    │                                    en la pieza cuyo slot coincide con
    │                                    la EnchantmentCategory del encantamiento
    ├── FullSetBonusMixin.java        ← fix #2: bonus de set completo (nivel
    │                                    1 → 2) + compatibilidad con Don't
    │                                    Break My Items
    └── BlacksmithUpgradeEnchantMigrationMixin.java
                                       ← fix #4: migra encantamientos innatos
                                         a items fuera de Dungeons Gear

src/main/resources/
├── META-INF/mods.toml               ← metadatos del mod + dependencias
├── ej_fixes.mixins.json             ← registro de mixins
├── pack.mcmeta
├── data/dungeons_gear/gearconfig/armor/*.json
│                                     ← overrides de datapack para dungeons_gear
│                                       (Forge fusiona el data/ del jar de cada
│                                       mod; ordering="AFTER" en mods.toml
│                                       garantiza que estos ganan sobre los
│                                       del propio dungeons_gear.jar)
└── data/bte_mobs/recipes/*.json     ← recetas de upgrade nuevas (fix #3 y #4)
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

**⚠️ Bug corregido (importante):** las primeras versiones de este Mixin
recalculaban el nivel mirando siempre los 4 slots de armadura (casco,
pechera, pantalón, botas), sin importar de qué tipo era el encantamiento.
Para un encantamiento de **arma** (mainhand — Sharpness-style, no aplica
aquí, pero sí los de Dungeons Gear como los de espada/arco), eso significaba
que el Mixin comprobaba los 4 slots de armadura, no encontraba nada ahí, y
devolvía nivel 0 — **anulando en silencio cualquier encantamiento innato de
arma** desde que este fix se activó, sin ningún error visible. Ahora usa
`enchantment.getSlotItems(entity)` (la propia API de Minecraft), que
devuelve los slots correctos para cada encantamiento — los 4 de armadura
para uno de armadura, mainhand para uno de arma — así que el bonus de set
(armor-only) y el nivel base (cualquier tipo) se calculan cada uno con los
slots que le corresponden.

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

## Fix #4 — Migrar encantamientos innatos a items fuera de Dungeons Gear (`bte_mobs`)

**Objetivo:** poder crear recetas de herrero que suban de tier una pieza de
Dungeons Gear a un item de OTRO mod o vanilla (p. ej.
`dungeons_gear:battle_robes_helmet` con Reckless I → `minecraft:iron_helmet`,
o a `twilightforest:ironwood_helmet`), conservando el encantamiento innato.

**Problema:** los encantamientos innatos de Dungeons Gear viven en una
cápsula (capability) aparte, no en el NBT real de encantamientos del item.
`BlacksmithUpgradeRecipe.assemble()` (de `bte_mobs`) ya copia el NBT
completo de la pieza base al resultado, pero esa cápsula solo tiene efecto
en items que sean `ArmorGear`/`MeleeGear`/`BowGear`/`CrossbowGear` — en un
`iron_helmet` normal, esa copia de NBT no tiene a qué "engancharse" y el
encantamiento se pierde en silencio.

**Fix:** `BlacksmithUpgradeEnchantMigrationMixin` engancha
`BlacksmithUpgradeRecipe.assemble()` y, solo cuando el resultado **no** es
uno de esos cuatro tipos de item de Dungeons Gear, escribe los
encantamientos innatos de la pieza base directamente como encantamiento
NBT real en el resultado — el mismo NBT que ya usa toda la maquinaria de
Minecraft (y que los propios encantamientos de Dungeons Gear ya saben leer,
puesto que consultan el nivel de forma genérica vía
`EnchantmentHelper.getEnchantmentLevel`). Si el resultado SÍ es otro item de
Dungeons Gear (p. ej. Spelunker → Cave Crawler), no se toca nada — ese caso
ya funciona correctamente por su cuenta vía su propio `gearconfig`, y
duplicar el encantamiento ahí solo generaría una línea repetida en el
tooltip.

Al enganchar `assemble()` en vez de una receta concreta, esto aplica
automáticamente a **cualquier** receta `bte_mobs:blacksmith_upgrade` que
definas de una pieza de Dungeons Gear hacia un item externo — no hace falta
tocar código para añadir más, solo el JSON de la receta.

Se incluye un ejemplo funcional en
`data/bte_mobs/recipes/ej_fixes_battle_robes_to_iron_helmet.json`
(`battle_robes_helmet` → `iron_helmet`, conservando Reckless I). Dime qué
otras armaduras/piezas — armadura o **arma** — quieres mapear y te añado el
JSON correspondiente. La comprobación `isDungeonsGearGear` ya cubre
`MeleeGear`/`BowGear`/`CrossbowGear` desde el principio, así que este fix
ya migraba correctamente los encantamientos de armas — el hueco real
estaba en cómo se mostraban después (fix #5) y en un bug del bonus de set
que de paso anulaba su nivel (fix #2, corregido arriba).

## Fix #5 — Tooltip: color y descripción consistentes (armadura y arma), nivel real con el set completo

**Problema 1 (color):** una pieza `ArmorGear`/`MeleeGear`/`BowGear`/
`CrossbowGear` original muestra su encantamiento innato en naranja porque
Dungeons Libraries lo pinta así (`DescriptionHelper.onItemTooltip`), sin
distinguir armadura de arma. Una pieza migrada (fix #4) ya no pasa por ese
código — su línea de encantamiento la pinta el renderer genérico de
Minecraft, con el color plano de siempre, sin nada que indique que es un
encantamiento heredado/innato.

**Problema 2 (descripción):** al revés que el color — una pieza migrada
lleva el encantamiento como NBT real, así que algún otro mod del pack que
lee las claves de idioma `enchantment.<namespace>.<path>.desc` le añade
automáticamente una línea de descripción debajo (p. ej. "Small chance to
avoid all damage."). Una pieza original de Dungeons Gear (armadura o arma)
nunca la tiene, porque Dungeons Libraries solo añade el nombre, no la
descripción.

**Problema 3 (nivel, solo armadura):** ni el listener de Dungeons Libraries
ni el renderer genérico de Minecraft saben nada del bonus de
`FullSetBonusMixin`, que se calcula por entidad en el momento en que se
dispara el efecto, no por item al pintar el tooltip. Así que el tooltip
decía "I" aunque el efecto real ya estuviera aplicando "II" con el set
completo puesto. Un arma no tiene "set" que completar, así que esta parte
nunca le aplica.

**Fix:** `BuiltInEnchantmentTooltipFix` es un listener normal de Forge (sin
Mixin, sin problemas de SRG) que escucha el mismo `ItemTooltipEvent` con
prioridad `LOW` — Dungeons Libraries usa la prioridad `NORMAL` por defecto,
así que el nuestro corre después, cuando la línea original ya existe.
Resuelve los tres problemas por separado:

- **Color:** si la pieza es migrada (no `ArmorGear`), sus líneas de
  encantamiento innato se repintan de naranja **siempre**, tengas el set
  completo o no — es solo una cuestión de identidad visual del item. Una
  pieza `ArmorGear` normal no se toca aquí porque ya sale naranja de fábrica.
- **Descripción:** para cualquier encantamiento innato (`ArmorGear` o
  migrado) que tenga clave `.desc` en el idioma y no la muestre ya, se
  inserta esa línea justo debajo del nombre, en gris — así ambos estilos
  quedan iguales.
- **Nivel:** solo si la pieza bajo el cursor es exactamente la que el
  jugador tiene equipada (no una copia suelta en el inventario) y ese
  jugador lleva las 4 piezas del mismo set (rastreado vía
  `MigratedGearTag`), ninguna rota, se sustituye el número por el nivel
  subido (mismo tope de `getMaxLevel()` que usa el propio bonus real), para
  que el tooltip nunca contradiga al efecto real.

## Fix #6 — El bonus de set sobrevive a piezas migradas (`MigratedGearTag`)

**Problema:** en cuanto una pieza deja de ser `ArmorGear` (por ejemplo, tras
migrarla a `minecraft:netherite_helmet` con el Fix #4), tanto
`FullSetBonusMixin` como `BuiltInEnchantmentTooltipFix` dejaban de
reconocerla como parte del set — así que un set "heredado" (4 piezas que
vienen todas del mismo set de Dungeons Gear, aunque ya no lo sean
literalmente) nunca llegaba a nivel II, ni en el efecto real ni en el
tooltip.

**Fix:** `MigratedGearTag` es una utilidad compartida (no un Mixin) que:

1. `BlacksmithUpgradeEnchantMigrationMixin` la usa para escribir, en el NBT
   del resultado migrado, de qué `armorSet` viene la pieza y qué
   encantamientos se migraron como innatos.
2. `FullSetBonusMixin` y `BuiltInEnchantmentTooltipFix` ya no comprueban
   `instanceof ArmorGear` directamente — preguntan a `MigratedGearTag`
   cuál es el "set efectivo" de cada pieza (el suyo propio si sigue siendo
   `ArmorGear`, o el heredado si fue migrada) y si un encantamiento
   concreto es innato en ella (cápsula si es `ArmorGear`, o la lista
   grabada en el NBT si fue migrada).

Con esto, un set de 4 piezas donde alguna (o todas) se hayan migrado a
`iron_helmet`/`netherite_leggings`/`twilightforest:ironwood_*` sigue
contando como "set completo" mientras todas compartan el mismo origen y
ninguna esté rota — el nivel II se aplica igual que con el set original sin
migrar, y el tooltip lo refleja igual.

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
