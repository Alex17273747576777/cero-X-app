package com.ceroX.app.modules.booru

import java.net.URI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Matemáticas del visor: cuánto se amplía la imagen y hasta dónde se puede mover.
 *
 * POR QUÉ ESTE ARCHIVO NO LLEVA ANDROID
 * -------------------------------------
 * Todo lo que decide la escala y los límites vive aquí, sin `Matrix`, sin
 * `MotionEvent` y sin `View`. Así se prueba en la JVM con números exactos: un
 * gesto mal limitado se ve en una prueba, no hace falta un aparato.
 * [VisorImagenView] sólo traduce gestos a estas funciones y pinta el resultado.
 *
 * EL MODELO
 * ---------
 * Un punto de la imagen `(ix, iy)` cae en la pantalla en
 * `(ix * escala + dx, iy * escala + dy)`. Con eso, ampliar sin que la imagen se
 * escape del dedo es despejar `ix` antes de cambiar la escala y volver a
 * colocarlo en el mismo sitio de la vista.
 *
 * LOS LÍMITES, QUE SON LA PARTE FÁCIL DE ROMPER
 * ---------------------------------------------
 *   · El mínimo es el ajuste a pantalla (la imagen entera visible). Si la imagen
 *     es más pequeña que la vista, el mínimo es 1:1: no se inventa un aumento
 *     para rellenar.
 *   · El máximo es [ZOOM_MAXIMO_RELATIVO] veces el ajuste, para que una
 *     miniatura no acabe siendo un bloque de píxeles sin fin.
 *   · Si la imagen ampliada es más ancha que la vista, `dx` queda entre
 *     `anchoVista - anchoImagen * escala` y `0`: no se puede arrastrar fuera y
 *     dejar un hueco negro. Si cabe entera, va centrada. Ídem en vertical.
 */
data class VisorTransformacion(
    val anchoImagen: Int,
    val altoImagen: Int,
    val anchoVista: Int,
    val altoVista: Int,
    val escala: Float = SIN_AJUSTAR,
    val dx: Float = 0f,
    val dy: Float = 0f,
) {

    /** Hay imagen y hay vista: se puede calcular algo. */
    val util: Boolean
        get() = anchoImagen > 0 && altoImagen > 0 && anchoVista > 0 && altoVista > 0

    /** Ya se aplicó un ajuste; sin esto la escala vale 0 y no se pinta nada. */
    val lista: Boolean
        get() = util && escala > 0f

    val escalaAjuste: Float
        get() = if (!util) 1f else min(
            anchoVista / anchoImagen.toFloat(),
            altoVista / altoImagen.toFloat(),
        )

    val escalaMinima: Float
        get() = min(escalaAjuste, 1f)

    val escalaMaxima: Float
        get() = max(escalaAjuste * ZOOM_MAXIMO_RELATIVO, 1f)

    val ajustada: Boolean
        get() = lista && abs(escala - escalaAjuste) <= TOLERANCIA

    val ampliada: Boolean
        get() = lista && escala > escalaAjuste + TOLERANCIA

    val puedeAmpliar: Boolean
        get() = lista && escala < escalaMaxima - TOLERANCIA

    val puedeReducir: Boolean
        get() = lista && escala > escalaMinima + TOLERANCIA

    /** Imagen entera visible y centrada. Es el estado inicial y el del botón «Ajustar». */
    fun ajustar(): VisorTransformacion {
        if (!util) return this
        val s = escalaAjuste
        return copy(
            escala = s,
            dx = (anchoVista - anchoImagen * s) / 2f,
            dy = (altoVista - altoImagen * s) / 2f,
        )
    }

    /** Píxeles reales: un píxel de la imagen, un píxel de la pantalla. */
    fun unoAUno(): VisorTransformacion =
        conEscala(1f, anchoVista / 2f, altoVista / 2f)

    /**
     * Doble toque: si ya está ampliada vuelve al ajuste; si no, amplía
     * [ZOOM_DOBLE_TOQUE] veces sobre el punto tocado.
     */
    fun alternarDobleToque(focoX: Float, focoY: Float): VisorTransformacion {
        if (!util) return this
        if (ampliada) return ajustar()
        return conEscala(escalaAjuste * ZOOM_DOBLE_TOQUE, focoX, focoY)
    }

    /** Pellizco: multiplica la escala manteniendo el foco donde estaba. */
    fun pellizco(factor: Float, focoX: Float, focoY: Float): VisorTransformacion {
        if (!lista || factor <= 0f) return this
        return conEscala(escala * factor, focoX, focoY)
    }

    /** Arrastre. El signo ya viene invertido por el gesto: mover el dedo mueve la imagen. */
    fun desplazar(deltaX: Float, deltaY: Float): VisorTransformacion {
        if (!lista) return this
        return limitar(copy(dx = dx + deltaX, dy = dy + deltaY))
    }

    /**
     * Cambia la escala a [nueva], limitada al rango válido, y deja el punto de la
     * imagen que estaba bajo `(focoX, focoY)` exactamente ahí.
     */
    fun conEscala(nueva: Float, focoX: Float, focoY: Float): VisorTransformacion {
        if (!util || nueva <= 0f) return this
        val base = if (lista) this else ajustar()
        val objetivo = nueva.coerceIn(base.escalaMinima, base.escalaMaxima)
        val ix = (focoX - base.dx) / base.escala
        val iy = (focoY - base.dy) / base.escala
        return limitar(
            copy(
                escala = objetivo,
                dx = focoX - ix * objetivo,
                dy = focoY - iy * objetivo,
            ),
        )
    }

    /** Nuevo tamaño de vista (rotación, ventana dividida): misma escala, recolocada. */
    fun conVista(nuevoAncho: Int, nuevoAlto: Int): VisorTransformacion {
        val cambiada = copy(anchoVista = nuevoAncho, altoVista = nuevoAlto)
        if (!cambiada.util) return cambiada
        return if (cambiada.lista) {
            cambiada.conEscala(cambiada.escala, nuevoAncho / 2f, nuevoAlto / 2f)
        } else {
            cambiada.ajustar()
        }
    }

    /** Qué punto de la imagen está bajo este punto de la vista. */
    fun puntoDeImagen(vistaX: Float, vistaY: Float): Pair<Float, Float>? {
        if (!lista) return null
        return ((vistaX - dx) / escala) to ((vistaY - dy) / escala)
    }

    /** Escala legible con coma decimal, sin depender del idioma del aparato. */
    val textoEscala: String
        get() {
            if (!lista) return "0,00×"
            val centesimas = (escala * 100).roundToInt()
            return "${centesimas / 100},${(centesimas % 100).toString().padStart(2, '0')}×"
        }

    /** Estado de una línea para la pantalla. No dice «listo» si no lo está. */
    fun resumen(): String = when {
        !util -> "Sin dimensiones de imagen o de vista: no se puede calcular el ajuste"
        !lista -> "Imagen de ${anchoImagen}×${altoImagen} declarada, esperando el primer ajuste"
        ampliada -> "$textoEscala · ampliada: arrastra para recorrerla"
        else -> "$textoEscala · ajustada a la pantalla"
    }

    private fun limitar(candidata: VisorTransformacion): VisorTransformacion {
        if (!candidata.lista) return candidata
        val ancho = candidata.anchoImagen * candidata.escala
        val alto = candidata.altoImagen * candidata.escala
        val dxFinal = if (ancho <= candidata.anchoVista) {
            (candidata.anchoVista - ancho) / 2f
        } else {
            candidata.dx.coerceIn(candidata.anchoVista - ancho, 0f)
        }
        val dyFinal = if (alto <= candidata.altoVista) {
            (candidata.altoVista - alto) / 2f
        } else {
            candidata.dy.coerceIn(candidata.altoVista - alto, 0f)
        }
        return candidata.copy(dx = dxFinal, dy = dyFinal)
    }

    companion object {
        /** Escala sin calcular todavía; el visor la ajusta en cuanto sabe el tamaño. */
        const val SIN_AJUSTAR = 0f
        const val ZOOM_MAXIMO_RELATIVO = 8f
        const val ZOOM_DOBLE_TOQUE = 2.5f
        private const val TOLERANCIA = 0.001f

        /**
         * Dimensiones a partir del texto que ya trae la API (`"1200×800"`).
         * Devuelve null si el texto no son dos números positivos: el visor lo
         * dice y espera al drawable real en vez de inventarse un tamaño.
         */
        fun dimensiones(texto: String): Pair<Int, Int>? {
            val partes = texto.split('×', 'x', 'X').map { it.trim() }
            if (partes.size != 2) return null
            val ancho = partes[0].toIntOrNull() ?: return null
            val alto = partes[1].toIntOrNull() ?: return null
            return if (ancho > 0 && alto > 0) ancho to alto else null
        }

        /** Extensión segura para guardar, tomada de la URL y limitada a imágenes. */
        fun extensionDe(url: String): String {
            val ruta = runCatching { URI(url).path }.getOrNull().orEmpty()
            val candidata = ruta.substringAfterLast('.', "").lowercase()
            return if (candidata in setOf("jpg", "jpeg", "png", "gif", "webp", "avif")) candidata else "jpg"
        }
    }
}
