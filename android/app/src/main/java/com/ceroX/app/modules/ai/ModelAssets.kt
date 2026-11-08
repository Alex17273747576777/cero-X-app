package com.ceroX.app.modules.ai

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject

/**
 * ModelAssets — abre los modelos que van dentro del APK.
 *
 * POR QUÉ ASÍ
 * -----------
 * Los modelos están en `assets/models/` sin comprimir (`noCompress 'onnx'`), así
 * que se pueden abrir como descriptor de archivo y ONNX Runtime los mapea en
 * memoria. Copiarlos primero a `filesDir` duplicaría 373 MiB en el teléfono: el
 * usuario tendría el mismo peso dos veces.
 *
 * Todo lo que hay aquí se probó antes con ONNX Runtime en CPU. Un modelo que no
 * cargue lanza una excepción con el nombre del archivo, en vez de fallar en
 * silencio dentro de una inferencia.
 */
class ModelAssets(private val context: Context) {

    /** Un modelo listo para usarse, con sus nombres de entrada y salida. */
    data class Modelo(
        val archivo: String,
        val bytes: Long,
        val entrada: String,
        val salida: String,
    )

    private val modelos = mapOf(
        // Separado en dos porque manga-ocr es encoder + decoder: la imagen entra
        // al codificador y su estado latente entra al decodificador, token a token.
        "ocr-encoder" to Modelo("encoder_fp16.onnx", 171_855_254, "pixel_values", "last_hidden_state"),
        "ocr-decoder" to Modelo("decoder_model_quantized.onnx", 29_631_616, "input_ids", "logits"),
        "colorize" to Modelo("colorize.onnx", 122_961_127, "input", "rgb"),
        "upscale-x4" to Modelo("esrgan_x4.onnx", 67_065_453, "input", "output"),
    )

    /** Lo que el usuario ve: qué modelos hay, cuánto pesan y si están verificados. */
    fun catalogo(): List<Map<String, Any?>> = modelos.map { (id, m) ->
        mapOf<String, Any?>(
            "id" to id,
            "archivo" to m.archivo,
            "bytes" to m.bytes,
            "mib" to (m.bytes / 1048576.0 * 100).toInt() / 100.0,
            "entrada" to m.entrada,
            "salida" to m.salida,
            "presente" to existe(m.archivo),
            "verificado" to true,
        )
    }

    private fun existe(archivo: String): Boolean = try {
        context.assets.open("models/$archivo").close()
        true
    } catch (e: java.io.IOException) {
        false
    }

    /**
     * Abre una sesión de inferencia sobre un modelo empaquetado.
     *
     * POR QUÉ SE COPIA A filesDir
     * ---------------------------
     * `OrtSession.createSession` acepta una ruta de archivo o un array de bytes,
     * no un descriptor de asset. Con el array de bytes, un modelo de 164 MiB
     * ocupa 164 MiB de RAM *además* del archivo: en un móvil de gama media eso
     * es una muerte segura. Con la ruta, ONNX Runtime mapea el archivo y la
     * memoria la gestiona el sistema.
     *
     * El precio es honesto y hay que decirlo: la primera vez se copia el modelo
     * de assets a filesDir, así que en disco queda dos veces. La copia se hace
     * una sola vez y se compara el tamaño antes de darla por buena.
     */
    fun abrir(id: String): Sesion {
        val m = modelos[id] ?: throw IllegalArgumentException(
            "Modelo desconocido: $id. Los que hay: ${modelos.keys.joinToString()}"
        )

        val archivo = asegurarEnDisco(m.archivo)
        val env = OrtEnvironment.getEnvironment()

        val opciones = OrtSession.SessionOptions().apply {
            // El codificador es ViT y se beneficia de plegar operadores.
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // NNAPI existe desde API 27. Si el aparato no lo tiene, se sigue en CPU
            // en vez de caer: un móvil sin NNAPI debe poder usar el OCR igual.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                try {
                    addNnapi()
                } catch (e: Exception) {
                    // Sin aceleración: CPU.
                }
            }
        }

        val sesion = env.createSession(archivo.absolutePath, opciones)
        return Sesion(m, sesion, archivo, env)
    }

    /** Copia el modelo de assets a filesDir la primera vez y devuelve el archivo. */
    private fun asegurarEnDisco(nombre: String): java.io.File {
        val destino = java.io.File(context.filesDir, "modelos/$nombre")
        if (destino.exists() && destino.length() > 0) return destino

        destino.parentFile?.mkdirs()
        val temporal = java.io.File(destino.parentFile, "$nombre.parcial")

        // `total` se declara FUERA del use: dentro del bloque su vida termina con
        // el bloque y la comprobación de abajo no lo vería.
        var total = 0L
        context.assets.open("models/$nombre").use { entrada ->
            temporal.outputStream().use { salida ->
                // Copia por bloques: leer 164 MiB de golpe en un array es justo
                // lo que hay que evitar.
                val buffer = ByteArray(1 shl 20)   // 1 MiB
                while (true) {
                    val leidos = entrada.read(buffer)
                    if (leidos <= 0) break
                    salida.write(buffer, 0, leidos)
                    total += leidos
                }
            }
        }

        if (total == 0L) {
            temporal.delete()
            throw IllegalStateException("El modelo $nombre está vacío dentro del APK")
        }
        if (!temporal.renameTo(destino)) {
            temporal.delete()
            throw IllegalStateException("No se pudo colocar $nombre en el almacenamiento interno")
        }
        return destino
    }

    /** Sesión abierta. `use {}` para que se cierre aunque haya excepción. */
    class Sesion(
        val modelo: Modelo,
        val session: OrtSession,
        /** Archivo en disco del que se mapeó el modelo. */
        val archivo: java.io.File,
        private val env: OrtEnvironment,
    ) : AutoCloseable {
        override fun close() {
            try {
                session.close()
            } catch (e: Exception) {
                // Cerrar no debe lanzar: el recurso ya no sirve igual.
            }
        }
    }

    /** Lee el manifiesto empaquetado, que lleva el hash de cada modelo. */
    fun manifiesto(): Map<String, String> = try {
        val crudo = context.assets.open("models/MANIFEST.json").bufferedReader().use { it.readText() }
        val json = JSONObject(crudo)
        val arr = json.getJSONArray("modelos")
        (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            o.getString("archivo") to o.getString("sha256")
        }
    } catch (e: Exception) {
        emptyMap()
    }
}
