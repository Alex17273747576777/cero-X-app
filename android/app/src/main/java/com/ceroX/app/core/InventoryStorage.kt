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
 * LO QUE ESTÁ CLARO Y HAY QUE CAMBIAR (sin disfrazarlo)
 * ----------------------------------------------------
 *   · `ui/reader/ReadingProgress` escribe un `.properties` en `filesDir`.
 *   · `modules/reader/LibraryRepository` y `modules/houri/SingleSQLiteConnection`
 *     crean dos bases de datos SQLite distintas para los mismos datos; las dos
 *     en el almacenamiento interno, y una de ellas ni se usa.
 *   · `modules/backup/BackupManager` cae a `filesDir` cuando no hay
 *     almacenamiento externo.
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
            "ui/reader/ReadingProgress.kt",
            "marcapáginas de lectura",
            Verdict.DEUDA_MIGRACION,
            "Escribe lector-progreso.properties en filesDir. Es dato del usuario en el " +
                "almacenamiento interno: la regla lo prohíbe. Va a la base de datos de la " +
                "biblioteca, y esa base de datos también tiene que salir de ahí.",
        ),
        Sitio(
            "modules/reader/LibraryRepository.kt",
            "biblioteca del usuario (Room, reader_database)",
            Verdict.DEUDA_MIGRACION,
            "Base de datos SQLite en el almacenamiento interno. Es la deuda principal.",
        ),
        Sitio(
            "modules/houri/SingleSQLiteConnection.kt",
            "una segunda base de datos (ceroX_database)",
            Verdict.DEUDA_MIGRACION,
            "Duplica el esquema de LibraryRepository en otra base. O sobra una o hay que " +
                "migrar las dos: tener dos es peor que tener una.",
        ),
        Sitio(
            "modules/backup/BackupManager.kt",
            "copias de seguridad",
            Verdict.DEUDA_MIGRACION,
            "Usa el almacenamiento externo de la app y cae a filesDir si no hay. El respaldo " +
                "es del usuario y no puede acabar en el interno por defecto.",
        ),
        Sitio(
            "core/CrashHandler.kt",
            "volcado de un fallo",
            Verdict.DEUDA_MIGRACION,
            "Escribe el informe en el externo de la app y cae a filesDir. Un informe de fallo " +
                "es diagnóstico, no token.",
        ),
        Sitio(
            "core/ConfigManager.kt",
            "ajustes de la app",
            Verdict.DEUDA_MIGRACION,
            "SharedPreferences (que vive en el almacenamiento interno). Los ajustes no son tokens.",
        ),
        Sitio(
            "modules/houri/tracker/TrackerAutoSync.kt",
            "relación obra -> id de AniList/MAL",
            Verdict.DEUDA_MIGRACION,
            "SharedPreferences. El id de una serie es público, así que no es un token, pero " +
                "tampoco es un recurso descargable: es un ajuste y comparte la deuda con ConfigManager.",
        ),
        Sitio(
            "modules/extension_sandbox/ExtensionInstaller.kt",
            "APK de extensión instalado por el usuario",
            Verdict.DEUDA_MIGRACION,
            "filesDir/extension_sandbox. Un APK que el usuario instaló no es caché: es un " +
                "recurso, y la regla lo prohíbe en el interno.",
        ),
        Sitio(
            "modules/extension_factory/ExtensionFactory.kt",
            "APK generado por la fábrica de extensiones",
            Verdict.DEUDA_MIGRACION,
            "Escribe el APK en el directorio de la app antes de instalarlo.",
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
            Verdict.DEUDA_MIGRACION,
            "Es la puerta única: sustituye a las dos bases que había. Sigue en el almacenamiento " +
                "interno y por eso sigue siendo deuda; lo que se arregló es que ya no haya dos.",
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
    )

    /** Las que hoy cumplen. Sirve para no dar por bueno lo que no lo es. */
    fun cumplen(): List<Sitio> = sitios.filter {
        it.verdicto == Verdict.TOKEN_CIFRADO ||
            it.verdicto == Verdict.CACHE_DEL_SISTEMA ||
            it.verdicto == Verdict.SOLO_LECTURA
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
