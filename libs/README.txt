Copia aqui los jars de los mods que un @Mixin necesite resolver en tiempo de
compilacion (el annotation processor de Mixin necesita la clase objetivo en
el classpath, aunque el target este puesto por string).

Para el fix actual (BuiltInEnchantmentsMixin) hace falta:
  - dungeons_libraries-1.19.2-x.x.x.jar

Utiles tambien para fixes futuros:
  - dungeons_gear-1_19_2-5_0_6-beta.jar

Los puedes copiar directamente desde la carpeta mods/ de tu instancia del
modpack. Esta carpeta no se empaqueta ni se sube al jar final: solo se usa
como dependencia "compileOnly" (ver build.gradle).
