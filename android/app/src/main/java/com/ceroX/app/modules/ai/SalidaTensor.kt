package com.ceroX.app.modules.ai

import ai.onnxruntime.OnnxTensor
import java.nio.FloatBuffer

/**
 * Lectura de tensores de salida, sin casteos que revientan.
 *
 * QUÉ ARREGLA ESTO
 * ----------------
 * Los dos módulos que consumen imágenes leían la salida del modelo así:
 *
 *     val rgb = out.get(0).value as Array<Array<FloatArray>>
 *
 * Es un casteo de [1,3,H,W] a una estructura de tres niveles. La salida de ONNX
 * Runtime tiene **cuatro** niveles (`Array<Array<Array<FloatArray>>>`), así que
 * el casteo lanzaba `ClassCastException` en cada llamada. En `KisaraColorizer`
 * la excepción se recogía y se devolvía «el modelo falló»: un mensaje honesto
 * sobre un fallo que no era del modelo. En `KisaraUpscaler` se devolvía `null`,
 * el pipeline caía al segundo modelo, también fallaba, y la app decía que
 * ninguno de los dos podía ejecutarse.
 *
 * Medido con los modelos del APK, no supuesto:
 *
 *   colorize.onnx     [1,5,H,W] -> [1,3,H,W]
 *   anime4x.onnx      [1,3,H,W] -> [1,3,4H,4W]
 *   esrgan_x4.onnx    [1,3,H,W] -> [1,3,4H,4W]
 *
 * POR QUÉ SE COMPRUEBAN LAS DIMENSIONES Y NO SE CONFÍA
 * ----------------------------------------------------
 * El código anterior deducía el alto de la imagen de salida dividiendo el total
 * entre el ancho (`rgb[0][0].size / w`). Con una salida ×4 eso da un alto falso
 * cuatro veces mayor y el `Bitmap` se crea con el tamaño equivocado. Aquí las
 * dimensiones se leen del tensor y se comparan con lo esperado; si no cuadran,
 * se dice cuál era el tamaño y no se inventa un `Bitmap`.
 */
object SalidaTensor {

    /** Canales por píxel de la imagen que devuelven los tres modelos. */
    const val CANALES = 3

    class FormaInesperada(val esperado: String, val obtenido: String) :
        Exception("la salida del modelo no tiene la forma esperada: se esperaba $esperado y llegó $obtenido")

    /**
     * Copia los tres canales de una salida [1,3,H,W] a un `IntArray` de píxeles ARGB.
     *
     * @param valor el `value` del tensor de salida, tal cual lo devuelve ONNX Runtime.
     * @param ancho ancho esperado; se comprueba contra el real.
     * @param alto alto esperado; se comprueba contra el real.
     */
    fun aPixeles(valor: Any?, ancho: Int, alto: Int): IntArray {
        val porCanal = leer(valor, ancho, alto)
        val pixeles = IntArray(ancho * alto)
        // La red devuelve [0,1]; hay que recortar antes de multiplicar, porque en
        // los bordes se sale del rango.
        for (i in pixeles.indices) {
            val r = (porCanal[0][i].coerceIn(0f, 1f) * 255f).toInt()
            val g = (porCanal[1][i].coerceIn(0f, 1f) * 255f).toInt()
            val b = (porCanal[2][i].coerceIn(0f, 1f) * 255f).toInt()
            pixeles[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return pixeles
    }

    /**
     * Devuelve los tres canales planos de una salida [1,3,H,W].
     *
     * Se acepta tanto el `FloatBuffer` (lo que expone ONNX Runtime cuando el
     * tensor no está convertido a objetos) como el array anidado. Las dos formas
     * aparecen según la versión y el tipo de tensor, y comprobar una sola ha
     * hecho fallar antes a más de un lector.
     */
    fun leer(valor: Any?, ancho: Int, alto: Int): Array<FloatArray> {
        val pixeles = ancho * alto
        val canales = Array(CANALES) { FloatArray(pixeles) }

        when (valor) {
            is FloatBuffer -> {
                // Formato plano: [1][3][H][W] seguido por canal.
                val copia = valor.duplicate()
                if (copia.remaining() < CANALES * pixeles) {
                    throw FormaInesperada(
                        "[1,3,$alto,$ancho] (${CANALES * pixeles} valores)",
                        "${copia.remaining()} valores",
                    )
                }
                for (c in 0 until CANALES) {
                    copia.get(canales[c], 0, pixeles)
                }
            }

            is Array<*> -> {
                // Formato anidado: [1][3][H][W] o [1][3][H*W].
                val lote = valor.firstOrNull()
                    ?: throw FormaInesperada("[1,3,$alto,$ancho]", "vacío")
                val porCanal = when (lote) {
                    is Array<*> -> lote
                    else -> throw FormaInesperada("[1,3,$alto,$ancho]", lote?.javaClass?.simpleName ?: "null")
                }
                if (porCanal.size < CANALES) {
                    throw FormaInesperada(
                        "$CANALES canales",
                        "${porCanal.size} canal(es): ${porCanal.firstOrNull()?.javaClass?.simpleName}",
                    )
                }
                for (c in 0 until CANALES) {
                    val canal = porCanal[c]
                    when (canal) {
                        is FloatArray -> {
                            if (canal.size < pixeles) {
                                throw FormaInesperada("$pixeles valores por canal", "${canal.size}")
                            }
                            canal.copyInto(canales[c], 0, 0, pixeles)
                        }

                        is Array<*> -> {
                            // [H][W]
                            if (canal.size < alto) {
                                throw FormaInesperada("$alto filas", "${canal.size}")
                            }
                            for (y in 0 until alto) {
                                val fila = canal[y] as? FloatArray
                                    ?: throw FormaInesperada("fila de FloatArray", "otra cosa")
                                if (fila.size < ancho) {
                                    throw FormaInesperada("$ancho columnas", "${fila.size}")
                                }
                                fila.copyInto(canales[c], y * ancho, 0, ancho)
                            }
                        }

                        else -> throw FormaInesperada(
                            "canal plano o por filas",
                            canal?.javaClass?.simpleName ?: "null",
                        )
                    }
                }
            }

            else -> throw FormaInesperada(
                "[1,3,$alto,$ancho]",
                valor?.javaClass?.simpleName ?: "null",
            )
        }
        return canales
    }

    /** Alto y ancho reales de una salida [1,3,H,W], para el caso de las ×4. */
    fun forma(valor: Any?): Pair<Int, Int>? {
        val lado = { n: Int -> kotlin.math.sqrt(n.toDouble()).toInt().let { if (it * it == n) it else null } }
        return when (valor) {
            is FloatBuffer -> lado(valor.remaining() / CANALES)?.let { it to it }
            is Array<*> -> {
                val canal = (valor.firstOrNull() as? Array<*>)?.firstOrNull()
                when (canal) {
                    is Array<*> -> canal.size to ((canal.firstOrNull() as? FloatArray)?.size ?: 0)
                    is FloatArray -> lado(canal.size)?.let { it to it }
                    else -> null
                }
            }

            else -> null
        }
    }
}
