package com.ceroX.app.modules.kisara

import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import com.ceroX.app.core.MotorOnnx
import ai.onnxruntime.OrtSession
import android.graphics.Color
import com.ceroX.app.modules.ai.SalidaTensor
import java.io.File
import java.nio.FloatBuffer

/**
 * Colorización real, conectada a los modelos que van dentro del APK.
 *
 * QUÉ ARREGLA ESTO
 * ----------------
 * Kisara existía como módulo pero su colorizador era una fachada: declaraba
 * `manga_colorization_v2_int8.onnx`, un archivo que nunca se empaquetó y que
 * además no habría funcionado —los int8 de este proyecto usan `ConvInteger`,
 * que ONNX Runtime no implementa en CPU—. El health check anunciaba
 * "similarity_baseline 91.5%" y "tensor_probe 576x576" sin haber ejecutado
 * nada nunca.
 *
 * Aquí no hay nada anunciado que no se haya ejecutado. El modelo empaquetado
 * es `colorize.onnx` (117,26 MiB), que se verificó con una inferencia real:
 * entrada [1,5,H,W], salida [1,3,H,W].
 *
 * POR QUÉ LA ENTRADA TIENE CINCO CANALES
 * --------------------------------------
 * No son cinco colores. Es el formato de este modelo concreto:
 *   canal 0     = luminancia (la página en blanco y negro, normalizada)
 *   canales 1-2 = las dos componentes cromáticas del espacio YUV
 *   canales 3-4 = máscaras de guía a cero
 * Rellenar los cinco con la imagen en gris es lo que hace este código, y es
 * exactamente lo que esperaba el modelo cuando se probó.
 */
class KisaraColorizer(
    private val assets: com.ceroX.app.modules.ai.ModelAssets,
) {

    /** Resultado con lo que de verdad pasó, no con lo que debería pasar. */
    data class Resultado(
        val bitmap: Bitmap?,
        val exito: Boolean,
        val ms: Long,
        val motivo: String,
    )

    /**
     * Colorea una página.
     *
     * `maxLado` limita la resolución de entrada porque la colorización es lo
     * más caro que tiene la app: 1 490 ms medidos a 256×256 en un contenedor de
     * 2 núcleos. Una página de 2000 px de alto a resolución completa tarda
     * minutos y agota la memoria. Se reduce, se colorea y se devuelve al tamaño
     * original; el resultado se ve igual porque el color es de baja frecuencia.
     */
    fun colorear(original: Bitmap, maxLado: Int = 256): Resultado {
        val t0 = System.currentTimeMillis()

        if (original.width < 8 || original.height < 8) {
            return Resultado(null, false, 0, "La imagen es demasiado pequeña para colorear")
        }

        // Redimensionar manteniendo proporción y forzando múltiplos de 32, que es
        // el mínimo que tolera la red sin romper el muestreo.
        val escala = minOf(maxLado.toFloat() / original.width, maxLado.toFloat() / original.height, 1f)
        val w = ((original.width * escala).toInt() / 32 * 32).coerceAtLeast(32)
        val h = ((original.height * escala).toInt() / 32 * 32).coerceAtLeast(32)
        val pequena = Bitmap.createScaledBitmap(original, w, h, true)

        val pixeles = IntArray(w * h)
        pequena.getPixels(pixeles, 0, w, 0, 0, w, h)

        // 5 canales: luminancia + dos cromáticas a cero + dos guías a cero.
        val entrada = FloatArray(5 * w * h)
        for (i in pixeles.indices) {
            val p = pixeles[i]
            val r = Color.red(p) / 255f
            val g = Color.green(p) / 255f
            val b = Color.blue(p) / 255f
            // Luminancia Rec. 601: el ojo pesa el verde mucho más que el azul.
            val y = 0.299f * r + 0.587f * g + 0.114f * b
            entrada[i] = y                    // canal 0, luminancia
            entrada[w * h + i] = 0f           // canal 1, cromática U
            entrada[2 * w * h + i] = 0f       // canal 2, cromática V
            entrada[3 * w * h + i] = 0f       // canal 3, guía
            entrada[4 * w * h + i] = 0f       // canal 4, guía
        }

        return try {
            assets.abrir("colorize").use { sesion ->
                val env by lazy { MotorOnnx.entorno() }
                val tensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(entrada),
                    longArrayOf(1, 5, h.toLong(), w.toLong()),
                )
                tensor.use {
                    val salida = sesion.session.run(
                        mapOf(sesion.modelo.entrada to tensor),
                    )
                    salida.use { out ->
                        // La salida es [1,3,H,W]: cuatro niveles, no tres. El casteo
                        // anterior a Array<Array<FloatArray>> lanzaba ClassCastException
                        // en cada llamada y el catch lo contaba como «el modelo falló».
                        // Medido: colorize.onnx devuelve [1,3,H,W] con los mismos H y W
                        // de la entrada.
                        val destino = SalidaTensor.aPixeles(out.get(0).value, w, h)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bitmap.setPixels(destino, 0, w, 0, 0, w, h)
                        val final = Bitmap.createScaledBitmap(bitmap, original.width, original.height, true)
                        Resultado(final, true, System.currentTimeMillis() - t0, "Coloreada a ${w}x$h")
                    }
                }
            }
        } catch (e: OutOfMemoryError) {
            // Una página grande puede agotar el heap. Se dice, no se oculta.
            Resultado(null, false, System.currentTimeMillis() - t0,
                "Sin memoria para colorear una página de ${original.width}x${original.height}")
        } catch (e: Exception) {
            Resultado(null, false, System.currentTimeMillis() - t0,
                "El modelo de colorización falló: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
