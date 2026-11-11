package com.ceroX.app.modules.audio_eq

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer

/**
 * Los efectos de audio de Android, envueltos.
 *
 * QUÉ ES ESTO
 * -----------
 * `android.media.audiofx` trae el ecualizador, el realce de graves, el
 * envolvente y el realce de sonoridad. Son efectos del sistema: se adjuntan a una
 * sesión de audio y el DSP hace el trabajo. No hay aquí ningún proceso propio ni
 * ninguna librería de terceros —la base elegida, RootlessViPER4Android (GPL-3.0),
 * hace lo mismo con su propio motor en C, y este envoltorio es el camino que no
 * necesita compilar nada nativo—.
 *
 * LA REGLA DE ESTE FICHERO
 * ------------------------
 * Adjuntar efectos falla a menudo y por motivos que no controlamos: dispositivos
 * sin DSP, sin permiso, con el número de sesiones agotado, o con un fabricante que
 * no implementa el efecto. Cada constructor lanza `RuntimeException` o
 * `UnsupportedOperationException` en esos casos. Así que nada de esto devuelve
 * éxito sin haber construido el objeto, y todo lo que puede fallar devuelve el
 * motivo en un `String?`: `null` significa que salió, cualquier otra cosa es lo
 * que pasó.
 *
 * SOBRE LA SESIÓN 0
 * -----------------
 * `audioSession = 0` es la mezcla de salida: afecta a todo lo que suena en el
 * dispositivo, que es lo que hace un ecualizador independiente. Hace falta el
 * permiso `MODIFY_AUDIO_SETTINGS` (normal, sin diálogo). Cualquier otro id es un
 * reproductor concreto, y es lo que usa cero-X cuando hay audio propio sonando.
 */
class Ecualizador {

    private var eq: Equalizer? = null
    private var bajo: BassBoost? = null
    private var envolvente: Virtualizer? = null
    private var ganancia: LoudnessEnhancer? = null
    private var sesionAdjunta: Int? = null

    /**
     * Adjunta los cuatro efectos a una sesión.
     *
     * Si el ecualizador no se puede crear, el resultado no está activo y lleva el
     * motivo: un ecualizador sin bandas no sirve para nada. Los otros tres son
     * opcionales —muchos dispositivos no tienen envolvente— y su ausencia se
     * anota en `motivo` sin impedir que el ecualizador funcione.
     */
    fun adjuntar(audioSession: Int): Adjunto {
        liberar()

        val fallos = mutableListOf<String>()

        eq = try {
            Equalizer(0, audioSession).apply { enabled = true }
        } catch (e: Exception) {
            fallos.add("Equalizer: " + (e.message ?: e.javaClass.simpleName))
            null
        }

        val e = eq
        if (e == null) {
            return Adjunto(
                sesion = null,
                bandas = emptyList(),
                presetsDelSistema = emptyList(),
                gravesSoportados = false,
                envolventeSoportada = false,
                gananciaFinaDisponible = false,
                motivo = "No se pudo adjuntar el ecualizador a la sesion $audioSession. " + fallos.joinToString("; "),
            )
        }

        bajo = try {
            BassBoost(0, audioSession).apply { enabled = true }
        } catch (ex: Exception) {
            fallos.add("BassBoost: " + (ex.message ?: ex.javaClass.simpleName))
            null
        }

        envolvente = try {
            Virtualizer(0, audioSession).apply { enabled = true }
        } catch (ex: Exception) {
            fallos.add("Virtualizer: " + (ex.message ?: ex.javaClass.simpleName))
            null
        }

        ganancia = try {
            LoudnessEnhancer(audioSession).apply { enabled = true }
        } catch (ex: Exception) {
            fallos.add("LoudnessEnhancer: " + (ex.message ?: ex.javaClass.simpleName))
            null
        }

        sesionAdjunta = audioSession

        val bandas = leerBandas(e)
        val presets = try {
            (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
        } catch (ex: Exception) {
            fallos.add("presets: " + (ex.message ?: ex.javaClass.simpleName))
            emptyList()
        }

        return Adjunto(
            sesion = audioSession,
            bandas = bandas,
            presetsDelSistema = presets,
            gravesSoportados = bajo?.strengthSupported == true,
            envolventeSoportada = envolvente?.strengthSupported == true,
            gananciaFinaDisponible = ganancia != null,
            motivo = if (fallos.isEmpty()) null else fallos.joinToString("; "),
        )
    }

    /** Lee del dispositivo las bandas que tiene, con su rango real de nivel. */
    private fun leerBandas(e: Equalizer): List<Banda> {
        return try {
            val rango = e.bandLevelRange
            val minMb = rango[0].toInt()
            val maxMb = rango[1].toInt()
            (0 until e.numberOfBands.toInt()).map { i ->
                val banda = i.toShort()
                val rangoBanda = e.getBandFreqRange(banda)
                Banda(
                    indice = i,
                    centroHz = e.getCenterFreq(banda) / 1000,
                    desdeHz = rangoBanda[0] / 1000,
                    hastaHz = rangoBanda[1] / 1000,
                    nivelMb = e.getBandLevel(banda).toInt(),
                    minMb = minMb,
                    maxMb = maxMb,
                )
            }
        } catch (ex: Exception) {
            emptyList()
        }
    }

    /** Bandas actuales, leídas del DSP en cada llamada: no se guardan en caché. */
    fun bandas(): List<Banda> {
        val e = eq ?: return emptyList()
        return leerBandas(e)
    }

    /**
     * Fija el nivel de una banda en milibelios.
     *
     * Devuelve `null` si se fijó. El valor se recorta al rango que el propio
     * dispositivo declara para esa banda, porque cada DSP tiene el suyo y pasarse
     * lanza.
     */
    fun ponerNivel(indice: Int, mb: Int): String? {
        val e = eq ?: return "No hay ningun ecualizador adjunto"
        val total = try {
            e.numberOfBands.toInt()
        } catch (ex: Exception) {
            return "No se pudo leer el numero de bandas: " + (ex.message ?: ex.javaClass.simpleName)
        }
        if (indice !in 0 until total) {
            return "La banda $indice no existe: este dispositivo tiene $total"
        }
        return try {
            val rango = e.bandLevelRange
            val recortado = mb.coerceIn(rango[0].toInt(), rango[1].toInt())
            e.setBandLevel(indice.toShort(), recortado.toShort())
            null
        } catch (ex: Exception) {
            "No se pudo fijar la banda $indice a $mb mB: " + (ex.message ?: ex.javaClass.simpleName)
        }
    }

    /**
     * Aplica una curva de perfil banda por banda.
     *
     * Los perfiles están definidos en diez frecuencias ISO, y los dispositivos
     * tienen entre 3 y 10 bandas en otras frecuencias, así que la curva se
     * interpola en escala logarítmica sobre el centro real de cada banda. No se
     * asume que el dispositivo tenga las bandas del perfil.
     *
     * Devuelve `null` si se aplicaron todas, o el primer motivo de fallo.
     */
    fun aplicarPerfil(perfil: Perfil): String? {
        val bandas = bandas()
        if (bandas.isEmpty()) return "No hay bandas que ajustar: el ecualizador no esta adjunto"
        for (banda in bandas) {
            val db = Perfiles.interpolar(perfil, banda.centroHz)
            val mb = kotlin.math.round(db * 100f).toInt()
            val fallo = ponerNivel(banda.indice, mb.coerceIn(banda.minMb, banda.maxMb))
            if (fallo != null) return fallo
        }
        return null
    }

    /** Pone todas las bandas a cero. Es lo que hace un ecualizador plano. */
    fun ponerPlano(): String? {
        val bandas = bandas()
        if (bandas.isEmpty()) return "No hay bandas que ajustar: el ecualizador no esta adjunto"
        for (banda in bandas) {
            val fallo = ponerNivel(banda.indice, 0)
            if (fallo != null) return fallo
        }
        return null
    }

    /** Realce de graves, de 0 a 1000, que es la escala de `BassBoost`. */
    fun fijarGraves(fuerza: Int): String? {
        val b = bajo ?: return "Este dispositivo no dio BassBoost al adjuntar los efectos"
        if (b.strengthSupported != true) return "BassBoost existe pero no admite cambiar la fuerza"
        return try {
            b.setStrength(fuerza.coerceIn(0, 1000).toShort())
            null
        } catch (e: Exception) {
            "No se pudo fijar la fuerza de graves a $fuerza: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    /** Envolvente (amplitud estéreo), de 0 a 1000. */
    fun fijarEnvolvente(fuerza: Int): String? {
        val v = envolvente ?: return "Este dispositivo no dio Virtualizer al adjuntar los efectos"
        if (v.strengthSupported != true) return "Virtualizer existe pero no admite cambiar la fuerza"
        return try {
            v.setStrength(fuerza.coerceIn(0, 1000).toShort())
            null
        } catch (e: Exception) {
            "No se pudo fijar la envolvente a $fuerza: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Ganancia fina en milibelios, de -1500 a +1500.
     *
     * Es el ajuste que no cabe entre dos pasos del volumen del sistema: 30 dB de
     * recorrido en pasos de 50 mB son 60 posiciones donde el sistema sólo tiene una.
     */
    fun fijarGananciaFina(mb: Int): String? {
        val g = ganancia ?: return "Este dispositivo no dio LoudnessEnhancer al adjuntar los efectos"
        return try {
            g.setTargetGain(mb.coerceIn(-1500, 1500))
            null
        } catch (e: Exception) {
            "No se pudo fijar la ganancia a $mb mB: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    /** Ganancia fina actual, o null si no hay realce de sonoridad. */
    fun gananciaFinaActual(): Int? = try {
        ganancia?.targetGain
    } catch (e: Exception) {
        null
    }

    /** Activa o desactiva el ecualizador sin soltar los efectos. */
    fun activarEcualizador(activo: Boolean): String? {
        val e = eq ?: return "No hay ningun ecualizador adjunto"
        return try {
            e.enabled = activo
            null
        } catch (ex: Exception) {
            "No se pudo " + (if (activo) "activar" else "desactivar") + " el ecualizador: " +
                (ex.message ?: ex.javaClass.simpleName)
        }
    }

    fun sesion(): Int? = sesionAdjunta

    /** Suelta los cuatro efectos. Se llama al parar el módulo y al readjuntar. */
    fun liberar() {
        for (efecto in listOfNotNull(eq, bajo, envolvente, ganancia)) {
            try {
                efecto.enabled = false
                efecto.release()
            } catch (e: Exception) {
                // Soltar un efecto que ya no existe lanza; no hay nada que hacer.
            }
        }
        eq = null
        bajo = null
        envolvente = null
        ganancia = null
        sesionAdjunta = null
    }
}

/** Una banda del ecualizador, leída del dispositivo. */
data class Banda(
    val indice: Int,
    val centroHz: Int,
    val desdeHz: Int,
    val hastaHz: Int,
    val nivelMb: Int,
    val minMb: Int,
    val maxMb: Int,
)

/**
 * Lo que quedó adjunto, y lo que no.
 *
 * `motivo` lleva los fallos de los efectos que no se pudieron crear. Que esté
 * lleno no significa que no haya nada útil: un dispositivo sin envolvente sigue
 * teniendo ecualizador, y eso se ve en `activo` y en `bandas`.
 */
data class Adjunto(
    val sesion: Int?,
    val bandas: List<Banda>,
    val presetsDelSistema: List<String>,
    val gravesSoportados: Boolean,
    val envolventeSoportada: Boolean,
    val gananciaFinaDisponible: Boolean,
    val motivo: String?,
) {
    val activo: Boolean get() = sesion != null && bandas.isNotEmpty()
}
