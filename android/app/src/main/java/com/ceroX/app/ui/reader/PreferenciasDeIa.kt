package com.ceroX.app.ui.reader

import android.content.Context
import com.ceroX.app.core.Prefs
import com.ceroX.app.modules.reader.ReaderPipeline

/**
 * Lo que el lector recuerda sobre los modelos: si están encendidos, y qué pasó.
 *
 * POR QUÉ NO SE GUARDA EN `últimoResultado`
 * -----------------------------------------
 * El motivo del último fallo se guarda en la misma preferencia que el interruptor,
 * pero **no se borra al apagar el modelo**: si alguien enciende la colorización, ve
 * que falla, la apaga y vuelve mañana, necesita seguir sabiendo por qué falló. Un
 * interruptor apagado que oculta el error de ayer obliga a repetir el experimento.
 *
 * POR QUÉ POR DEFECTO ESTÁ APAGADO
 * --------------------------------
 * Medido con los modelos del APK en este contenedor: colorear una página de
 * 256x256 cuesta ~1 490 ms y reescalar ×4 entre 2 673 ms (anime) y 24 587 ms
 * (general). Encender eso sin que nadie lo pida convierte un lector en una estufa.
 * Apagado por defecto, y el coste escrito al lado para que la decisión sea informada.
 */
class PreferenciasDeIa(context: Context) {

    private val prefs = Prefs(context)

    companion object {
        const val CLAVE_COLORIZAR = "ia.colorizar"
        const val CLAVE_REESCALAR = "ia.reescalar"
        const val CLAVE_MOTIVO = "ia.ultimo_motivo"
        const val CLAVE_MS = "ia.ultimo_ms"
    }

    var colorizar: Boolean
        get() = prefs.getBoolean(CLAVE_COLORIZAR, false)
        set(v) = prefs.putBoolean(CLAVE_COLORIZAR, v)

    var reescalar: Boolean
        get() = prefs.getBoolean(CLAVE_REESCALAR, false)
        set(v) = prefs.putBoolean(CLAVE_REESCALAR, v)

    /** Qué dijo el motor la última vez. Vacío si nunca se encendió. */
    var ultimoMotivo: String
        get() = prefs.getString(CLAVE_MOTIVO, "") ?: ""
        set(v) = prefs.putString(CLAVE_MOTIVO, v)

    /** Cuánto tardó la última página procesada. 0 si nunca se procesó ninguna. */
    var ultimosMs: Long
        get() = prefs.getLong(CLAVE_MS, 0L)
        set(v) = prefs.putLong(CLAVE_MS, v)

    /** Los ajustes que entiende el pipeline, armados desde lo guardado. */
    fun ajustes() = ReaderPipeline.Ajustes(
        colorizar = colorizar,
        reescalar = reescalar,
    )

    /** Deja constancia de lo que hizo el pipeline con una página. */
    fun anotar(resultado: ReaderPipeline.Resultado) {
        ultimosMs = resultado.ms
        ultimoMotivo = when {
            resultado.fallido.isNotEmpty() -> resultado.fallido.joinToString("; ")
            resultado.aplicado.isNotEmpty() ->
                "${resultado.aplicado.joinToString(" + ")} en ${resultado.ms} ms"

            else -> "nada que aplicar"
        }
    }

    /** Texto para la pantalla: qué está encendido y cuánto cuesta. */
    fun resumen(): String {
        val encendidos = buildList {
            if (colorizar) add("colorizar")
            if (reescalar) add("reescalar ×4")
        }
        if (encendidos.isEmpty()) return "Apagado. El lector pinta la página tal cual llega."
        val coste = when {
            colorizar && reescalar -> "~1 500 ms + 2 700 a 24 600 ms por página"
            colorizar -> "~1 500 ms por página"
            else -> "2 700 a 24 600 ms por página"
        }
        return "${encendidos.joinToString(" + ")} · $coste"
    }
}
