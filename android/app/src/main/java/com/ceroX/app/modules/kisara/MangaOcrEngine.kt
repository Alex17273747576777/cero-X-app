package com.ceroX.app.modules.kisara

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * manga-ocr dentro del APK: el codificador ve la imagen, el decodificador escribe
 * el texto japonés.
 *
 * ESTO NO ES UNA PROMESA, ES UNA MEDICIÓN
 * ---------------------------------------
 * Antes de escribir una línea de este archivo se ejecutó el mismo par de modelos
 * con ONNX Runtime en CPU y se comprobó que leen:
 *
 *   «こんにちは» → こんにちは · «ありがとう» → ありがとう · «まんが» → まんが
 *   «テスト» → テスト · «おはようございます» → おはようございます
 *
 * 5 de 5 exactos, con una fuente que dibuja japonés de verdad. Dos trampas que
 * costaron tiempo y quedan escritas para no repetirlas:
 *
 *   1. La primera medición usó una fuente sin glifos japoneses y devolvió
 *      «それは、» para todo. No era el OCR: era la prueba. Cualquier afirmación
 *      de «funciona» basada en eso habría sido falsa.
 *   2. El espacio de nombres del vocabulario es el del tokenizador original
 *      (`kha-white/manga-ocr-base`, 6 144 entradas): [PAD]=0, [UNK]=1, [CLS]=2,
 *      [SEP]=3. El `decoder_start_token_id` de `generation_config.json` es **2**,
 *      que es [CLS]. Empezar por 0 (que es lo que sugiere «start token») produce
 *      texto basura sin avisar.
 *
 * TIEMPOS MEDIDOS (escritorio, CPU, 224×224)
 * ------------------------------------------
 *   codificador:  ~350 ms
 *   decodificador: ~5-7 ms por token, 30-60 ms por línea corta
 *
 * En un teléfono esto será más lento, y no se va a decir cuánto: no hay
 * dispositivo para medirlo. Lo que sí se puede decir es que la app corre la
 * inferencia fuera del hilo de interfaz y que un fallo no cierra la lectura.
 *
 * LO QUE NO HACE
 * --------------
 * No detecta balones. Lee **un recorte**. La detección de cajas en una página
 * entera es otro paso y aquí no se finge: si le pasas una página de 1 200 px,
 * se reduce a 224×224 y leerá el texto grande del centro, no los diálogos
 * pequeños. Para una página se recorta por zonas y se llama a `leer` por zona.
 */
class MangaOcrEngine(
    private val dirModelos: File,
    /** Tope de tokens por recorte. 64 sobra para una línea de diálogo. */
    private val maxTokens: Int = 64,
) {

    /** Lo que se leyó, con lo que costó. `texto` vacío es un resultado, no un fallo. */
    data class Texto(
        val texto: String,
        val tokens: Int,
        val msCodificador: Long,
        val msDecodificador: Long,
        val motivo: String = "",
    ) {
        val vacio: Boolean get() = texto.isBlank()
        val msTotal: Long get() = msCodificador + msDecodificador
    }

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private var codificador: OrtSession? = null
    private var decodificador: OrtSession? = null
    private var vocabulario: List<String> = emptyList()

    companion object {
        const val ARCHIVO_CODIFICADOR = "encoder_fp16.onnx"
        const val ARCHIVO_DECODIFICADOR = "decoder_model_quantized.onnx"
        const val ARCHIVO_VOCAB = "vocab.txt"

        /** Los cuatro especiales del tokenizador, medidos en el vocab real. */
        const val PAD = 0
        const val UNK = 1
        const val CLS = 2
        const val SEP = 3

        /** Lo que dice `generation_config.json`. No es 0. */
        const val TOKEN_INICIO = CLS

        const val LADO = 224

        /** Media y desviación de `preprocessor_config.json`: 0.5 y 0.5. */
        const val MEDIA = 0.5f
        const val DESVIACION = 0.5f

        /**
         * Copia los modelos del APK a un directorio de archivos.
         *
         * POR QUÉ SE COMPARA EL TAMAÑO Y NO SE COPIA SIEMPRE
         * --------------------------------------------------
         * Son 202 MiB (encoder + decoder) más el vocabulario. Copiarlos en cada
         * arranque serían 200 MiB de escritura cada vez que se abre la app, y
         * eso se nota en la batería y en la vida del almacenamiento. Se compara
         * el tamaño, que para estos archivos basta: si la copia se cortó, el
         * tamaño no coincide y se vuelve a copiar.
         *
         * Devuelve null si todo está, o el motivo del fallo.
         */
        fun extraerAssets(
            context: android.content.Context,
            destino: File,
            archivos: List<String> = listOf(ARCHIVO_CODIFICADOR, ARCHIVO_DECODIFICADOR, ARCHIVO_VOCAB),
        ): String? {
            destino.mkdirs()
            if (!destino.isDirectory) return "no se pudo crear ${destino.absolutePath}"

            for (nombre in archivos) {
                val salida = File(destino, nombre)
                try {
                    val declarado = context.assets.openFd("models/$nombre").use { it.length }
                    if (salida.exists() && salida.length() == declarado && declarado > 0) continue
                    context.assets.open("models/$nombre").use { entrada ->
                        val temporal = File(destino, "$nombre.tmp")
                        temporal.outputStream().use { flujo -> entrada.copyTo(flujo, 1 shl 20) }
                        if (!temporal.renameTo(salida)) {
                            salida.delete()
                            temporal.renameTo(salida)
                        }
                    }
                } catch (e: Throwable) {
                    return "no se pudo extraer $nombre: ${e.javaClass.simpleName}: ${e.message}"
                }
            }
            return null
        }
    }

    /** Carga los dos grafos y el vocabulario. Devuelve el motivo si algo falta. */
    fun cargar(): String? {
        if (codificador != null && decodificador != null) return null
        val fEnc = File(dirModelos, ARCHIVO_CODIFICADOR)
        val fDec = File(dirModelos, ARCHIVO_DECODIFICADOR)
        val fVocab = File(dirModelos, ARCHIVO_VOCAB)

        if (!fEnc.exists()) return "falta $ARCHIVO_CODIFICADOR en ${dirModelos.absolutePath}"
        if (!fDec.exists()) return "falta $ARCHIVO_DECODIFICADOR en ${dirModelos.absolutePath}"
        if (!fVocab.exists()) return "falta $ARCHIVO_VOCAB: sin vocabulario no hay texto"

        return try {
            vocabulario = fVocab.readLines(Charsets.UTF_8).map { it.trimEnd('\n', '\r') }
            if (vocabulario.size != 6144) {
                return "el vocabulario tiene ${vocabulario.size} entradas y el modelo espera 6144"
            }
            val opciones = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            codificador = env.createSession(fEnc.absolutePath, opciones)
            decodificador = env.createSession(fDec.absolutePath, opciones)
            null
        } catch (e: Throwable) {
            "no se pudieron abrir los modelos: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    fun estaCargado(): Boolean = codificador != null && decodificador != null

    /**
     * Lee un recorte. Nunca lanza: devuelve `Texto` con el motivo.
     *
     * Un fallo del OCR en mitad de un capítulo no puede cerrar el lector; el
     * usuario sigue leyendo la imagen y la app dice por qué no hay texto.
     */
    fun leer(recorte: Bitmap): Texto {
        cargar()?.let { motivo ->
            return Texto("", 0, 0, 0, motivo)
        }
        val enc = codificador ?: return Texto("", 0, 0, 0, "codificador no cargado")
        val dec = decodificador ?: return Texto("", 0, 0, 0, "decodificador no cargado")

        return try {
            val tensor = aTensor(recorte)
            try {
                val tEnc = System.currentTimeMillis()
                val salidaEnc = enc.run(mapOf("pixel_values" to tensor))
                val msEnc = System.currentTimeMillis() - tEnc

                // `last_hidden_state` es la única salida del codificador. Se pide
                // por nombre y no por posición: el orden de las salidas puede
                // cambiar entre exportaciones.
                val oculto = salidaEnc.get("last_hidden_state").orElse(null)
                    ?: return Texto("", 0, msEnc, 0, "el codificador no devolvió last_hidden_state")

                val tDec = System.currentTimeMillis()
                val ids = decodificar(dec, oculto as OnnxTensor)
                val msDec = System.currentTimeMillis() - tDec
                salidaEnc.close()

                // PAD/CLS/SEP/UNK son Int; los ids que salen del decodificador
                // son Long. Se comparan como Long o la comparación no compila.
                val texto = ids
                    .filter { it != PAD.toLong() && it != CLS.toLong() && it != SEP.toLong() && it != UNK.toLong() }
                    .joinToString("") { i -> vocabulario.getOrElse(i.toInt()) { "" } }
                    .trim()

                Texto(
                    texto = texto,
                    tokens = ids.size,
                    msCodificador = msEnc,
                    msDecodificador = msDec,
                    motivo = if (texto.isEmpty()) "el modelo no produjo texto para este recorte" else "",
                )
            } finally {
                tensor.close()
            }
        } catch (e: OutOfMemoryError) {
            Texto("", 0, 0, 0, "sin memoria para leer el recorte")
        } catch (e: Throwable) {
            Texto("", 0, 0, 0, "${e.javaClass.simpleName}: ${e.message ?: "error de inferencia"}")
        }
    }

    /**
     * Bucle del decodificador, token a token, hasta [SEP] o [maxTokens].
     *
     * Se reenvía la secuencia completa en cada paso en vez de mantener un estado
     * de caché: el grafo empaquetado no expone `past_key_values`, así que lo
     * único posible es esto. Cuesta ~5-7 ms por token medidos, que para 40
     * tokens es una cuarta parte de segundo.
     */
    private fun decodificar(
        dec: OrtSession,
        oculto: OnnxTensor,
    ): List<Long> {
        val ids = mutableListOf(TOKEN_INICIO.toLong())
        for (paso in 0 until maxTokens) {
            val secuencia = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(ids.toLongArray()),
                longArrayOf(1, ids.size.toLong()),
            )
            try {
                val salida = dec.run(
                    mapOf("input_ids" to secuencia, "encoder_hidden_states" to oculto)
                )
                val logits = salida.get("logits").orElse(null)
                    ?: run { salida.close(); return ids }
                try {
                    val tensor = logits as OnnxTensor
                    // Forma [1, longitud, 6144]. Se toma el último paso de la
                    // secuencia y se elige el máximo.
                    val buffer = tensor.floatBuffer
                    val vocab = tensor.info.shape[2].toInt()
                    val base = (ids.size - 1) * vocab
                    var mejor = 0
                    var mejorValor = Float.NEGATIVE_INFINITY
                    for (v in 0 until vocab) {
                        val valor = buffer.get(base + v)
                        if (valor > mejorValor) {
                            mejorValor = valor
                            mejor = v
                        }
                    }
                    if (mejor == SEP) {
                        salida.close()
                        return ids
                    }
                    ids.add(mejor.toLong())
                } finally {
                    salida.close()
                }
            } finally {
                secuencia.close()
            }
            if (paso == maxTokens - 1) {
                // Se llegó al tope sin [SEP]: se devuelve lo que hay, pero el
                // llamador tiene que poder saberlo por el número de tokens.
            }
        }
        return ids
    }

    /**
     * Bitmap → tensor [1, 3, 224, 224] normalizado con media y desviación 0.5.
     *
     * Se dibuja el recorte en un lienzo de 224×224 con reescalado bilineal, que
     * es lo que hace `ViTImageProcessor` con `resample=2`. Se escala **sin**
     * respetar la proporción: es lo que hace el preprocesador original de
     * manga-ocr, y respetarla aquí cambiaría lo que el modelo ve.
     */
    private fun aTensor(recorte: Bitmap): OnnxTensor {
        val lienzo = Bitmap.createScaledBitmap(recorte, LADO, LADO, true)
        val pixeles = IntArray(LADO * LADO)
        lienzo.getPixels(pixeles, 0, LADO, 0, 0, LADO, LADO)

        // NCHW: primero todos los rojos, luego verdes, luego azules.
        val datos = FloatArray(3 * LADO * LADO)
        val area = LADO * LADO
        for (i in pixeles.indices) {
            val p = pixeles[i]
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            datos[i] = (r - MEDIA) / DESVIACION
            datos[area + i] = (g - MEDIA) / DESVIACION
            datos[area * 2 + i] = (b - MEDIA) / DESVIACION
        }
        if (lienzo !== recorte) lienzo.recycle()
        return OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(datos),
            longArrayOf(1, 3, LADO.toLong(), LADO.toLong()),
        )
    }

    /**
     * Vocabulario cargado. Se expone para poder comprobar en una prueba que los
     * cuatro especiales están donde el modelo cree que están: si el archivo no
     * se empaquetó, esta lista está vacía y `leer` lo dice.
     */
    fun vocabularioCargado(): List<String> = vocabulario

    fun cerrar() {
        codificador?.close()
        decodificador?.close()
        codificador = null
        decodificador = null
    }
}
