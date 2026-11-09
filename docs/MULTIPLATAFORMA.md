# Multicompatibilidad de plataforma: qué es y qué se midió

cero-X no tiene cuatro copias del código. Tiene **un solo fuente** de las reglas
(`comun/src/commonMain/kotlin`) que se compila a cuatro destinos:

| destino | cómo | estado medido el 2026-11-09 |
|---|---|---|
| **Android** | la app compila `comun/src/commonMain` dentro de su propio módulo | `:app:compileDebugKotlin` **BUILD SUCCESSFUL** |
| **Escritorio (JVM)** | `comun` es un proyecto Kotlin/JVM | `java -jar cerox-comun-todo.jar estado` arranca y responde |
| **Web (JS)** | target `js(IR) { nodejs() }` + `ApiWeb.kt` | `jsNodeTest` **71 pruebas, 0 fallos** |
| **iOS** | targets `iosX64/iosArm64/iosSimulatorArm64` | declarados; **no se puede enlazar en Linux** (necesita macOS + Xcode) |

## Cómo se engancha Android sin copiar nada

```groovy
// android/app/build.gradle
sourceSets {
    main {
        java.srcDirs += file('../../comun/src/commonMain/kotlin').absolutePath
        java.srcDirs += file('../../comun/src/androidAppMain/kotlin').absolutePath
    }
}
```

`main.java.srcDirs` es donde el plugin de Kotlin de Android busca los `.kt`.
Usar `kotlin.srcDirs` en ese bloque **no compila** (`Could not get unknown
property 'srcDirs'`), y una ruta relativa depende del directorio desde el que se
lance Gradle: por eso `file(...).absolutePath`.

### El detalle de `expect/actual`

`expect fun plataformaReal()` **solo existe en un proyecto multiplataforma**. Si
ese archivo viajara al source set de Android, el compilador diría
«'expect' and 'actual' declarations can be used only in multiplatform projects».

Solución, medida: el archivo con `expect` vive en `comun/src/commonExpect/kotlin`
y se añade **solo** a `commonMain` del proyecto KMP (`kotlin.srcDir(...)`). La app
de Android no lo ve; compila `comun/src/androidAppMain/.../PlataformaRealAndroid.kt`,
que declara la misma función sin `expect` y devuelve `"android"`.

Lo que se intentó y **no** funciona, para no repetirlo:

- Incluir `:comun` como subproyecto de Android → `Cannot add task 'clean'`
  (LifecycleBasePlugin duplicado).
- Aplicar `com.android.library` dentro de `comun` → choque de plugins y
  `'withJava()' is not compatible with Android Plugins`.
- `main.java.exclude` para quitar el archivo con `expect` → el filtro no llegaba
  al compilador de Kotlin, que seguía viéndolo.

## Qué gana cada plataforma con esto

Las reglas compartidas no son decorativas: son las que deciden cosas.

- **Orden natural de capítulos** (`OrdenNatural`): el 10 después del 9, en las
  cuatro plataformas.
- **Cola de descargas con presupuesto** (`PresupuestoDeCola`): nunca más de dos a
  la vez, en Android y en escritorio.
- **Progreso de tracker sin retrocesos** (`PlanDeSincronizacion`): la misma regla
  en Android y en la web.
- **Variantes del español** (`Variante`) y **registro neutro/modismos**
  (`Idiomas`): las mismas tablas en los cuatro destinos.
- **Proveedores de traducción** (`ProveedoresTraduccion`): la forma de la petición
  y el lector de la respuesta se prueban **sin red**, en JVM y en JS.

## Lo que sigue sin ser verdad

- **iOS no tiene binario.** No hay Mac en este entorno. El código compila a
  framework en un macOS con Xcode; aquí no se ha enlazado ni una vez.
- **No hay app de escritorio con ventana.** Hay un `.jar` de 3,4 MB que ejecuta las
  reglas por línea de comandos. Eso no es una aplicación de escritorio.
- **La web no tiene interfaz**: tiene el núcleo compilado a `.js` y 71 pruebas.
