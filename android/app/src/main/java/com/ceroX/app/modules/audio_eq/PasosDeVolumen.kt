package com.ceroX.app.modules.audio_eq

import android.media.AudioManager

/**
 * Volumen del sistema y ganancia fina entre paso y paso.
 *
 * QUÉ SE PUEDE HACER SIN ROOT, Y QUÉ NO
 * -------------------------------------
 * Android decide cuántos pasos tiene el volumen de cada flujo y una app no puede
 * cambiarlo: `STREAM_MUSIC` viene con 15 en casi todos los dispositivos y el valor
 * vive en el sistema. Aumentar ese número, que es lo que hacen las soluciones tipo
 * 32steps (MIT), exige modificar el comportamiento del sistema, y cero-X no hace
 * eso: no lleva nada que reescriba el framework.
 *
 * Lo que sí se puede sin tocar el sistema es **ganancia digital fina** entre un
 * paso y otro, con `LoudnessEnhancer`. Cada paso del sistema son unos 300-500 mB
 * según el dispositivo; con 50 mB de resolución se ponen seis u ocho posiciones
 * intermedias donde antes no había ninguna. Este fichero hace las dos cosas y las
 * mantiene separadas, porque una la decide el sistema y la otra cero-X.
 */
class PasosDeVolumen(private val audio: AudioManager) {

    /** Pasos que el sistema tiene para música. Lo decide el fabricante. */
    fun pasosDelSistema(): Int = try {
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    } catch (e: Exception) {
        0
    }

    /** Paso actual. */
    fun pasoActual(): Int = try {
        audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    } catch (e: Exception) {
        -1
    }

    /**
     * Sube o baja el volumen del sistema, y devuelve el paso en el que quedó.
     *
     * `null` si no se pudo: sin paso anterior no hay nada que devolver y no se
     * inventa un número.
     */
    fun moverPasos(delta: Int): Int? {
        val actual = pasoActual()
        if (actual < 0) return null
        val maximo = pasosDelSistema()
        if (maximo <= 0) return null
        val destino = (actual + delta).coerceIn(0, maximo)
        return try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, destino, 0)
            destino
        } catch (e: Exception) {
            // Android 11+ lanza SecurityException si el volumen está controlado por
            // otro dispositivo (unos auriculares Bluetooth con AVRCP absoluto).
            null
        }
    }

    /**
     * Cuántos pasos finos caben entre dos pasos del sistema, dada una resolución.
     *
     * Es una cuenta, no una promesa: dice lo que se puede pedir a
     * [Ecualizador.fijarGananciaFina] entre un paso y el siguiente.
     */
    fun pasosFinosEntrePasos(resolucionMb: Int = 50, recorridoMb: Int = 400): Int {
        if (resolucionMb <= 0) return 0
        return recorridoMb / resolucionMb
    }

    /**
     * El estado completo, para enseñarlo tal cual es.
     *
     * `gananciaFinaMb` viene del ecualizador, no de aquí: el volumen del sistema y
     * la ganancia digital son dos cosas distintas y se muestran por separado, porque
     * sumarlas en un número daría la impresión de que cero-X controla el volumen
     * con más resolución de la que tiene.
     */
    fun estado(gananciaFinaMb: Int?): EstadoVolumen = EstadoVolumen(
        pasoActual = pasoActual(),
        pasosDelSistema = pasosDelSistema(),
        gananciaFinaMb = gananciaFinaMb,
        pasosFinos = pasosFinosEntrePasos(),
    )
}

data class EstadoVolumen(
    val pasoActual: Int,
    val pasosDelSistema: Int,
    val gananciaFinaMb: Int?,
    val pasosFinos: Int,
)
