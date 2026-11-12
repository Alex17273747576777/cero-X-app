package com.ceroX.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La puerta del motor, comprobada. La tercera prueba es una puerta publicada:
 * recorre las fuentes y no deja que nadie cree el entorno ONNX a pelo fuera de
 * MotorOnnx, que es la forma de que la variante sin .so no reviente por un
 * camino que nadie vigiló.
 */
class MotorOnnxTest {

    @Test
    fun cargadorQueRevientaDaMotivoYSilencioNo() {
        val motivo = MotorOnnx.estado({ throw UnsatisfiedLinkError("libonnxruntime.so") })
        assertNotNull("un cargador que revienta tiene que dar motivo", motivo)
        assertTrue("el motivo tiene que llevar lo que faltó: %s".format(motivo),
            motivo!!.contains("libonnxruntime.so"))
        assertTrue("el motivo tiene que decir que es el motor: %s".format(motivo),
            motivo.contains("motor ONNX no disponible"))
    }

    @Test
    fun cargadorSanoDaNulo() {
        assertNull(MotorOnnx.estado({ Any() }))
    }

    @Test
    fun nadieCreaElEntornoAPeloFueraDeLaPuerta() {
        var d = File(".").absoluteFile
        while (d.parentFile != null && !File(d, "android/app/src/main/java").isDirectory) {
            d = d.parentFile
        }
        val raiz = File(d, "android/app/src/main/java/com/ceroX/app")
        assertTrue("no encontré las fuentes desde %s".format(d), raiz.isDirectory)
        val llamada = "%s%s".format("OrtEnvironment.get", "Environment(")
        val culpables = raiz.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") && it.name != "MotorOnnx.kt" }
            .filter { it.readText().contains(llamada) }
            .map { it.name }
            .toList()
        assertEquals("el entorno se crea sólo en MotorOnnx.kt, no en %s".format(culpables),
            emptyList<String>(), culpables)
    }
}
