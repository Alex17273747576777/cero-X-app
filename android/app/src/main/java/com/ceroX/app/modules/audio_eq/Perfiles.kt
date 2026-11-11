package com.ceroX.app.modules.audio_eq

import kotlin.math.ln

/**
 * Perfiles de ecualización.
 *
 * Cada perfil es una curva de diez puntos en las frecuencias ISO de un
 * ecualizador gráfico (31 Hz a 16 kHz), en decibelios. Los valores no son
 * decorativos: son los desplazamientos que se aplican, y la prueba `EqTest` los
 * recorre uno a uno comprobando que las frecuencias suben y que ningún punto se
 * sale de ±12 dB, que es donde empieza el recorte audible en un móvil.
 *
 * POR QUÉ HAY QUE INTERPOLAR
 * --------------------------
 * Estas curvas tienen diez puntos. Los dispositivos tienen los que su DSP quiere:
 * hay móviles con tres bandas y otros con diez, y los centros casi nunca caen en
 * 31, 62 o 125 Hz. Así que la curva se interpola en escala logarítmica sobre el
 * centro real de cada banda, que es como se mide el sonido: la distancia percibida
 * entre 100 y 200 Hz es la misma que entre 1000 y 2000.
 */
data class Perfil(
    val nombre: String,
    /** Pares (frecuencia en Hz, desplazamiento en dB), en orden ascendente. */
    val puntos: List<Pair<Int, Float>>,
)

object Perfiles {

    /** Las diez frecuencias ISO de un ecualizador gráfico. */
    private val ISO = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    private fun curva(nombre: String, db: List<Float>): Perfil {
        require(db.size == ISO.size) { "$nombre: la curva necesita ${ISO.size} puntos" }
        return Perfil(nombre, ISO.zip(db))
    }

    val todos: List<Perfil> = listOf(
        curva("Plano", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        curva("Graves", listOf(6f, 5f, 3.5f, 1f, 0f, 0f, 0f, 0f, 0f, 0f)),
        curva("Agudos", listOf(0f, 0f, 0f, 0f, 0f, 0.5f, 2f, 4f, 5.5f, 6f)),
        curva("Voz", listOf(-3f, -2f, -1f, 0f, 1.5f, 3f, 3.5f, 2f, 0.5f, -1f)),
        curva("Rock", listOf(4.5f, 3.5f, 2f, 0f, -1f, 0.5f, 2.5f, 3.5f, 4f, 4.5f)),
        curva("Pop", listOf(-1f, 1f, 3f, 3.5f, 2f, 0f, -0.5f, -1f, 1f, 2f)),
        curva("Jazz", listOf(3f, 2f, 1f, 0f, -1f, 0f, 1f, 2f, 2.5f, 3f)),
        curva("Electronica", listOf(5f, 4f, 2f, 0f, -1.5f, -1f, 0.5f, 3f, 4.5f, 5f)),
        curva("Clasica", listOf(2.5f, 2f, 1f, 0f, 0f, 0f, -0.5f, 0.5f, 1.5f, 2.5f)),
        // Recorte de dinámica para escuchar de noche: sube extremos, baja el medio.
        curva("Noche", listOf(2f, 1f, 0f, -0.5f, -1f, -1f, -0.5f, 0f, 0.5f, 1.5f)),
    )

    fun porNombre(nombre: String): Perfil? = todos.firstOrNull { it.nombre == nombre }

    fun nombres(): List<String> = todos.map { it.nombre }

    /**
     * Desplazamiento en dB de una curva a una frecuencia cualquiera.
     *
     * Fuera del rango de la curva devuelve el valor del extremo, no cero: un
     * ecualizador de 10 bandas que sube graves tiene que seguir subiéndolos a
     * 20 Hz, y poner ahí cero crearía un escalón en el borde.
     *
     * Dentro del rango interpola en log(f), que es la escala en la que el oído
     * percibe la distancia entre frecuencias.
     */
    fun interpolar(perfil: Perfil, hz: Int): Float {
        val puntos = perfil.puntos
        if (puntos.isEmpty()) return 0f
        if (hz <= puntos.first().first) return puntos.first().second
        if (hz >= puntos.last().first) return puntos.last().second

        for (i in 0 until puntos.size - 1) {
            val f0 = puntos[i].first
            val f1 = puntos[i + 1].first
            if (hz in f0..f1) {
                val d0 = puntos[i].second
                val d1 = puntos[i + 1].second
                val x = ln(hz.toDouble())
                val x0 = ln(f0.toDouble())
                val x1 = ln(f1.toDouble())
                if (x1 == x0) return d0
                val t = ((x - x0) / (x1 - x0)).toFloat()
                return d0 + (d1 - d0) * t
            }
        }
        return puntos.last().second
    }

    /**
     * La curva de un perfil tal como caería sobre unas bandas concretas.
     *
     * Sirve para enseñar en pantalla lo que se va a aplicar antes de aplicarlo,
     * que es la única forma de que el usuario vea por qué un perfil suena igual en
     * dos móviles con distinto número de bandas.
     */
    fun curvaPara(perfil: Perfil, bandas: List<Banda>): List<Int> =
        bandas.map { kotlin.math.round(interpolar(perfil, it.centroHz) * 100f).toInt() }
}
