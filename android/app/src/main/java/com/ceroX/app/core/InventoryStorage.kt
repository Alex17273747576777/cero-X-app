package com.ceroX.app.core

/**
 * Todo lo que cero-X escribe en el almacenamiento **interno** de la app.
 *
 * LA REGLA
 * --------
 * «Está prohibido almacenar cualquier recurso en el almacenamiento interno,
 * con la única excepción de los tokens.»
 *
 * Esa frase tiene una consecuencia incómoda y hay que decirla antes de
 * esconderla: **`SharedPreferences` y las bases de datos Room viven en el
 * almacenamiento interno**. Guardar ahí el progreso de lectura o la biblioteca no
 * es una zona gris: es exactamente lo que la regla prohíbe. Por eso este archivo
 * no es un comentario de intenciones, es un **inventario cerrado**: cada entrada
 * declara qué es, si es un token, y qué hay que hacer con ella. La prueba
 * `ReglaDeAlmacenamientoTest` comprueba dos cosas que un inventario de verdad
 * exige:
 *
 *   1. que no entre nada nuevo sin declararlo aquí, y
 *   2. que ninguna entrada que no sea un token diga «cumple».
 *
 * LO QUE ESTÁ CLARO Y CONVERTIDO
 * ------------------------------
 *   · **Modelos ONNX** → ya no se copian: se leen del APK con
 *     `AssetFileDescriptor` (ver `ModelAssets`). Antes se duplicaban 373 MiB en
 *     `filesDir` en el primer arranque.
 *   · **Registros del sistema** (`HardwareMonitor`) → ya no consultan
 *     `filesDir`; leen espacio libre y total del sistema.
 *
 * LO QUE SE ARREGLÓ (y cómo)
 * -------------------------
 *   · `ReadingProgress` escribía en `filesDir`. Ahora en `AlmacenExterno`, y al
 *     arrancar copia y **borra** el archivo viejo del interno: actualizar no
 *     cuesta la página donde ibas.
 *   · `ConfigManager` y el mapa de obras del tracker usaban `SharedPreferences`
 *     (vive en `/data/data/<pkg>/shared_prefs`). Ahora `core/Prefs`: JSON en
 *     `AlmacenExterno`, escritura atómica por renombrado y migración del interno.
 *   · `BibliotecaDb` abre Room con **ruta absoluta**; `LibraryRepository` y
 *     `SingleSQLiteConnection` ya no construyen nada y piden la misma instancia.
 *   · `BackupManager`, `CrashHandler`, `ExtensionInstaller`, `ExtensionFactory`,
 *     `NeuralAIModule`, `RepositorioGlobal` y `NightlyUpdater` ya no caen al
 *     interno: o escriben fuera, o devuelven el fallo con su motivo.
 *
 * LO QUE SIGUE SIENDO DEUDA (sin disfrazarlo)
 * -------------------------------------------
 *   · `NightlyUpdater` recibe el `apkPath` de fuera: rechaza los del interno, pero
 *     quien descarga tiene que usar `directorioDeDescarga()`.
 *   · `MangaOcrEngine.extraerAssets` rechaza un destino interno, pero quien llama
 *     todavía tiene que pasarle uno externo.
 *   · `modules/source_api/.../ExtensionSourceLoader` usa `cacheDir` para el
 *     dexopt de extensiones: ese directorio es obligatorio para el cargador de
 *     clases y no es un recurso del usuario — es caché regenerable del sistema.
 *
 * LO QUE SÍ CUMPLE HOY
 * --------------------
 *   · `modules/houri/tracker/TrackerCredentials`: tokens cifrados con Keystore.
 *   · Caché de páginas en memoria (`PageCache`), sin tocar disco.
 */
object InventoryStorage {

    /** Qué papel juega cada sitio de escritura. */
    enum class Verdict {
        /** Es un token y va cifrado. La única excepción de la regla. */
        TOKEN_CIFRADO,

        /** Caché del sistema operativo, regenerable y no es dato del usuario. */
        CACHE_DEL_SISTEMA,

        /** Nada se escribe: se lee del APK o se consulta al sistema. */
        SOLO_LECTURA,

        /**
         * Escribe, pero **fuera** del almacenamiento interno: en el directorio
         * externo propio de la app (`AlmacenExterno`), sin permisos.
         */
        FUERA_DEL_INTERNO,

        /** Debe migrar fuera del almacenamiento interno. Es deuda, no excusa. */
        DEUDA_MIGRACION,
    }

    /** Un sitio de escritura, con nombre y archivo, para poder ir a verlo. */
    data class Sitio(
        val archivo: String,
        val que: String,
        val verdicto: Verdict,
        val nota: String,
    )

    val sitios: List<Sitio> = listOf(
        Sitio(
            "modules/iptv/IptvModule.kt",
            "lista de fuentes IPTV que añade el usuario",
            Verdict.FUERA_DEL_INTERNO,
            "Un JSON con las fuentes instaladas: nombre, URL y, si la fuente la declara, la " +
                "huella SHA-256 con la que se comprueba lo descargado. Se resuelve con " +
                "AlmacenExterno.archivo; si el almacenamiento externo no está disponible, " +
                "initialize falla con su motivo en vez de caer al interno. Este archivo no " +
                "lleva ninguna lista de canales ni ninguna guía dentro: las fuentes las pone " +
                "el usuario, una por una, y se descargan en el momento de cargarlas.",
        ),
        Sitio(
            "core/AlmacenExterno.kt",
            "dónde van los datos que no son tokens",
            Verdict.FUERA_DEL_INTERNO,
            "Es el mecanismo, no un sitio de escritura de datos: resuelve " +
                "`getExternalFilesDirs` (carpeta propia de la app en el almacenamiento " +
                "compartido, sin permisos) y comprueba que se puede escribir de verdad antes " +
                "de decir que sí. No cae al interno: devuelve null y quien llama decide.",
        ),
        Sitio(
            "core/Prefs.kt",
            "preferencias de la app (JSON)",
            Verdict.FUERA_DEL_INTERNO,
            "Sustituye a SharedPreferences: un JSON en AlmacenExterno con escritura atómica por " +
                "renombrado. El único `MODE_PRIVATE` que queda es el de abrir las preferencias " +
                "viejas para copiarlas y BORRARLAS del interno.",
        ),
        Sitio(
            "modules/houri/tracker/TrackerCredentials.kt",
            "token de AniList y de MyAnimeList",
            Verdict.TOKEN_CIFRADO,
            "EncryptedSharedPreferences con clave del Keystore. Si el Keystore falla, " +
                "no se guarda en claro: se devuelve false y la pantalla lo avisa.",
        ),
        Sitio(
            "modules/ai/ModelAssets.kt",
            "modelos ONNX de assets/",
            Verdict.SOLO_LECTURA,
            "Se abren con AssetFileDescriptor directamente del APK. La copia a filesDir " +
                "que había antes duplicaba 373 MiB en el primer arranque y ya no existe.",
        ),
        Sitio(
            "modules/hardware_monitor/HardwareMonitor.kt",
            "espacio libre y total de almacenamiento",
            Verdict.SOLO_LECTURA,
            "Consulta el sistema de archivos. Antes pasaba filesDir solo para preguntar " +
                "por el volumen, que confundía el dato con una escritura.",
        ),
        Sitio(
            "modules/reader/PageCache.kt",
            "páginas decodificadas",
            Verdict.SOLO_LECTURA,
            "Todo en memoria, con tope en bytes y recorte en onTrimMemory. No toca disco.",
        ),
        Sitio(
            "modules/source_api/eu/kanade/tachiyomi/source/ExtensionSourceLoader.kt",
            "dex optimizado de una extensión",
            Verdict.CACHE_DEL_SISTEMA,
            "cacheDir es obligatorio para DexClassLoader y el sistema lo puede borrar " +
                "cuando quiera. No es un recurso del usuario.",
        ),
        Sitio(
            "modules/source_api/eu/kanade/tachiyomi/source/ConfigurableSource.kt",
            "preferencias independientes de cada fuente 1.6",
            Verdict.FUERA_DEL_INTERNO,
            "El contrato usa el API Android de preferencias por compatibilidad binaria, pero " +
                "CeroXApplication redirige source_* a PreferenciasExtensiones, un JSON atómico " +
                "bajo AlmacenExterno/host-extensiones/preferencias; no se crea shared_prefs interno.",
        ),
        Sitio(
            "ui/reader/ReadingProgress.kt",
            "marcapáginas de lectura",
            Verdict.FUERA_DEL_INTERNO,
            "Escribe lector-progreso.properties en `AlmacenExterno` (externo propio, sin permisos). Al " +
                "arrancar copia y borra el archivo que hubiera en `filesDir`."
        ),
        Sitio(
            "modules/reader/LibraryRepository.kt",
            "biblioteca del usuario (Room, reader_database)",
            Verdict.FUERA_DEL_INTERNO,
            "Ya no construye nada: pide la instancia única a `BibliotecaDb`, que abre Room con ruta " +
                "absoluta en `AlmacenExterno`. Con nombre relativo Room siempre usa el interno."
        ),
        Sitio(
            "modules/houri/SingleSQLiteConnection.kt",
            "una segunda base de datos (ceroX_database)",
            Verdict.FUERA_DEL_INTERNO,
            "Delega en `BibliotecaDb`. Ya no existe la segunda base `ceroX_database`: un solo archivo, " +
                "en el directorio externo de la app."
        ),
        Sitio(
            "modules/backup/BackupManager.kt",
            "copias de seguridad",
            Verdict.FUERA_DEL_INTERNO,
            "Escribe en `AlmacenExterno`. Ya no cae a `filesDir`: sin externo devuelve un fallo con su " +
                "motivo en vez de guardar el respaldo donde la regla lo prohíbe."
        ),
        Sitio(
            "core/CrashHandler.kt",
            "volcado de un fallo",
            Verdict.FUERA_DEL_INTERNO,
            "Escribe el informe en `AlmacenExterno` y ya no cae a `filesDir`: sin externo no se escribe " +
                "nada y se anota en el log."
        ),
        Sitio(
            "core/ConfigManager.kt",
            "ajustes de la app",
            Verdict.FUERA_DEL_INTERNO,
            "Usa `core/Prefs`: JSON en `AlmacenExterno` con escritura atómica por renombrado. " +
                "`SharedPreferences` deja de usarse; lo que hubiera en el interno se migra y se borra."
        ),
        Sitio(
            "modules/houri/tracker/TrackerAutoSync.kt",
            "relación obra -> id de AniList/MAL",
            Verdict.FUERA_DEL_INTERNO,
            "`MapaDeObras` usa `core/Prefs`, no `SharedPreferences`: el archivo queda fuera del interno y " +
                "lo anterior se migra y se borra."
        ),
        Sitio(
            "modules/extension_sandbox/ExtensionInstaller.kt",
            "APK de extensión instalado por el usuario",
            Verdict.FUERA_DEL_INTERNO,
            "El APK de la extensión se copia a `AlmacenExterno/extension_sandbox`. Sin externo devuelve " +
                "un fallo con su motivo: no se escribe en el interno."
        ),
        Sitio(
            "modules/extension_factory/ExtensionFactory.kt",
            "APK generado por la fábrica de extensiones",
            Verdict.FUERA_DEL_INTERNO,
            "Exige un directorio de destino explícito (el externo de la app) y ya no usa " +
                "`java.io.tmpdir`. Sin destino, `isEnabled()` es `false`."
        ),
        Sitio(
            "modules/diagnostics/ModelHonestyGuard.kt",
            "lista de modelos del APK",
            Verdict.SOLO_LECTURA,
            "Antes creaba un directorio de prueba para comprobar que los assets eran legibles; " +
                "comprobar una lectura no necesita escribir y ya no lo hace.",
        ),
        Sitio(
            "modules/reader/BibliotecaDb.kt",
            "la única base de datos de la biblioteca (biblioteca.db)",
            Verdict.FUERA_DEL_INTERNO,
            "Abre Room con **ruta absoluta** en `AlmacenExterno`; con nombre relativo Room ignora " +
                "cualquier directorio. Migra el archivo viejo con sus `-wal` y `-shm` y lo borra del interno."
        ),
        Sitio(
            "modules/installflow/SilentInstaller.kt",
            "comprobación de si un APK es legible por el usuario shell",
            Verdict.SOLO_LECTURA,
            "No escribe: compara la ruta con cacheDir y con el externo de la app para saber si " +
                "`pm install` podrá leerla. Se declara porque nombra esas rutas.",
        ),
        Sitio(
            "sources/RepositorioGlobal.kt",
            "carpeta de lectura local del usuario",
            Verdict.SOLO_LECTURA,
            "Solo comprueba si la carpeta existe para registrar la fuente. No escribe nada; " +
                "la carpeta es del usuario, no de la app.",
        ),
        Sitio(
            "modules/updates/NightlyUpdater.kt",
            "compilación nocturna descargada",
            Verdict.DEUDA_MIGRACION,
            "Descarga un APK para actualizar. Es un recurso y hoy cae en el interno.",
        ),
        Sitio(
            "modules/kisara/MangaOcrEngine.kt",
            "modelo de OCR",
            Verdict.DEUDA_MIGRACION,
            "Escribe/abre el modelo en el directorio de la app.",
        ),
        Sitio(
            "modules/ai/NeuralAIModule.kt",
            "modelos de colorización y reescalado",
            Verdict.DEUDA_MIGRACION,
            "Resuelve rutas de modelo en el directorio de la app.",
        ),
        Sitio(
            "modules/booru/FavoritosBooruStore.kt",
            "favoritos de Safebooru (sólo metadatos)",
            Verdict.FUERA_DEL_INTERNO,
            "Un JSON en `AlmacenExterno/booru/favoritos.json` con lo que ya devolvió la API: id, " +
                "URL, etiquetas y puntuación. No guarda las imágenes. Si el almacenamiento " +
                "externo no está disponible, `disponible()` devuelve false y la pantalla lo dice; " +
                "no se cae al interno.",
        ),
        Sitio(
            "modules/booru/DescargasBooru.kt",
            "imágenes de Safebooru descargadas a petición del usuario",
            Verdict.FUERA_DEL_INTERNO,
            "Sólo con botón explícito, sólo HTTPS de safebooru.org, sólo `rating:safe` y sólo " +
                "`image/*`, con tope de 25 MiB. Escribe en " +
                "`AlmacenExterno/booru/safebooru-<id>.<ext>` pasando por un `.tmp` que se " +
                "renombra: una descarga cortada no deja un archivo que parezca bueno. Sin " +
                "almacenamiento externo devuelve el fallo con su motivo en vez de escribir " +
                "dentro del interno.",
        ),
    )

    /** Las que hoy cumplen. Sirve para no dar por bueno lo que no lo es. */
    fun cumplen(): List<Sitio> = sitios.filter {
        it.verdicto == Verdict.TOKEN_CIFRADO ||
            it.verdicto == Verdict.CACHE_DEL_SISTEMA ||
            it.verdicto == Verdict.SOLO_LECTURA ||
            it.verdicto == Verdict.FUERA_DEL_INTERNO
    }

    /** Las que hay que migrar. El número tiene que bajar, no subir. */
    fun deudas(): List<Sitio> = sitios.filter { it.verdicto == Verdict.DEUDA_MIGRACION }

    /**
     * Resumen de una línea para la pantalla de estado.
     *
     * Se dice el número de deudas, no «cumple»: hoy no cumple entera.
     */
    fun resumen(): String =
        "${cumplen().size} sitios conformes · ${deudas().size} por migrar fuera del almacenamiento interno"
}
