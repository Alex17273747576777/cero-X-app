# cero-X · las nueve incompatibilidades que impiden compilar

*Auditoría del sábado 8 de noviembre de 2026 · repositorio `Alex17273747576777/cero-X-app`, rama `main`*

Todo lo que sigue está comprobado leyendo los archivos del repositorio. No hay suposiciones: cada punto trae el archivo y la línea que lo causa.

---

## El estado real, primero

**cero-X no es vaporware.** Tiene 172 archivos de código y 453.540 bytes de fuente real:

| archivo | bytes |
|---|---:|
| `android/app/.../modules/catalog/CatalogModule.kt` | 27.910 |
| `android/app/.../modules/kisara/KisaraModule.kt` | 13.760 |
| `android/app/.../core/CeroXApplication.kt` | 12.788 |
| `android/app/.../core/engine/ModuleLifecycleManager.kt` | 11.920 |
| `core/engine.py` | 10.815 |
| `android/app/.../ui/main/MainActivity.kt` | 8.748 |

Eso es un esqueleto con músculo. El problema no es que falte código: es que **el proyecto no compila**, y por nueve razones concretas.

---

## 🔴 Bloqueantes · sin esto no hay APK

### 1 · El `gradle-wrapper.jar` está vacío

```
android/gradle/wrapper/gradle-wrapper.jar → 11 bytes
```

Un `gradle-wrapper.jar` legítimo pesa **unos 43.000 bytes**. Con 11 bytes, `./gradlew` no arranca en ninguna máquina: falla antes de leer una sola línea de tu código. Es el bloqueante número uno y el más fácil de arreglar.

### 2 · Kotlin 2.0 declarado contra Kotlin 1.9.22 en el classpath

`android/app/build.gradle`:
```groovy
plugins {
    id 'org.jetbrains.kotlin.plugin.compose'   // ← este plugin SOLO existe en Kotlin 2.0+
}
```
`android/build.gradle`:
```groovy
classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22'   // ← 1.9
```

El plugin `kotlin.plugin.compose` se introdujo con Kotlin 2.0. Declararlo con la 1.9.22 en el classpath da `Plugin [id: 'org.jetbrains.kotlin.plugin.compose'] was not found`. **Hay que escoger una:** o Kotlin 2.0 con todo su stack, o Kotlin 1.9 con `composeOptions`.

### 3 · AGP 8.2 contra `compileSdk 36`

```groovy
classpath 'com.android.tools.build:gradle:8.2.0'   // AGP 8.2
compileSdk 36                                      // Android 16
targetSdk 36
```
AGP 8.2 se publicó cuando `compileSdk` 34 era lo más alto. Compilar contra 36 con AGP 8.2 da `Unsupported compileSdkVersion 36`. Se necesita **AGP 8.9 o superior**, y con ello Gradle 8.11+.

### 4 · `composeOptions` con el plugin de Compose 2.0

```groovy
composeOptions {
    kotlinCompilerExtensionVersion = "1.5.8"
}
```
Al usar `org.jetbrains.kotlin.plugin.compose`, el compilador de Compose lo aporta el plugin de Kotlin y **`kotlinCompilerExtensionVersion` ya no se usa**. Dejarlo no siempre rompe, pero cuando el plugin es 2.0 y la extensión declarada es 1.5.8, produce desajustes difíciles de leer.

---

## 🟠 Rompen la compilación o la entrega

### 5 · `JavaVersion.VERSION_1_7` con desugaring activado

```groovy
compileOptions {
    sourceCompatibility JavaVersion.VERSION_1_7
    targetCompatibility JavaVersion.VERSION_1_7
    coreLibraryDesugaringEnabled true
}
```
`coreLibraryDesugaring` exige **Java 8 como mínimo**. Con 1.7 falla. Y aunque no fallara, el desugaring necesita además `coreLibraryDesugaring "com.android.tools:desugar_jdk_libs:2.x"` en `dependencies`, que no aparece.

### 6 · `jvmTarget 1.8` con Compose 1.7

```groovy
kotlinOptions { jvmTarget = '1.8' }
```
Compose 1.7 se compila con Java 11 y arrastra metadatos de esa versión. `jvmTarget 1.8` da `Inconsistent JVM-target compatibility`. Se necesita **`jvmTarget = '11'`** y, a la vez, que `compileOptions` diga 11 — los dos tienen que coincidir.

### 7 · La `release` se firma con la llave de depuración

```groovy
release {
    signingConfig signingConfigs.debug
}
```
Cada APK de release sale firmado con `androiddebugkey`, que es pública: cualquiera puede firmar un APK que el teléfono acepte como actualización. Además `signingConfigs.debug` apunta a `file('../debug.keystore')` **que no existe en el repositorio**, así que ni siquiera compila sin crearlo a mano.

### 8 · Hilt declarado y nunca aplicado

```groovy
id 'com.google.dagger.hilt.android' version '2.50' apply false
```
`apply false` significa exactamente eso: no se aplica. Si el código usa `@HiltAndroidApp` / `@Inject` —y `CeroXApplication.kt` con 12.788 bytes tiene toda la pinta— la compilación falla por símbolos no resueltos. O se aplica el plugin y se añaden `hilt-android` + `kapt`/`ksp`, o se quita del todo.

### 9 · La contradicción de tamaño

`android/app/build.gradle` cierra con este comentario:

```groovy
// Configuración de tamaño objetivo 200-750 MB para APK universal
// Incluye: ONNX Runtime (4 arquitecturas), modelos ONNX, bibliotecas nativas, recursos
```

Pero **no hay una sola dependencia de ONNX Runtime** en el bloque `dependencies`, y **no hay ni un archivo de modelo** en el repositorio. Un APK universal con ONNX para cuatro arquitecturas ronda los **60–90 MB**, sin contar modelos. Los 200–750 MB no salen del código: saldrían de empaquetar modelos dentro del APK, que es justo lo que un instalador limpio no hace.

---

## 🟡 Lo que además está mal

- **El ícono es el marcador por defecto de Android Studio.** `drawable/ic_launcher_foreground.xml` dibuja una gota morada `#6200EE` —el morado primario de Material— sobre `@color/black`. Cero identidad.
- **`largeHeap true`** en `defaultConfig`: le pide al sistema el heap máximo sin justificarlo, y eso es lo contrario de «más eficiente que un kernel de supercomputadora».
- **`android.enableJetifier=true`** en `gradle.properties`: Jetifier existe para migrar librerías viejas de Support Library. Con AndroidX puro es solo tiempo de compilación perdido.
- **`android.nonTransitiveRClass=false`**: engorda las R de cada módulo.
- **`-Xmx4096m`** para un proyecto de este tamaño: 4 GB de heap para compilar 453 KB de fuente.
- **Falta la `AndroidManifest.xml`** en el árbol que se ve por la API de la raíz — hay que confirmar si está y qué declara, porque de ella dependen los permisos y las activities exportadas.
- **`jniLibs { excludes += '**/libc++_shared.so' }`** mientras el comentario de al lado dice *«No excluir .so files - necesitamos todas las arquitecturas»*. Las dos afirmaciones se contradicen en el mismo bloque.

---

## El orden en que hay que arreglarlo

No se puede arreglar todo a la vez: cada punto depende del anterior.

1. **Poner un `gradle-wrapper.jar` de verdad** (Gradle 8.11) — sin esto no hay nada que probar.
2. **Subir AGP a 8.9** y, con él, `compileSdk`/`targetSdk` 36 quedan legítimos.
3. **Decidir Kotlin**: recomiendo **Kotlin 2.0.21 + plugin de Compose 2.0**, y borrar `composeOptions`.
4. **`compileOptions` y `jvmTarget` a 11**, añadir `desugar_jdk_libs`.
5. **Quitar Hilt o aplicarlo de verdad.** Si el código no lo usa todavía, quitarlo es lo honesto.
6. **Crear un keystore real de release** fuera del repositorio, y sacar la firma de depuración de la `release`.
7. **Arreglar el ícono** con algo que sea cero-X y no la gota de Android Studio.
8. **Decidir el tamaño con números**, no con una meta escrita en un comentario.
9. Recién entonces: limpiar `largeHeap`, Jetifier, `nonTransitiveRClass` y `-Xmx4096m`.

---

*Nada de esto se arregló todavía en el repositorio. Este documento es la lista, en orden, para que se arregle una cosa a la vez y se pueda comprobar cada paso.*
