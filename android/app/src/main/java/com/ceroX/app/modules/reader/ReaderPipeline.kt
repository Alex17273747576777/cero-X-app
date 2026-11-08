package com.ceroX.app.modules.reader

import android.content.Context
import com.ceroX.app.modules.ai.ModelAssets
import com.ceroX.app.modules.kisara.KisaraColorizer
import com.ceroX.app.modules.kisara.KisaraUpscaler

/**
 * El eslabón que faltaba entre los motores de IA y la pantalla del lector.
 *
 * EL HUECO QUE TAPA
 * ----------------
 * Los motores estaban y funcionaban: `colorize.onnx` coloreaba en 1 490 ms y
 * `anime4x.onnx` reescalaba en 2 673 ms, medidos. Pero no había **ninguna** pieza
 * que uniera «aquí tengo la página que el usuario está mirando» con «aquí tengo
 * un modelo que puede mejorarla». Se podía llamar a los motores desde una prueba
 * y desde ningún sitio más. En el README eso se llamaba «motor listo», y era
 * exacto.
 *
 * QUÉ HACE Y QUÉ NO
 * -----------------
 * Hace: dado un bitmap de página y unos ajustes, devuelve el bitmap que hay que
 * pintar, aplicando colorización y/o reescalado, con el resultado medido.
 *
 * No hace: descargar páginas ni pintar en pantalla. Eso es del lector. Esta clase
 * no sabe de red ni de vistas, y por eso se puede probar sin emulador.
 *
 * LAS TRES REGLAS QUE LA HACEN USABLE
 * -----------------------------------
 * 1. **Nunca lanza.** Un modelo que falla en mitad de un capítulo no puede
 *    cerrar la app. Devuelve la página original y dice qué pasó en `motivo`.
 *    El usuario sigue leyendo en blanco y negro; eso es feo, no es un cierre.
 *
 * 2. **La IA es opcional y se apaga.** Colorizar 1 490 ms por página es caro y
 *    la mayoría de la gente lee manga en blanco y negro porque así se dibujó.
 *    Si el ajuste está apagado no se toca ni un píxel: coste cero.
 *
 * 3. **El trabajo va fuera del hilo de interfaz.** `procesar` es bloqueante a
 *    propósito, para que el llamador tenga que decidir dónde lo pone. Si se
 *    llamara desde el hilo principal, 1 490 ms son un ANR.
 */
/**
 * Lo que el pipeline necesita de un colorizador. No necesita saber que existe
 * ONNX: solo que hay algo que recibe una imagen y devuelve otra, o un motivo.
 *
 * Se declara como interfaz por dos razones, y ninguna es "para poder probarlo":
 *   1. se puede cambiar el motor (otro modelo, otra librería) sin tocar el lector
 *   2. las clases de Kisara son `final` porque tienen estado nativo que no debe
 *      heredarse; abrirlas solo para las pruebas sería debilitar la producción
 */
interface ColorizadorDePagina {
    fun colorear(original: android.graphics.Bitmap, maxLado: Int): KisaraColorizer.Resultado
}

/** Lo mismo para el reescalado. */
interface EscaladorDePagina {
    fun reescalar(original: android.graphics.Bitmap, maxLado: Int): KisaraUpscaler.Resultado
}

class ReaderPipeline(
    context: Context,
    private val colorizador: ColorizadorDePagina = ColorizadorOnnx(context),
    private val reescalador: EscaladorDePagina = EscaladorOnnx(context),
) {

    /** Adaptador del motor real de colorización. */
    class ColorizadorOnnx(context: Context) : ColorizadorDePagina {
        private val motor = KisaraColorizer(ModelAssets(context))
        override fun colorear(original: android.graphics.Bitmap, maxLado: Int) =
            motor.colorear(original, maxLado)
    }

    /** Adaptador del motor real de reescalado. */
    class EscaladorOnnx(context: Context) : EscaladorDePagina {
        private val motor = KisaraUpscaler(ModelAssets(context))
        override fun reescalar(original: android.graphics.Bitmap, maxLado: Int) =
            motor.reescalar(original, maxLado)
    }

    /** Qué aplicar a cada página. */
    data class Ajustes(
        /** Colorear páginas en blanco y negro. Caro: ~1 490 ms por página. */
        val colorizar: Boolean = false,
        /** Reescalar ×4. Caro: 2 673 ms (anime) a 24 587 ms (general). */
        val reescalar: Boolean = false,
        /**
         * Lado máximo de entrada a la colorización. 256 es lo que se midió.
         * Subirlo multiplica el tiempo por el cuadrado del factor.
         */
        val ladoColorizacion: Int = 256,
        /** Lado máximo de entrada al reescalado. */
        val ladoReescalado: Int = 512,
    )

    /** Lo que se pintó, y por qué. */
    data class Resultado(
        /** Bitmap a pintar. Nunca null: si algo falla, es el original. */
        val pagina: android.graphics.Bitmap,
        val aplicado: List<String>,
        val fallido: List<String>,
        val ms: Long,
    ) {
        val huboCambios: Boolean get() = aplicado.isNotEmpty()
    }

    /**
     * Aplica los ajustes a una página.
     *
     * El orden importa: primero se reescala y después se colorea.
     * Al revés, la colorización trabajaría sobre una imagen a resolución completa
     * (minutos) y luego el reescalado tiraría casi todo ese color al reducir.
     * Reescalar primero es más rápido y el color de baja frecuencia se conserva.
     */
    fun procesar(
        original: android.graphics.Bitmap,
        ajustes: Ajustes = Ajustes(),
    ): Resultado {
        val t0 = System.currentTimeMillis()
        val aplicado = mutableListOf<String>()
        val fallido = mutableListOf<String>()

        // Atajo importante: sin IA encendida no se copia ni se toca nada.
        if (!ajustes.colorizar && !ajustes.reescalar) {
            return Resultado(original, aplicado, fallido, System.currentTimeMillis() - t0)
        }

        var actual = original

        if (ajustes.reescalar) {
            val r = try {
                reescalador.reescalar(actual, ajustes.ladoReescalado)
            } catch (e: OutOfMemoryError) {
                null.also { fallido.add("reescalado: sin memoria") }
            } catch (e: Exception) {
                null.also { fallido.add("reescalado: ${e.javaClass.simpleName}") }
            }
            if (r?.exito == true && r.bitmap != null) {
                actual = r.bitmap
                aplicado.add("${r.modelo} ${r.ms} ms")
            } else if (r != null) {
                fallido.add("reescalado: ${r.motivo}")
            }
        }

        if (ajustes.colorizar) {
            val r = try {
                colorizador.colorear(actual, ajustes.ladoColorizacion)
            } catch (e: OutOfMemoryError) {
                null.also { fallido.add("colorización: sin memoria") }
            } catch (e: Exception) {
                null.also { fallido.add("colorización: ${e.javaClass.simpleName}") }
            }
            if (r?.exito == true && r.bitmap != null) {
                actual = r.bitmap
                aplicado.add("colorize ${r.ms} ms")
            } else if (r != null) {
                fallido.add("colorización: ${r.motivo}")
            }
        }

        return Resultado(actual, aplicado, fallido, System.currentTimeMillis() - t0)
    }

    /**
     * Cuánto va a costar antes de hacerlo, para poder avisar en la interfaz.
     *
     * Los números vienen de la medición real en CPU de 2 núcleos, no de una
     * estimación. Se devuelven como rango porque un móvil puede ir más rápido o
     * más lento y prometer un número exacto sería mentir otra vez.
     */
    fun estimacionMs(ajustes: Ajustes): LongRange? {
        if (!ajustes.colorizar && !ajustes.reescalar) return 0L..0L
        var min = 0L
        var max = 0L
        if (ajustes.colorizar) {
            min += 1490; max += 1490
        }
        if (ajustes.reescalar) {
            // anime4x 2 673 ms es el caso normal; esrgan_x4 24 587 ms el peor.
            min += 2673; max += 24587
        }
        return min..max
    }
}
