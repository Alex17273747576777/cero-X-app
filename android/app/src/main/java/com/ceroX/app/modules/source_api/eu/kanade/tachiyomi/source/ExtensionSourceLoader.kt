package eu.kanade.tachiyomi.source

import dalvik.system.DexClassLoader
import java.io.File
import java.util.ServiceLoader

/**
 * Carga extensiones de Mihon/Tachiyomi desde un APK, con aislamiento real.
 *
 * CÓMO FUNCIONA
 * -------------
 * 1. El APK de la extensión trae su `classes.dex`.
 * 2. Se crea un [DexClassLoader] cuyo *parent* es el ClassLoader de la app. Así
 *    la extensión ve el contrato `eu.kanade.tachiyomi.source.*` de esta app y no
 *    trae el suyo.
 * 3. Se lee `META-INF/services/eu.kanade.tachiyomi.source.SourceFactory` del APK,
 *    que es el ServiceLoader estándar de Java.
 * 4. Se instancia la fábrica **desde el ClassLoader de la extensión**, no desde
 *    el de la app: si no, la clase no se encuentra.
 *
 * POR QUÉ EL AISLAMIENTO IMPORTA
 * ------------------------------
 * Una extensión es bytecode de terceros ejecutándose con los permisos de la app.
 * Sin ClassLoader propio, dos extensiones que traigan la misma librería chocan,
 * y una extensión maliciosa o rota puede tumbar el proceso entero. Con
 * ClassLoader propio, cada extensión vive en su espacio y su fallo se contiene.
 *
 * LO QUE ESTE ARCHIVO NO HACE
 * ---------------------------
 * No verifica la firma del APK ni el hash del origen. Eso es indispensable antes
 * de cargar bytecode ajeno, y va en [ExtensionValidator] — este cargador asume
 * que lo que le pasan ya pasó por la verificación. Separar las dos cosas es
 * deliberado: un cargador que también verifica tiende a que se le salte la
 * verificación cuando hay prisa.
 */
class ExtensionSourceLoader(private val cacheDir: File) {

    /**
     * Carga las fuentes de una extensión.
     *
     * @param apk la extensión, ya verificada.
     * @return las fuentes, o lista vacía si el APK no declara ninguna.
     * @throws ExtensionLoadException si el APK no se puede cargar.
     */
    fun load(apk: File): List<Source> {
        if (!apk.isFile) throw ExtensionLoadException("No existe el APK: ${apk.path}")
        if (!apk.canRead()) throw ExtensionLoadException("Sin permiso de lectura: ${apk.path}")

        val dexOut = File(cacheDir, "ext_${apk.nameWithoutExtension}").apply { mkdirs() }

        val loader = try {
            DexClassLoader(
                apk.absolutePath,
                dexOut.absolutePath,
                null,
                // El parent es el ClassLoader de la app: así la extensión resuelve
                // el contrato de este paquete en vez de traer su propia copia.
                javaClass.classLoader,
            )
        } catch (e: Exception) {
            throw ExtensionLoadException("No se pudo abrir el APK de extensión", e)
        }

        val factories = try {
            ServiceLoader.load(SourceFactory::class.java, loader).toList()
        } catch (e: Throwable) {
            // Throwable y no Exception: un APK corrupto puede lanzar
            // NoClassDefFoundError o VerifyError, que no son Exception.
            throw ExtensionLoadException("El APK no declara un SourceFactory válido", e)
        }

        if (factories.isEmpty()) {
            throw ExtensionLoadException(
                "El APK no tiene META-INF/services/eu.kanade.tachiyomi.source.SourceFactory"
            )
        }

        return factories.flatMap { f ->
            try {
                f.createSources()
            } catch (e: Throwable) {
                // Una fuente rota no debe tumbar las demás de la misma extensión.
                emptyList()
            }
        }
    }

    /**
     * Comprueba si un APK declara el contrato sin llegar a instanciar nada.
     * Sirve para decidir antes de cargar, que es más barato que cargar y fallar.
     */
    fun declaresContract(apk: File): Boolean = try {
        val probe = DexClassLoader(
            apk.absolutePath,
            File(cacheDir, "probe").apply { mkdirs() }.absolutePath,
            null,
            javaClass.classLoader,
        )
        probe.getResourceAsStream(
            "META-INF/services/eu.kanade.tachiyomi.source.SourceFactory"
        )?.use { true } ?: false
    } catch (e: Throwable) {
        false
    }
}

/** Fallo al cargar una extensión, con el motivo en lenguaje llano. */
class ExtensionLoadException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
