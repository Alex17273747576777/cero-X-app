package eu.kanade.tachiyomi.source

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.ceroX.app.modules.extension_sandbox.ExtensionValidator
import dalvik.system.DexClassLoader
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Cargador Android de extensions-lib 1.6, basado en el flujo de Mihon.
 * Un ClassLoader separa clases, NO permisos: el código conserva el UID de cero-X.
 */
class ExtensionSourceLoader(
    private val context: Context,
    private val cacheDir: File = File(context.codeCacheDir, "extensiones-1.6"),
    private val trustedSignerSha256: Set<String> = emptySet(),
) {
    data class ExtensionCargada(
        val paquete: String,
        val nombre: String,
        val version: String,
        val codigo: Long,
        val nsfw: Boolean,
        val sources: List<Source>,
        val apkVerificado: File,
    )

    // La firma ya se verificó con apksig antes de esta lectura. No pedir
    // certificados otra vez evita que PackageParser vuelva a abrir el APK.
    private val flags = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA

    fun load(apk: File): List<Source> = loadExtension(apk).sources

    fun loadExtension(apk: File): ExtensionCargada {
        validarEntrada(apk)
        if (!cacheDir.isDirectory && !cacheDir.mkdirs()) {
            throw ExtensionLoadException("No se pudo crear la caché privada de código")
        }
        val temporal = File.createTempFile("nox-verificado-", ".apk", cacheDir)
        var definitivo: File? = null
        try {
            copiarLimitado(apk, temporal)
            // Android 14 exige que el código cargado dinámicamente sea de sólo lectura.
            if (!temporal.setReadOnly()) throw ExtensionLoadException("No se pudo proteger la copia del APK")
            val firma = ExtensionValidator(trustedSignerSha256).validate(temporal.path)
            if (!firma.isValid) throw ExtensionLoadException(firma.errors.joinToString("; "))

            val info = context.packageManager.getPackageArchiveInfo(temporal.absolutePath, flags)
                ?: throw ExtensionLoadException("Android no pudo leer el manifiesto de la extensión")
            val appInfo = info.applicationInfo
                ?: throw ExtensionLoadException("La extensión no declara <application>")
            val metadata = appInfo.metaData
                ?: throw ExtensionLoadException("La extensión no declara metadatos Tachiyomi")
            validarManifiesto(info, appInfo, metadata)

            val paquete = info.packageName
            val codigo = PackageInfoCompat.getLongVersionCode(info)
            definitivo = File(cacheDir, "$paquete-$codigo.apk")
            if (definitivo.exists() && !definitivo.delete()) {
                throw ExtensionLoadException("No se pudo sustituir la versión anterior")
            }
            if (!temporal.renameTo(definitivo)) throw IOException("No se pudo fijar el APK verificado")
            if (!definitivo.setReadOnly()) throw ExtensionLoadException("No se pudo proteger el APK definitivo")
            appInfo.sourceDir = definitivo.absolutePath
            appInfo.publicSourceDir = definitivo.absolutePath

            val clases = metadata.getString(METADATA_SOURCE_CLASS).orEmpty()
                .split(';').map { it.trim() }.filter { it.isNotEmpty() }
                .map { if (it.startsWith('.')) paquete + it else it }
            if (clases.isEmpty()) throw ExtensionLoadException("No hay clases fuente utilizables")
            val dex = File(cacheDir, "$paquete-$codigo-dex").apply {
                if (!isDirectory && !mkdirs()) throw ExtensionLoadException("No se pudo crear dexopt")
            }
            val nativas = extraerNativas(definitivo, File(cacheDir, "$paquete-$codigo-lib"))
            val loader = DexClassLoader(definitivo.absolutePath, dex.absolutePath, nativas?.absolutePath, context.classLoader)
            val sources = clases.flatMap { nombre ->
                val objeto = try {
                    Class.forName(nombre, false, loader).getDeclaredConstructor().newInstance()
                } catch (e: Throwable) {
                    throw ExtensionLoadException("No se pudo instanciar $nombre: ${describir(e)}", e)
                }
                when (objeto) {
                    is Source -> listOf(objeto)
                    is SourceFactory -> objeto.createSources()
                    else -> throw ExtensionLoadException("$nombre no implementa Source ni SourceFactory")
                }
            }
            if (sources.isEmpty()) throw ExtensionLoadException("El APK no produjo ninguna fuente")
            val ids = sources.map { it.id }
            if (ids.toSet().size != ids.size) throw ExtensionLoadException("El APK produjo IDs de fuente duplicados")
            val nombre = metadata.getString(METADATA_NAME)
                ?: appInfo.loadLabel(context.packageManager)?.toString()?.substringAfter("Tachiyomi: ")
                ?: paquete
            return ExtensionCargada(
                paquete, nombre, info.versionName.orEmpty(), codigo,
                esNsfw(appInfo), sources, definitivo,
            )
        } catch (e: Throwable) {
            definitivo?.delete()
            if (e is ExtensionLoadException) throw e
            throw ExtensionLoadException("No se pudo verificar o cargar la extensión: ${describir(e)}", e)
        } finally {
            temporal.delete()
        }
    }

    /** Inspección de manifiesto; nunca crea clases ni ejecuta la extensión. */
    fun declaresContract(apk: File): Boolean = try {
        val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
        val app = info?.applicationInfo
        val metadata = app?.metaData
        info != null && app != null && metadata != null &&
            info.reqFeatures?.any { it.name == EXTENSION_FEATURE } == true &&
            !metadata.getString(METADATA_SOURCE_CLASS).isNullOrBlank() &&
            versionLib(info, metadata) == SUPPORTED_LIB_VERSION
    } catch (_: Throwable) {
        false
    }

    private fun validarEntrada(apk: File) {
        if (!apk.isFile || !apk.canRead()) throw ExtensionLoadException("No existe o no se puede leer el APK")
        if (apk.length() !in 1L..MAX_APK_BYTES) throw ExtensionLoadException("APK vacío o demasiado grande")
        if (trustedSignerSha256.isEmpty()) {
            throw ExtensionLoadException("No hay certificados de extensiones aprobados; carga denegada")
        }
    }

    private fun validarManifiesto(info: PackageInfo, app: ApplicationInfo, metadata: android.os.Bundle) {
        if (info.packageName.isNullOrBlank() || info.versionName.isNullOrBlank()) {
            throw ExtensionLoadException("Paquete o versionName ausente")
        }
        if (info.reqFeatures?.any { it.name == EXTENSION_FEATURE } != true) {
            throw ExtensionLoadException("Falta uses-feature $EXTENSION_FEATURE")
        }
        val lib = versionLib(info, metadata)
        if (lib != SUPPORTED_LIB_VERSION) {
            throw ExtensionLoadException("extensionLib $lib no soportada; este host implementa $SUPPORTED_LIB_VERSION")
        }
        if (metadata.getString(METADATA_SOURCE_CLASS).isNullOrBlank()) {
            throw ExtensionLoadException("Falta $METADATA_SOURCE_CLASS")
        }
    }

    private fun versionLib(info: PackageInfo, metadata: android.os.Bundle): String? {
        val valor = metadata.getFloat(METADATA_EXTENSION_LIB).takeUnless { it == 0f }?.toString()
        val version = valor ?: info.versionName?.substringBeforeLast('.')
        return version?.toDoubleOrNull()?.toString()
    }

    private fun esNsfw(app: ApplicationInfo): Boolean {
        val metadata = app.metaData
        return metadata.getInt(METADATA_CONTENT_WARNING) != 0 || metadata.getInt(METADATA_NSFW) == 1
    }

    private fun copiarLimitado(origen: File, destino: File) {
        destino.outputStream().buffered().use { salida ->
            origen.inputStream().buffered().use { entrada ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = entrada.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_APK_BYTES) throw ExtensionLoadException("APK demasiado grande")
                    salida.write(buffer, 0, n)
                }
            }
        }
    }

    /** Extrae sólo la ABI activa y nombres .so estrictos; rechaza Zip Slip y desbordes. */
    private fun extraerNativas(apk: File, destino: File): File? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        ZipFile(apk).use { zip ->
            val entradas = zip.entries().asSequence().filter {
                !it.isDirectory && it.name.startsWith("lib/$abi/") && it.name.endsWith(".so") &&
                    it.name.substringAfterLast('/').matches(Regex("[A-Za-z0-9_.+-]+\\.so"))
            }.toList()
            if (entradas.isEmpty()) return null
            if (!destino.isDirectory && !destino.mkdirs()) throw ExtensionLoadException("No se pudo crear lib dir")
            var total = 0L
            entradas.forEach { entrada ->
                total += entrada.size
                if (entrada.size !in 1L..MAX_NATIVE_BYTES || total > MAX_NATIVE_TOTAL) {
                    throw ExtensionLoadException("Biblioteca nativa excesiva")
                }
                val salida = File(destino, entrada.name.substringAfterLast('/'))
                if (salida.canonicalFile.parentFile != destino.canonicalFile) {
                    throw ExtensionLoadException("Ruta nativa no permitida")
                }
                zip.getInputStream(entrada).use { input ->
                    salida.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var leidos = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            leidos += n
                            if (leidos > MAX_NATIVE_BYTES) throw ExtensionLoadException("Biblioteca nativa excesiva")
                            output.write(buffer, 0, n)
                        }
                    }
                }
                salida.setReadOnly()
            }
        }
        return destino
    }

    private fun describir(e: Throwable): String = generateSequence(e) { it.cause }.last()
        .let { it.message?.takeIf(String::isNotBlank)?.let { m -> "${it.javaClass.simpleName}: $m" } ?: it.javaClass.simpleName }

    companion object {
        const val EXTENSION_FEATURE = "tachiyomi.extension"
        const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
        const val METADATA_NAME = "tachiyomix.name"
        const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
        const val METADATA_NSFW = "tachiyomi.extension.nsfw"
        const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"
        const val SUPPORTED_LIB_VERSION = "1.6"
        private const val MAX_APK_BYTES = 100L * 1024 * 1024
        private const val MAX_NATIVE_BYTES = 64L * 1024 * 1024
        private const val MAX_NATIVE_TOTAL = 256L * 1024 * 1024
    }
}

class ExtensionLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
