Copia aqui los jars de los mods que un @Mixin necesite resolver en tiempo de
compilacion (el annotation processor de Mixin necesita la clase objetivo en
el classpath, aunque el target este puesto por string).

Para el fix actual (BuiltInEnchantmentsMixin + FullSetBonusMixin) hace falta:
  - dungeons_libraries-1.19.2-x.x.x.jar
  - geckolib-forge-1.19.2-x.x.x.jar
    (dependencia transitiva: ArmorGear extiende GeoArmorItem de GeckoLib,
    asi que el compilador necesita esas clases resueltas tambien, aunque
    no las usemos directamente)
  - dont_break_items-1_19_2-1_19_4-1_3.jar
    (FullSetBonusMixin llama a BrokenItemsEvents.isItemBroken(ItemStack)
    directamente, ya incluido en esta carpeta)
  - BeyondTheEndMobs-1_11_46.jar
    (BlacksmithUpgradeEnchantMigrationMixin apunta directamente a la clase
    BlacksmithUpgradeRecipe de este mod, ya incluido en esta carpeta)

Utiles tambien para fixes futuros:
  - dungeons_gear-1_19_2-5_0_6-beta.jar

Los puedes copiar directamente desde la carpeta mods/ de tu instancia del
modpack. Esta carpeta no se empaqueta ni se sube al jar final: solo se usa
como dependencia "compileOnly" (ver build.gradle).
