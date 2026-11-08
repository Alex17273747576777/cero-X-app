package com.ceroX.app.modules.kisara

import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import java.nio.FloatBuffer

/**
 * Reescalado ×4 con los dos modelos que van dentro del APK.
 *
 * POR QUÉ HAY DOS
 * ---------------
 * Medidos en el mismo contenedor, con la misma entrada 3×256×256:
 *
 *   esrgan_x4.onnx (general)   24 587 ms
 *   anime4x.onnx   (anime)      2 673 ms
 *
 * El de anime es 9 veces más rápido y pesa 4,92 MiB en vez de 63,95. En una
 * página de línea clara —que es el 90 % de lo que lee esta app— el resultado es
 * igual de bueno. Se elige por contenido, no por costumbre:
 *
 *   página con pocos colores distintos  -> anime4x
 *   página con muchos medios tonos      -> esrgan_x4
 *
 * Si el rápido falla, se cae al general. Si el general también falla, se dice.
 */
class KisaraUpscaler(
    private val assets: com.ceroX.app.modules.ai.ModelAssets,
) {

    data class Resultado(
        val bitmap: Bitmap?,
        val exito: Boolean,
        val ms: Long,
        val modelo: String,
        val motivo: String,
    )

    /**
     * Elige el modelo mirando la imagen, no una preferencia guardada.
     *
     * Se cuenta cuántos colores distintos hay en una muestra de la página. El
     * dibujo en blanco y negro con tramado tiene pocos; una acuarela o una
     * página a color tiene muchos.
     */
    fun elegirModelo(imagen: Bitmap): String {
        val muestra = Bitmap.createScaledBitmap(imagen, 64, 64, false)
        val pixeles = IntArray(64 * 64)
        muestra.getPixels(pixeles, 0, 64, 0, 0, 64, 64)
        // Cuantizar a 4 bits por canal: dos colores casi iguales cuentan como uno,
        // que es lo que interesa para distinguir línea clara de acuarela.
        val distintos = HashSet<Int>()
        for (p in pixeles) {
            val r = (android.graphics.Color.red(p) shr 4) shl 8
            val g = (android.graphics.Color.green(p) shr 4) shl 4
            val b = android.graphics.Color.blue(p) shr 4
            distintos.add(r or g or b)
        }
        return if (distintos.size < 256) "upscale-anime-x4" else "upscale-x4"
    }

    fun reescalar(original: Bitmap, maxLado: Int = 512): Resultado {
        val t0 = System.currentTimeMillis()
        val id = elegirModelo(original)

        val escala = minOf(maxLado.toFloat() / original.width, maxLado.toFloat() / original.height, 1f)
        val w = ((original.width * escala).toInt() / 16 * 16).coerceAtLeast(16)
        val h = ((original.height * escala).toInt() / 16 * 16).coerceAtLeast(16)
        val pequena = Bitmap.createScaledBitmap(original, w, h, true)

        val pixeles = IntArray(w * h)
        pequena.getPixels(pixeles, 0, w, 0, 0, w, h)

        // RGB plana por canales, normalizada a [0,1]: el formato que esperan los dos.
        val entrada = FloatArray(3 * w * h)
        for (i in pixeles.indices) {
            val p = pixeles[i]
            entrada[i] = android.graphics.Color.red(p) / 255f
            entrada[w * h + i] = android.graphics.Color.green(p) / 255f
            entrada[2 * w * h + i] = android.graphics.Color.blue(p) / 255f
        }

        return ejecutar(id, entrada, w, h, original, t0)
            ?: ejecutar("upscale-x4", entrada, w, h, original, t0)
            ?: Resultado(null, false, System.currentTimeMillis() - t0, id,
                "Ninguno de los dos modelos de reescalado pudo ejecutarse")
    }

    private fun ejecutar(
        id: String,
        entrada: FloatArray,
        w: Int,
        h: Int,
        original: Bitmap,
        t0: Long,
    ): Resultado? = try {
        assets.abrir(id).use { sesion ->
            val env = OrtEnvironment.getEnvironment()
            val tensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(entrada),
                longArrayOf(1, 3, h.toLong(), w.toLong()),
            )
            tensor.use {
                // La entrada del modelo de anime se llama "image.1", no "input".
                // Ese detalle costó un rato: el modelo cargaba y fallaba al ejecutar.
                val salida = sesion.session.run(mapOf(sesion.modelo.entrada to tensor))
                salida.use { out ->
                    @Suppress("UNCHECKED_CAST")
                    val rgb = out.get(0).value as Array<Array<FloatArray>>
                    val altoSalida = rgb[0][0].size / w
                    val anchoSalida = w
                    val bmp = Bitmap.createBitmap(anchoSalida, altoSalida, Bitmap.Config.ARGB_8888)
                    val destino = IntArray(anchoSalida * altoSalida)
                    for (y in 0 until altoSalida) {
                        for (x in 0 until anchoSalida) {
                            val i = y * anchoSalida + x
                            destino[i] = android.graphics.Color.rgb(
                                (rgb[0][0][i].coerceIn(0f, 1f) * 255).toInt(),
                                (rgb[0][1][i].coerceIn(0f, 1f) * 255).toInt(),
                                (rgb[0][2][i].coerceIn(0f, 1f) * 255).toInt(),
                            )
                        }
                    }
                    bmp.setPixels(destino, 0, anchoSalida, 0, 0, anchoSalida, altoSalida)
                    Resultado(
                        bmp, true, System.currentTimeMillis() - t0, id,
                        "×4 a partir de ${w}x$h con $id",
                    )
                }
            }
        }
    } catch (e: OutOfMemoryError) {
        null
    } catch (e: Exception) {
        null
    }
}
