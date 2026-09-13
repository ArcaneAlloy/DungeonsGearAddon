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
  vea los bytes de la clase. Para el fix actual hace falta
  `dungeons_libraries-1.19.2-x.x.x.jar`.

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
└── pack.mcmeta
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

## Añadir un fix nuevo

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
