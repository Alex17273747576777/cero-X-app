package com.ceroX.app.sources

import android.app.Application
import android.content.Context
import com.ceroX.app.core.AlmacenExterno
import com.ceroX.app.modules.extension_sandbox.RepositoriosAuditados
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import eu.kanade.tachiyomi.HostExtensiones
import eu.kanade.tachiyomi.source.ExtensionSourceLoader
import java.io.File

/** Autorizaciones persistentes; un APK presente en descargas no se ejecuta por estar presente. */
object RegistroNox {
    data class Estado(val extensiones: Int, val fuentes: Int, val errores: Map<String, String>)
    @Volatile
    var ultimoEstado: Estado = Estado(0, 0, emptyMap())
        private set

    private val gson = Gson()

    @Synchronized
    fun autorizar(context: Context, paquete: String, archivo: File) {
        val datos = leer(context).toMutableMap()
        datos[paquete] = archivo.name
        escribir(context, datos)
    }

    @Synchronized
    fun desautorizar(context: Context, paquete: String) {
        val datos = leer(context).toMutableMap()
        datos.remove(paquete)
        escribir(context, datos)
        RepositorioGlobal.bajaExtension(paquete)
    }

    fun restaurar(application: Application): Estado {
        val autorizadas = leer(application)
        if (autorizadas.isEmpty()) return Estado(0, 0, emptyMap()).also { ultimoEstado = it }
        HostExtensiones.inicializar(application)
        val loader = ExtensionSourceLoader(
            application,
            trustedSignerSha256 = setOf(RepositoriosAuditados.FIRMA_PROJECT_NOX),
        )
        val errores = linkedMapOf<String, String>()
        var fuentes = 0
        var extensiones = 0
        autorizadas.forEach { (paquete, nombre) ->
            try {
                require(nombre.matches(Regex("[A-Za-z0-9_.-]+\\.apk"))) { "Nombre de APK no permitido" }
                val base = requireNotNull(AlmacenExterno.directorio(application))
                val apk = File(File(base, "extensiones-nox"), nombre)
                val cargada = loader.loadExtension(apk)
                require(cargada.paquete == paquete) { "El APK autorizado cambió de paquete" }
                fuentes += RepositorioGlobal.registrarExtension(paquete, cargada.sources)
                extensiones++
            } catch (e: Throwable) {
                RepositorioGlobal.bajaExtension(paquete)
                errores[paquete] = e.message ?: e.javaClass.simpleName
            }
        }
        return Estado(extensiones, fuentes, errores).also { ultimoEstado = it }
    }

    private fun archivo(context: Context): File {
        val base = requireNotNull(AlmacenExterno.directorio(context)) {
            "No hay almacenamiento externo para las autorizaciones Nox"
        }
        return File(base, "host-extensiones/autorizadas.json")
    }

    private fun leer(context: Context): Map<String, String> {
        val f = archivo(context)
        if (!f.isFile) return emptyMap()
        require(f.length() <= 1024 * 1024) { "Registro de extensiones excesivo" }
        val tipo = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson<Map<String, String>>(f.readText(Charsets.UTF_8), tipo).orEmpty()
            .filterKeys { it.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) }
    }

    private fun escribir(context: Context, datos: Map<String, String>) {
        val f = archivo(context)
        f.parentFile?.mkdirs()
        val temporal = File(f.parentFile, "${f.name}.tmp")
        try {
            temporal.writeText(gson.toJson(datos.toSortedMap()), Charsets.UTF_8)
            if (f.exists() && !f.delete()) error("No se pudo renovar autorizaciones Nox")
            if (!temporal.renameTo(f)) error("No se pudieron guardar autorizaciones Nox")
        } finally {
            temporal.delete()
        }
    }
}
