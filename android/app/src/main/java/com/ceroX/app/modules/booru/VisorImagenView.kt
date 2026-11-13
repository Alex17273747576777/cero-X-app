package com.ceroX.app.modules.booru

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * `ImageView` con matriz propia: pellizco, doble toque y arrastre.
 *
 * Sin bibliotecas de terceros ni `PhotoView`: los gestos sólo alimentan a
 * [VisorTransformacion], que es donde están las matemáticas y sus límites. Esta
 * clase no decide nada sobre cuánto se puede ampliar.
 *
 * Los botones de la pantalla llaman a [ajustar], [unoAUno], [ampliar] y
 * [reducir], que son exactamente las mismas operaciones que hacen los gestos.
 * Por eso el visor se puede probar sin lanzar `MotionEvent` sintéticos: no hay
 * un camino para el dedo y otro para la accesibilidad.
 */
class VisorImagenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    /** Estado visible; sólo se cambia por [establecer] o por un gesto. */
    var estado: VisorTransformacion = VisorTransformacion(0, 0, 0, 0)
        private set

    /** Para que la pantalla pinte la escala en cuanto cambia. */
    var alCambiar: ((VisorTransformacion) -> Unit)? = null

    private val matriz = Matrix()
    private var pellizcando = false

    private val detectorEscala = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                pellizcando = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                publicar(estado.pellizco(detector.scaleFactor, detector.focusX, detector.focusY))
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                pellizcando = false
            }
        },
    )

    private val detectorGestos = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                publicar(estado.alternarDobleToque(e.x, e.y))
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (pellizcando || !estado.ampliada) return false
                publicar(estado.desplazar(-distanceX, -distanceY))
                return true
            }
        },
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    /**
     * Fija las dimensiones de la imagen y del hueco disponible, y ajusta.
     *
     * El hueco se pasa explícitamente porque antes del primer `layout` el `width`
     * de la vista vale 0 y con 0 no hay ajuste posible.
     */
    fun establecer(anchoImagen: Int, altoImagen: Int, anchoVista: Int, altoVista: Int) {
        publicar(
            VisorTransformacion(anchoImagen, altoImagen, anchoVista, altoVista).ajustar(),
        )
    }

    fun ajustar() = publicar(estado.ajustar())

    fun unoAUno() = publicar(estado.unoAUno())

    fun ampliar() = publicar(
        estado.conEscala(estado.escala * PASO_BOTON, estado.anchoVista / 2f, estado.altoVista / 2f),
    )

    fun reducir() = publicar(
        estado.conEscala(estado.escala / PASO_BOTON, estado.anchoVista / 2f, estado.altoVista / 2f),
    )

    /**
     * Recupera una escala guardada tras un cambio de configuración.
     *
     * Se recupera la escala, no el encuadre exacto: al rotar cambia el hueco y
     * volver al centro es lo que espera quien gira el aparato. Los límites se
     * recalculan igual que con un gesto.
     */
    fun restaurarEscala(
        escala: Float,
        focoX: Float = estado.anchoVista / 2f,
        focoY: Float = estado.altoVista / 2f,
    ) = publicar(estado.conEscala(escala, focoX, focoY))

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        if (w != estado.anchoVista || h != estado.altoVista) publicar(estado.conVista(w, h))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!estado.util) return super.onTouchEvent(event)
        detectorEscala.onTouchEvent(event)
        detectorGestos.onTouchEvent(event)
        // Con la imagen ampliada, el padre (lista o deslizador) no debe robar el gesto.
        parent?.requestDisallowInterceptTouchEvent(
            event.pointerCount > 1 || pellizcando || estado.ampliada,
        )
        return true
    }

    private fun publicar(nuevo: VisorTransformacion) {
        estado = nuevo
        matriz.reset()
        if (nuevo.lista) {
            matriz.setScale(nuevo.escala, nuevo.escala)
            matriz.postTranslate(nuevo.dx, nuevo.dy)
        }
        imageMatrix = matriz
        alCambiar?.invoke(nuevo)
    }

    private companion object {
        const val PASO_BOTON = 1.4f
    }
}
