package com.ceroX.app.modules.kisara

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.TensorInfo
import ai.onnxruntime.OrtEnvironment
import com.ceroX.app.modules.ai.SalidaTensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.FloatBuffer

/**
 * Los modelos del APK se ejecutan de verdad, y la salida se lee como es.
 *
 * POR QUÉ ESTA PRUEBA EXISTE
 * --------------------------
 * Había 379 pruebas en verde y dos funciones que no funcionaban: la colorización y
 * el reescalado. Ninguna prueba cargaba un modelo. `ModelHonestyTest` miraba el
 * texto del código, que es útil para lo que hace —que nadie anuncie un archivo que
 * no está— pero no puede ver un casteo equivocado.
 *
 * El fallo concreto que se coló:
 *
 *     val rgb = out.get(0).value as Array<Array<FloatArray>>
 *
 * La salida de estos modelos tiene cuatro niveles. El casteo lanzaba
 * ClassCastException en cada página, el `catch` lo contaba como fallo del modelo, y
 * la app seguía diciendo que tenía colorización y reescalado ×4.
 *
 * Esta prueba carga cada modelo, ejecuta una inferencia con una entrada del tamaño
 * que el propio modelo declara y comprueba **la forma de la salida**. Si alguien
 * vuelve a suponer la forma, esto se pone en rojo.
 *
 * SOBRE LOS MODELOS AUSENTES
 * --------------------------
 * Los `.onnx` no viven en git (373 MiB, GitHub rechaza más de 100 MB). Se bajan con
 * `scripts/bajar-modelos.py`, que comprueba el sha256. Si no están, la prueba se
 * salta con un mensaje que dice cómo traerlos: un rojo por un archivo que falta no
 * enseña nada, y un verde sin haber ejecutado nada engaña.
 */
class ModelosEjecutadosTest {

    private val dir = File("src/main/assets/models")

    private fun modelo(nombre: String): File? = dir.resolve(nombre).takeIf { it.isFile }

    private fun saltarSiFalta(nombre: String): File {
        val f = modelo(nombre)
        assumeTrue(
            "$nombre no está: ejecuta scripts/bajar-modelos.py (comprueba sha256)",
            f != null,
        )
        return f!!
    }

    private fun entrada(env: OrtEnvironment, forma: LongArray): OnnxTensor {
        var n = 1L
        for (d in forma) n *= d
        val datos = FloatArray(n.toInt()) { 0.5f }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(datos), forma)
    }

    @Test
    fun `colorize devuelve 1x3xHxW y se lee sin casteos`() {
        val f = saltarSiFalta("colorize.onnx")
        val env = OrtEnvironment.getEnvironment()
        OrtEnvironment.getEnvironment().let {
            ai.onnxruntime.OrtSession.SessionOptions().use { opts ->
                env.createSession(f.absolutePath, opts).use { sesion ->
                    // La entrada son CINCO canales: luminancia, dos cromáticas y dos guías.
                    entrada(env, longArrayOf(1, 5, 64, 64)).use { t ->
                        sesion.run(mapOf(sesion.inputNames.first() to t)).use { out ->
                            val valor = out.get(0).value
                            val forma = (out.get(0).info as TensorInfo).shape.toList() // [1,3,64,64]
                            assertEquals(
                                "la salida de colorize tiene que ser [1,3,H,W]; si cambia, el lector cambia",
                                listOf(1L, 3L, 64L, 64L), forma,
                            )
                            // Y lo que importa: que el lector real la entienda.
                            val pixeles = SalidaTensor.aPixeles(valor, 64, 64)
                            assertEquals(64 * 64, pixeles.size)
                            // Cada píxel tiene que ser opaco: alfa a 255.
                            assertTrue(
                                "algún píxel salió sin alfa",
                                pixeles.all { (it ushr 24 and 0xFF) == 0xFF },
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `los dos modelos de reescalado devuelven el cuadrado del lado por cuatro`() {
        for (nombre in listOf("anime4x.onnx", "esrgan_x4.onnx")) {
            val f = saltarSiFalta(nombre)
            val env = OrtEnvironment.getEnvironment()
            ai.onnxruntime.OrtSession.SessionOptions().use { opts ->
                env.createSession(f.absolutePath, opts).use { sesion ->
                    val lado = 32
                    entrada(env, longArrayOf(1, 3, lado.toLong(), lado.toLong())).use { t ->
                        sesion.run(mapOf(sesion.inputNames.first() to t)).use { out ->
                            val forma = (out.get(0).info as TensorInfo).shape.toList()
                            assertEquals(
                                "$nombre tiene que devolver 4 veces el lado en alto y ancho",
                                listOf(1L, 3L, (lado * 4).toLong(), (lado * 4).toLong()),
                                forma,
                            )
                            // El lector tiene que devolver exactamente ese tamaño.
                            // Antes se deducía dividiendo el total entre el ancho, y
                            // daba 4 veces el alto real.
                            val pixeles = SalidaTensor.aPixeles(out.get(0).value, lado * 4, lado * 4)
                            assertEquals(lado * 4 * lado * 4, pixeles.size)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `el lector rechaza una forma que no es la esperada en vez de inventarsela`() {
        // Una salida [1,3,8,8] leída como si fuera de 32x32: antes esto creaba un
        // Bitmap del tamaño equivocado y pintaba basura. Ahora se dice.
        val falsa = arrayOf(arrayOf(Array(8) { FloatArray(8) }))
        val fallo = runCatching { SalidaTensor.aPixeles(falsa, 32, 32) }.exceptionOrNull()
        assertTrue(
            "leer una salida 8x8 como 32x32 tiene que fallar, no inventarse un tamaño",
            fallo is SalidaTensor.FormaInesperada,
        )
    }
}
