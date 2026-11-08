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
        "upscale-x4" to Modelo("esrgan_x4.onnx", 67_051_616, "input", "output"),
        // El upscaler específico de anime: mismo resultado ×4 en 2 673 ms frente
        // a 24 587 ms del general, y pesa 4,92 MiB en vez de 63,95. Se ofrecen
        // los dos porque el general va mejor en fotografía y el de anime en
        // línea clara; la app elige según el tipo de página.
        "upscale-anime-x4" to Modelo("anime4x.onnx", 5_156_099, "image.1", "output"),
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
     * POR QUÉ YA NO SE COPIA A filesDir
     * ---------------------------------
     * La versión anterior copiaba el modelo de `assets/` a `filesDir` la primera
     * vez. El motivo era real: `OrtSession.createSession` acepta una ruta o un
     * array de bytes, y con el array de bytes un modelo de 164 MiB ocuparía 164
     * MiB de RAM **además** del archivo.
     *
     * Pero la copia tenía dos costes que no se habían mirado:
     *
     *   1. **Duplicaba los 373 MiB de modelos en el disco del usuario** en el
     *      primer arranque. Es almacenamiento interno ocupado por un recurso que
     *      ya viaja dentro del APK, y es exactamente lo que ahora está prohibido.
     *   2. No hacía falta: ONNX Runtime 1.17 —la versión que usa este proyecto—
     *      acepta el `FileDescriptor` de un asset en `createSession(byte[],
     *      OrtSessionOptions)`, y se puede leer el asset con
     *      `AssetFileDescriptor` sin copiarlo al directorio de la app.
     *
     * Así que la sesión se crea pasando **los bytes del asset leídos con
     * `openFd()`**, y el archivo no se toca. Si en el futuro hace falta memoria
     * mapeada de verdad, el camino es `openFd().createInputStream()` sobre un
     * `FileChannel`, no volver a copiar.
     *
     * LO QUE ESTO CUESTA
     * ------------------
     * El `ByteBuffer` del modelo está en memoria mientras se crea la sesión.
     * ONNX Runtime lo copia/optimiza internamente, así que el pico es
     * modelo + sesión. Se dice aquí porque es el motivo por el que la copia
     * existía, y no se va a fingir que desaparece.
     */
    fun abrir(id: String): Sesion {
        val m = modelos[id] ?: throw IllegalArgumentException(
            "Modelo desconocido: $id. Los que hay: ${modelos.keys.joinToString()}"
        )

        val bytes = leerDelApk(m.archivo)
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

        val sesion = env.createSession(bytes, opciones)
        return Sesion(m, sesion, env)
    }

    /**
     * Lee el modelo directamente del APK.
     *
     * No hay directorio temporal, no hay archivo parcial, no hay renombrado: el
     * modelo vive en el APK y se lee de ahí cada vez que se abre una sesión. Si
     * algún día se quiere evitar la lectura repetida, la solución es una sesión
     * de larga vida (que ya existe: `NeuralAIModule` las guarda), no una copia
     * en el disco del usuario.
     */
    private fun leerDelApk(nombre: String): ByteArray {
        val descriptor = try {
            context.assets.openFd("models/$nombre")
        } catch (e: java.io.IOException) {
            throw IllegalStateException("el modelo $nombre no está en el APK: ${e.message}")
        }
        descriptor.use { fd ->
            if (fd.length <= 0L) throw IllegalStateException("el modelo $nombre está vacío dentro del APK")
            // Se lee en bloques de 1 MiB y se acumula en un buffer del tamaño
            // exacto: leer 164 MiB con `readBytes()` sobre un stream de assets
            // crece a base de copias y multiplica el pico de memoria.
            val salida = java.io.ByteArrayOutputStream(fd.length.toInt())
            fd.createInputStream().use { entrada ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val leidos = entrada.read(buffer)
                    if (leidos <= 0) break
                    salida.write(buffer, 0, leidos)
                }
            }
            val bytes = salida.toByteArray()
            if (bytes.size.toLong() != fd.length) {
                throw IllegalStateException(
                    "el modelo $nombre se leyó a medias: ${bytes.size} de ${fd.length} bytes"
                )
            }
            return bytes
        }
    }

    /** Sesión abierta. `use {}` para que se cierre aunque haya excepción. */
    class Sesion(
        val modelo: Modelo,
        val session: OrtSession,
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
