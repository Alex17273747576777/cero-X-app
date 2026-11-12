package com.ceroX.app.modules.extension_sandbox

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ceroX.app.core.AlmacenExterno
import java.io.File

/**
 * Descarga, comprueba y entrega el APK al instalador de Android.
 * Nunca devuelve «instalada»: esa decisión y su resultado pertenecen a Android.
 */
class InstaladorProjectNox(
    private val context: Context,
    private val descargador: DescargadorRepositorio = DescargadorHttps(),
) {
    fun prepararEInstalar(extension: ExtensionIndice): String {
        val repo = listOf(RepositoriosAuditados.MANGA, RepositoriosAuditados.ANIME)
            .firstOrNull { it.id == extension.repositorio }
            ?: throw IllegalArgumentException("Repositorio no autorizado")
        val base = AlmacenExterno.directorio(context)
            ?: throw IllegalStateException("No hay almacenamiento externo de la aplicación disponible")
        val carpeta = File(base, "extensiones-nox").apply {
            if (!isDirectory && !mkdirs()) throw IllegalStateException("No se pudo crear la carpeta de descargas")
        }
        val destino = File(carpeta, "${extension.paquete}-${extension.codigo}.apk")
        val descargado = if (destino.isFile && destino.length() > 0 &&
            (extension.sha256 == null || sha256(destino) == extension.sha256)
        ) {
            false
        } else {
            destino.delete()
            val url = extension.downloadUrl ?: if (repo.id == RepositoriosAuditados.MANGA.id) {
                ClienteRepositorios(
                    descargador,
                    cacheUrlsManga = File(base, "nox-release-urls-cache.json"),
                ).resolverUrlManga(extension)
            } else {
                repo.urlApk(extension.apk)
            }
            descargador.descargar(url, destino, 100L * 1024 * 1024)
            true
        }
        try {
            extension.sha256?.let { esperado ->
                val real = sha256(destino)
                require(real == esperado) { "SHA-256 no coincide. Se eliminó el APK; no se abrió Android." }
            }
            val validacion = ExtensionValidator(setOf(requireNotNull(repo.certificadoSha256))).validate(destino.path)
            require(validacion.isValid) { validacion.errors.joinToString("; ") }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destino)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return if (descargado) "APK descargado y firma verificada; Android pedirá confirmación" else
                "APK en caché y firma verificada; Android pedirá confirmación"
        } catch (e: Exception) {
            // Un archivo descargado que falla hash o firma nunca queda como caché reutilizable.
            destino.delete()
            throw e
        }
    }
}
