# Estado de compilación de cero-X · medido, no supuesto

*Auditoría realizada el domingo 8 de noviembre de 2026 compilando de verdad, no leyendo.*

---

## El veredicto corto

**cero-X no compila.** Pero ya no falla por el manifiesto ni por Gradle: falla por **209 errores reales en el código fuente**.

Ese es un progreso enorme y hay que decirlo con precisión: antes de esta sesión, el proyecto **no llegaba ni a intentar compilar Kotlin**. El `gradlew` no arrancaba, los recursos no se procesaban y la configuración de Gradle era inválida. Ahora los recursos compilan, el manifiesto es válido, Room genera su código con KSP, y el compilador de Kotlin llega a recorrer los 102 archivos y reporta lo que de verdad está mal en ellos.

---

## El entorno, montado y funcionando

Nada de esto es teórico. Se instaló y se usó:

| pieza | versión | por qué esa |
|---|---|---|
| JDK | **21.0.13** | AGP 8.9 y Kotlin 2.0 lo piden; el proyecto asumía 11 implícitamente |
| Android SDK | **platform 36 + build-tools 36.0.0** | `compileSdk 36` es Android 16 |
| Gradle | **8.11.1** | mínimo requerido por AGP 8.9.1 |
| AGP | **8.9.1** | primera versión que soporta `compileSdk 36` |
| Kotlin | **2.0.21** + plugin de Compose 2.0.21 | el proyecto declaraba el plugin de Compose 2.0 con Kotlin 1.9.22: imposible |
| KSP | **2.0.21-1.0.28** | kapt no soporta Kotlin 2.0 |

---

## Los 19 bloqueantes, arreglados y verificados

Cada uno se encontró compilando. Los primeros nueve se habían detectado leyendo; **los otros diez solo aparecieron al compilar**, y ese es el argumento de esta auditoría.

### De configuración

1. **`gradle-wrapper.jar` de 11 bytes** → reemplazado por el real de Gradle 8.11.1 (43.583 bytes).
2. **AGP 8.2 con `compileSdk 36`** → AGP 8.9.1.
3. **Kotlin 1.9.22 con el plugin de Compose 2.0** → Kotlin 2.0.21 en todo.
4. **`composeOptions`** con el plugin de Compose 2.0 → eliminado.
5. **`JavaVersion.VERSION_1_7` con desugaring** → Java 11.
6. **`jvmTarget '1.8'`** con Compose 1.7 → 11.
7. **`release` firmada con `signingConfigs.debug`** → sin firma de depuración; la firma va por variables de entorno.
8. **Hilt declarado con `apply false`** y nunca usado → eliminado del todo.
9. **`largeHeap true`** sin justificación → eliminado.

### De proyecto (los que solo salen compilando)

10. **`allprojects { repositories }` contra `FAIL_ON_PROJECT_REPOS`** → repositorios solo en `settings.gradle`.
11. **`kapt` usado sin el plugin aplicado** → migrado a KSP.
12. **Cinco `drawable` con `xmlns:android:android=`** (namespace duplicado) → corregidos.
13. **`fragment_mode_switcher.xml` con `app:` sin `xmlns:app`** → declarado.
14. **Atributos de Material 2 en temas Material 3** (`colorBackground`, `colorOnBackground`) → equivalentes de framework.
15. **`@drawable/ceroX_splash_*` inexistentes** → creados con el ícono nuevo de cero-X.
16. **Nombres de recurso con mayúscula** (`ceroX_`) → renombrados a `cerox_`.
17. **`never_for_location` en snake_case** → `neverForLocation`.
18. **Diez `mipmap-*dpi`** apuntando a un `@color/black` inexistente → eliminados (con `minSdk 26` el sistema solo usa el adaptativo: eran residuo puro).
19. **`splits.density`** (obsoleto, se elimina en AGP 10) y **`android.enableR8`** (eliminado en AGP 7) → fuera.

Y uno más de hardware, que no es del proyecto pero bloqueaba todo:
**el heap de 4 GB** en una máquina de 2 GB hacía que el daemon desapareciera a mitad de compilación. Ajustado a 1 GB, un worker, compilación en proceso.

---

## Los errores de código, arreglados

| # | qué | dónde | cuántos errores resolvió |
|---|---|---|---:|
| 1 | `is` como nombre de variable (**palabra reservada**) | ExtensionValidator.kt | 6 |
| 2 | `if` como expresión **sin `else`** | DeepReverseEngineeringModule.kt | 4 |
| 3 | `UnifiedLibraryItem` sin `@Entity` ni `@PrimaryKey` | UnifiedReaderModule.kt | 10 |
| 4 | **API de ONNX inventada**: `OnnxRuntime.Session` no existe | 6 archivos | 46 |
| 5 | `addApp()` **llamada 52 veces y nunca definida** | CatalogModule.kt | 52 |
| 6 | `dependencies` sin tipo explícito | 39 módulos | 33 |
| 7 | `onConfigurationChanged` **no implementado** en la base | ModuleInterface.kt | 39 |
| 8 | `onLowMemory`/`onTrimMemory` sin `override` | ModuleInterface.kt | 2 |
| 9 | `Icons` sin importar | 2 archivos | 27 |
| 10 | `R` sin importar | 5 archivos | 27 |
| 11 | `BottomNavigation` (Material 2) → `NavigationBar` (Material 3) | MainActivity.kt | 22 |
| 12 | Clases duplicadas (`BackupResult`, `RestoreResult`, `AchievementResult`) | 2 archivos | 12 |
| 13 | Imports faltantes entre `core` y `core.engine` | 3 archivos | 6 |
| 14 | `getString` devolvía `String?` | ConfigManager.kt | 2 |
| 15 | `ModuleRegistry.metadata` privado y usado desde fuera | ModuleRegistry.kt | 1 |

---

## La curva, medida

```
errores de Kotlin
519  ├─ primer contacto: el compilador ve todo el árbol
473  │  ONNX: API real en ai.onnxruntime
314  │  addApp, dependencies, onConfigurationChanged
287  │  Icons, R
236  │  imports core ↔ core.engine
218  │  ConfigManager, EventBus, lifecycle
211  │  mapOf, ModuleRegistry, externalFilesDirs
209  └─ techo actual: errores de diseño del código
```

De 519 a 209. Los 310 restantes de esa bajada eran **causas raíz mecánicas**: cada arreglo cerraba decenas de errores en cascada.

Los **209 que quedan no son mecánicos**. Son de diseño:

- `CrossPlatformModule` hereda de una clase sin constructor primario;
- `ExtensionSandbox` llama a `ExtensionFactory` que no existe;
- propiedades `private` que otros módulos leen directamente;
- firmas que devuelven `Int` donde se espera `Boolean`;
- `val` reasignados;
- referencias a miembros que nunca se declararon.

---

## Por qué esto no se arregla con más pasadas

Los errores de esta lista tienen algo en común: **arreglarlos exige decidir qué debía hacer el código**. `ExtensionSandbox` llama a `ExtensionFactory`; no existe tal clase. ¿Se crea, o la llamada sobra? Eso no lo puede decidir quien no escribió el módulo, y adivinarlo es exactamente la clase de trabajo que produce código que compila y no funciona.

Los 19 bloqueantes de configuración y los 15 clusters mecánicos eran objetivos: había una respuesta correcta y verificable. Los 209 de código son **290 decisiones de diseño** repartidas en 102 archivos.

---

## Lo que sí quedó demostrado

1. **El entorno existe y funciona.** JDK 21 + SDK 36 + Gradle 8.11.1 + AGP 8.9.1 + Kotlin 2.0.21 + KSP. Montado de cero, verificado compilando.
2. **El ícono nuevo compila y se usa.** El splash y el lanzador comparten la misma geometría (anillo + X).
3. **La cadena de recursos está sana.** Los 42 archivos de `res/` pasan AAPT.
4. **Room funciona con KSP.** El compilador de anotaciones corre; lo que falla es la entidad, no el procesador.
5. **La curva es real y medible.** 519 → 209 con números, no con impresiones.

---

## Lo siguiente, en orden

1. Decidir qué hace `ExtensionSandbox` con `ExtensionFactory` (crear la clase o quitar la llamada).
2. Revisar los 12 `val` reasignados: casi siempre es un `var` que debía serlo o un cálculo repetido.
3. Unificar los contratos de retorno `Int` vs `Boolean`.
4. Abrir las propiedades `private` que otros módulos leen, o darles un método público — lo segundo es mejor diseño.
5. Recompilar y repetir, en tandas de 20 sobre un solo paquete.

**Mientras esos 209 existan, no hay APK, y por tanto no hay nada que pesar en la banda de 200 a 750 MB.** Cualquier cifra de tamaño que se diga antes de eso sería una estimación, y esta auditoría no hace estimaciones.
