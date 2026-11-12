package com.ceroX.app.core

import ai.onnxruntime.OrtEnvironment

/**
 * La puerta del motor ONNX.
 *
 * `estado()` dice por qué la biblioteca nativa no está, o null si está. No dice
 * que el motor funcione: decir eso exigiría cargar un modelo y ejecutarlo, y
 * esta puerta no hace ninguna de las dos cosas. `entorno()` devuelve el
 * entorno o lanza con el motivo, porque un [UnsatisfiedLinkError] es un Error y
 * no un Exception: sin esta puerta, una variante sin los .so reventaría cruzando
 * código que sólo caza Exception, que es justo lo que la variante universal
 * necesita no hacer.
 */
object MotorOnnx {

    fun estado(cargador: () -> Any? = { OrtEnvironment.getEnvironment() }): String? =
        try {
            cargador()
            null
        } catch (t: Throwable) {
            "motor ONNX no disponible en esta variante: " +
                (t.message ?: t.javaClass.simpleName)
        }

    fun entorno(): OrtEnvironment {
        val motivo = estado()
        if (motivo != null) throw IllegalStateException(motivo)
        return OrtEnvironment.getEnvironment()
    }
}
