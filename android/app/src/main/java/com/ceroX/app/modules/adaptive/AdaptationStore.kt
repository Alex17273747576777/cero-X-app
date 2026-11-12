package com.ceroX.app.modules.adaptive

import android.content.Context
import com.ceroX.app.core.AlmacenExterno
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable private data class DatosAdaptacion(
    val version: Int = 1,
    val habilitado: Boolean = true,
    val eventos: List<MotorAdaptativo.Evento> = emptyList(),
)

/** Persistencia externa, acotada y sin texto de búsqueda, URLs ni credenciales. */
class AdaptationStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val archivo: File? = AlmacenExterno.directorio(context)?.let { File(it, "adaptacion/eventos.json") }
    private var datos: DatosAdaptacion = leer()

    @Synchronized fun habilitado(): Boolean = datos.habilitado
    @Synchronized fun disponible(): Boolean = archivo != null
    @Synchronized fun eventos(): List<MotorAdaptativo.Evento> = datos.eventos

    @Synchronized fun establecerHabilitado(valor: Boolean) {
        datos = datos.copy(habilitado = valor)
        guardar()
    }

    @Synchronized fun registrar(
        sesion: String, tipo: MotorAdaptativo.TipoEvento, categoria: String, origen: String,
        instante: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!datos.habilitado || sesion.isBlank()) return false
        val evento = MotorAdaptativo.Evento(sesion.take(80), tipo, categoria.take(60), origen.take(80), instante)
        datos = datos.copy(eventos = (datos.eventos + evento).takeLast(MAX_EVENTOS))
        guardar()
        return true
    }

    @Synchronized fun deshacerUltimo(sesion: String): Boolean {
        val indice = datos.eventos.indexOfLast { it.sesion == sesion }
        if (indice < 0) return false
        datos = datos.copy(eventos = datos.eventos.filterIndexed { i, _ -> i != indice })
        guardar()
        return true
    }

    @Synchronized fun restablecer() {
        datos = datos.copy(eventos = emptyList())
        guardar()
    }

    private fun leer(): DatosAdaptacion = runCatching {
        val f = archivo ?: return DatosAdaptacion()
        if (!f.isFile || f.length() > MAX_BYTES) return DatosAdaptacion()
        json.decodeFromString(DatosAdaptacion.serializer(), f.readText())
    }.getOrDefault(DatosAdaptacion())

    private fun guardar() {
        val destino = archivo ?: return
        destino.parentFile?.mkdirs()
        val temporal = File(destino.parentFile, destino.name + ".tmp")
        runCatching {
            temporal.writeText(json.encodeToString(DatosAdaptacion.serializer(), datos))
            if (!temporal.renameTo(destino)) {
                destino.writeText(temporal.readText())
                temporal.delete()
            }
        }
    }

    companion object {
        const val MAX_EVENTOS = 400
        private const val MAX_BYTES = 2L * 1024 * 1024
    }
}
