package com.ceroX.app.modules.extension_sandbox

import android.app.Application
import android.content.Context
import com.ceroX.app.sources.RegistroNox
import com.ceroX.app.sources.RepositorioGlobal
import eu.kanade.tachiyomi.HostExtensiones
import eu.kanade.tachiyomi.source.ExtensionSourceLoader

/** Acción explícita de confianza: valida, ejecuta y registra un APK Nox 1.6 en cero-X. */
class InstaladorInternoNox(
    private val context: Context,
    private val preparador: InstaladorProjectNox = InstaladorProjectNox(context),
) {
    fun ejecutar(extension: ExtensionIndice, autorizarNsfw: Boolean): String {
        require(extension.repositorio == RepositoriosAuditados.MANGA.id) {
            "El lector de manga sólo registra extensiones Nox Manga; Anime usa otro contrato"
        }
        require(!extension.nsfw || autorizarNsfw) {
            "La extensión está marcada NSFW/mixta; actívala y vuelve a autorizarla explícitamente"
        }
        val application = context.applicationContext as? Application
            ?: throw IllegalStateException("No hay Application para el host de extensiones")
        HostExtensiones.inicializar(application)
        val preparado = preparador.preparar(extension)
        val cargada = ExtensionSourceLoader(
            application,
            trustedSignerSha256 = setOf(requireNotNull(RepositoriosAuditados.MANGA.certificadoSha256)),
        ).loadExtension(preparado.archivo)
        require(cargada.paquete == extension.paquete) { "El paquete del APK no coincide con el índice" }
        require(cargada.codigo == extension.codigo) { "La versión del APK no coincide con el índice" }
        val total = RepositorioGlobal.registrarExtension(cargada.paquete, cargada.sources)
        RegistroNox.autorizar(application, cargada.paquete, preparado.archivo)
        return "${cargada.nombre}: $total fuente(s) ejecutándose y registradas en cero-X. " +
            "Ya puedes abrir Catálogo. El código conserva los permisos de la app; la firma no es un sandbox."
    }
}
