# La regla de almacenamiento, y qué falta para cumplirla

> **Regla:** está total y absolutamente prohibido almacenar cualquier recurso en
> el almacenamiento interno. Única excepción: los tokens.

Este documento existe porque una regla sin inventario se incumple en la siguiente
sesión. El inventario está en el código (`core/InventoryStorage.kt`) y hay una
prueba (`ReglaDeAlmacenamientoTest`, 9 casos) que **falla** si alguien añade un
sitio de escritura sin declararlo.

---

## El detalle que hay que decir en voz alta

`SharedPreferences` y las bases de datos Room **viven en el almacenamiento
interno** (`/data/data/<paquete>/`). No son una zona gris: guardar ahí el progreso
de lectura, la biblioteca o los ajustes es exactamente lo que la regla prohíbe.
Por eso el inventario no dice «cumple», dice cuántas deudas quedan.

## Lo que ya cumple

| sitio | qué | por qué cumple |
|---|---|---|
| `modules/houri/tracker/TrackerCredentials` | token de AniList y MAL | `EncryptedSharedPreferences` con clave del Keystore. **Es la excepción de la regla.** Si el Keystore no está, no se guarda en claro: se devuelve `false` y la pantalla lo avisa. |
| `modules/booru/FavoritosBooruStore` | favoritos de Safebooru | JSON con metadatos en `AlmacenExterno/booru/favoritos.json`. Sin almacenamiento externo, `disponible()` devuelve false y la pantalla lo dice. |
| `modules/booru/DescargasBooru` | imágenes descargadas a petición | Sólo con botón, sólo HTTPS de safebooru.org, sólo `rating:safe`, sólo tipo imagen y 25 MiB de tope. Escribe en `AlmacenExterno/booru/` por un `.tmp` renombrado. Sin externo devuelve el motivo: no cae al interno. |
| `modules/ai/ModelAssets` | modelos ONNX | **Se leen del APK** con `AssetFileDescriptor.openFd()`. Antes se copiaban 373 MiB a `filesDir` en el primer arranque; esa copia ya no existe. |
| `modules/diagnostics/ModelHonestyGuard` | lista de modelos | Ya no crea el directorio de prueba: comprobar una lectura no necesita escribir. |
| `modules/installflow/SilentInstaller` | ¿puede `pm install` leer el APK? | Solo compara rutas; ya no nombra `cacheDir`. |
| `modules/hardware_monitor/HardwareMonitor` | espacio libre y total | Consulta el sistema de archivos. |
| `modules/reader/PageCache` | páginas decodificadas | Todo en memoria, con tope en bytes. |
| `sources/RepositorioGlobal` | carpeta local del usuario | Solo comprueba si existe. |
| `ExtensionSourceLoader` | dex optimizado | `cacheDir` es obligatorio para `DexClassLoader` y el sistema lo borra cuando quiere. Es caché del sistema, no un recurso. |

## Las deudas, con nombre y apellido

Ninguna se disfraza de «pendiente menor». Son 14 sitios:

1. `ui/reader/ReadingProgress` — marcapáginas en un `.properties` de `filesDir`.
2. `modules/reader/LibraryRepository` — la biblioteca en Room.
3. `modules/reader/BibliotecaDb` — la puerta única de esa base (`biblioteca.db`).
4. `modules/houri/SingleSQLiteConnection` — antes abría una **segunda** base
   (`ceroX_database`); ya delega en `BibliotecaDb`, pero el archivo sigue vivo.
5. `modules/backup/BackupManager` — cae a `filesDir` si no hay externo.
6. `core/CrashHandler` — volcados de fallo.
7. `core/ConfigManager` — ajustes en `SharedPreferences`.
8. `modules/houri/tracker/TrackerAutoSync` — mapa obra → id de tracker.
9. `modules/extension_sandbox/ExtensionInstaller` — APK de extensión instalado.
10. `modules/extension_factory/ExtensionFactory` — APK generado.
11. `modules/updates/NightlyUpdater` — APK de actualización descargado.
12. `modules/kisara/MangaOcrEngine` — modelo de OCR.
13. `modules/ai/NeuralAIModule` — rutas de los modelos de colorización/reescalado.
14. (el resto del inventario, ver `InventoryStorage.deudas()`)

## Lo que se arregló en esta pasada

- **373 MiB de modelos duplicados** en `filesDir` → 0. Se leen del APK.
- **Dos bases de datos con el mismo esquema** (`reader_database` y
  `ceroX_database`) → una sola puerta, `BibliotecaDb.de(contexto)`, con doble
  comprobación de hilo. Dos bases no son «una conexión»: son dos mitades que no
  se ven.
- **Un `mkdirs()` que solo ensuciaba**: `ModelHonestyGuard` creaba un directorio
  para comprobar que podía leer un asset.
- **La regla queda verificada**: un archivo nuevo con `filesDir`, `MODE_PRIVATE` o
  `databaseBuilder` sin declarar **rompe la prueba**.

## Lo que esta prueba no puede demostrar

No abre un teléfono. Android escribe cosas por su cuenta en el almacenamiento
interno (dexopt, logcat, los archivos propios de AndroidX). Lo que se demuestra es
que **el código de cero-X** no pide almacenamiento interno fuera de los sitios
declarados, y que el número de esos sitios no crece sin que alguien lo vea.
