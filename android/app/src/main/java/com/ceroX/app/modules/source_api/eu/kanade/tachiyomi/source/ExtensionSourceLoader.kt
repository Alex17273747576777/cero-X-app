package eu.kanade.tachiyomi.source

import com.ceroX.app.modules.extension_sandbox.ExtensionValidator
import dalvik.system.DexClassLoader
import java.io.File
import java.util.ServiceLoader
import java.util.zip.ZipFile

/**
 * Las extensiones corren con el UID y los permisos de la app: un ClassLoader NO es un sandbox.
 * Sólo se ejecutan firmantes aprobados por el llamador. La lista vacía deniega toda carga.
 * cacheDir debe ser el directorio privado de código/caché de la aplicación.
 */
class ExtensionSourceLoader(
    private val cacheDir: File,
    private val trustedSignerSha256: Set<String> = emptySet(),
) {
    fun load(apk: File): List<Source> {
        if (!apk.isFile || !apk.canRead()) throw ExtensionLoadException("No existe o no se puede leer el APK")
        if (apk.length() !in 1L..(100L * 1024 * 1024)) throw ExtensionLoadException("APK vacío o demasiado grande")
        if (trustedSignerSha256.isEmpty()) throw ExtensionLoadException("No hay certificados de extensiones aprobados")
        if (!cacheDir.isDirectory && !cacheDir.mkdirs()) throw ExtensionLoadException("No se pudo crear la caché privada")
        // Copiar primero, comprobar después. Nunca verificar una ruta externa y ejecutar otra lectura mutable.
        val snapshot = File.createTempFile("verified-extension-", ".apk", cacheDir)
        try {
            snapshot.outputStream().use { output ->
                // Android 14 exige código dinámico de sólo lectura. El descriptor ya está abierto.
                if (!snapshot.setReadOnly()) throw ExtensionLoadException("No se pudo proteger la copia del APK")
                apk.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 100L * 1024 * 1024) throw ExtensionLoadException("APK demasiado grande")
                        output.write(buffer, 0, count)
                    }
                }
            }
            val result = ExtensionValidator(trustedSignerSha256).validate(snapshot.path)
            if (!result.isValid) throw ExtensionLoadException(result.errors.joinToString("; "))
            val loader = DexClassLoader(snapshot.absolutePath, cacheDir.absolutePath, null, javaClass.classLoader)
            val factories = ServiceLoader.load(SourceFactory::class.java, loader).toList()
            if (factories.isEmpty()) throw ExtensionLoadException("El APK no declara un SourceFactory válido")
            // No ocultar un fallo de inicialización como lista vacía exitosa.
            return factories.flatMap { it.createSources() }
        } catch (e: Throwable) {
            snapshot.delete()
            if (e is ExtensionLoadException) throw e
            throw ExtensionLoadException("No se pudo verificar o cargar la extensión", e)
        }
        // En éxito se conserva la copia de sólo lectura: el ClassLoader puede leerla de forma perezosa.
    }

    /** Sólo inspección ZIP; nunca crea un ClassLoader para una simple consulta. */
    fun declaresContract(apk: File): Boolean = try {
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry("META-INF/services/eu.kanade.tachiyomi.source.SourceFactory")
            entry != null && !entry.isDirectory && entry.size in 1L..65536L
        }
    } catch (e: Exception) {
        false
    }
}

class ExtensionLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
