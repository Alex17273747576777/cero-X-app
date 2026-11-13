package com.ceroX.app.modules.booru

import android.content.Context
import com.ceroX.app.core.AlmacenExterno
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Resultado de una descarga pedida por el usuario. Nunca dice «ok» sin archivo. */
sealed class ResultadoDescarga {
    data class Exito(val ruta: String, val bytes: Long, val yaExistia: Boolean) : ResultadoDescarga()
    data class Fallo(val motivo: String) : ResultadoDescarga()
}

/**
 * Descarga explícita de una imagen de Safebooru al almacenamiento externo.
 *
 * LA REGLA QUE CUMPLE
 * -------------------
 * Los recursos no van al almacenamiento interno. Aquí el destino se resuelve con
 * [AlmacenExterno]; si el almacenamiento externo no está disponible, **se
 * devuelve el fallo con su motivo** y no se escribe nada. No hay caída silenciosa
 * a `filesDir` ni a `java.io.tmpdir`.
 *
 * POR QUÉ HAY DOS COSTURAS
 * ------------------------
 * [resolverDestino] y [ejecutar] se inyectan. Sin ellas, las únicas pruebas
 * posibles serían con red real y un teléfono; con ellas se comprueban en la JVM
 * los casos que de verdad rompen: host ajeno, contenido que no es imagen,
 * respuesta mayor que el tope, almacenamiento ausente y archivo ya guardado.
 *
 * LÍMITES
 * -------
 * Sólo HTTPS a `safebooru.org` (lo comprueba [urlMediaSafebooru]), sólo
 * `rating:safe`, sólo respuestas de tipo imagen y como mucho [MAX_BYTES]. Se escribe en un
 * `.tmp` y se renombra: una descarga cortada no deja un archivo que parezca
 * bueno. Nada se descarga solo: hace falta el botón.
 *
 * Bloquea el hilo que la llame; la pantalla la invoca en `Dispatchers.IO`.
 */
class DescargasBooru(
    private val resolverDestino: (nombre: String) -> File?,
    private val ejecutar: (Request) -> Response,
    private val maxBytes: Long = MAX_BYTES,
) {

    fun descargar(post: BooruPost, url: String): ResultadoDescarga {
        val segura = urlMediaSafebooru(url)
            ?: return ResultadoDescarga.Fallo(
                "Sólo se descargan imágenes HTTPS de safebooru.org: $url no pasa el filtro",
            )
        if (post.rating !in setOf("safe", "s")) {
            return ResultadoDescarga.Fallo("La publicación #${post.id} no es rating:safe; no se descarga")
        }
        val nombre = nombreArchivo(post, segura)
        val destino = resolverDestino(nombre)
            ?: return ResultadoDescarga.Fallo(
                "Sin almacenamiento externo disponible: no se guarda en el interno",
            )

        val peticion = Request.Builder()
            .url(segura)
            .header("Accept", "image/*")
            .header("User-Agent", "cero-X/1.0 (+Safebooru public API; rating:safe)")
            .build()

        val respuesta = runCatching { ejecutar(peticion) }.getOrElse {
            return ResultadoDescarga.Fallo(
                "No se descargó: ${it.message ?: it.javaClass.simpleName}",
            )
        }

        return respuesta.use { r ->
            if (!r.isSuccessful) return ResultadoDescarga.Fallo("HTTP ${r.code}")
            val cuerpo = r.body
            val tipo = cuerpo.contentType()
            if (tipo == null || tipo.type != "image") {
                return ResultadoDescarga.Fallo(
                    "La respuesta no es una imagen (${tipo ?: "sin tipo de contenido"})",
                )
            }
            val declarado = cuerpo.contentLength()
            if (declarado > maxBytes) {
                return ResultadoDescarga.Fallo(
                    "La imagen declara ${legible(declarado)} y el tope es ${legible(maxBytes)}",
                )
            }
            if (destino.isFile && declarado > 0 && destino.length() == declarado) {
                return ResultadoDescarga.Exito(destino.absolutePath, destino.length(), yaExistia = true)
            }

            destino.parentFile?.mkdirs()
            val temporal = File(destino.parentFile, destino.name + ".tmp")
            var escritos = 0L
            try {
                temporal.outputStream().use { salida ->
                    cuerpo.byteStream().use { entrada ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val leidos = entrada.read(buffer)
                            if (leidos < 0) break
                            escritos += leidos
                            if (escritos > maxBytes) {
                                throw IllegalStateException(
                                    "La descarga superó el tope de ${legible(maxBytes)} y se cortó",
                                )
                            }
                            salida.write(buffer, 0, leidos)
                        }
                    }
                }
                if (escritos == 0L) {
                    temporal.delete()
                    return ResultadoDescarga.Fallo("La respuesta venía vacía: no se creó ningún archivo")
                }
                if (destino.isFile) destino.delete()
                if (!temporal.renameTo(destino)) {
                    temporal.copyTo(destino, overwrite = true)
                    temporal.delete()
                }
                ResultadoDescarga.Exito(destino.absolutePath, escritos, yaExistia = false)
            } catch (e: Exception) {
                temporal.delete()
                ResultadoDescarga.Fallo(
                    if (e is IllegalStateException) {
                        e.message ?: "Se superó el tope de tamaño"
                    } else {
                        "No se pudo escribir en el almacenamiento externo: " +
                            (e.message ?: e.javaClass.simpleName)
                    },
                )
            }
        }
    }

    companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
        private const val MIB = 1024L * 1024L

        /** Tamaño legible en un mensaje: MiB con un decimal a partir de 1 MiB, bytes por debajo. */
        private fun legible(bytes: Long): String = if (bytes >= MIB) {
            val decimas = bytes * 10 / MIB
            "${decimas / 10},${decimas % 10} MiB"
        } else {
            "$bytes bytes"
        }

        /** Ruta relativa dentro de [AlmacenExterno]; la misma carpeta que los favoritos. */
        fun nombreArchivo(post: BooruPost, url: String = post.url): String =
            "booru/safebooru-${post.id}.${VisorTransformacion.extensionDe(url)}"

        /**
         * El descargador real: destino en el almacenamiento externo de la app y
         * HTTP con el mismo cliente limitado que usa la búsqueda.
         */
        fun real(
            contexto: Context,
            cliente: OkHttpClient = SafebooruClient.clienteSafebooru(),
        ): DescargasBooru {
            val app = contexto.applicationContext
            return DescargasBooru(
                resolverDestino = { nombre -> AlmacenExterno.archivo(app, nombre) },
                ejecutar = { peticion -> cliente.newCall(peticion).execute() },
            )
        }
    }
}
