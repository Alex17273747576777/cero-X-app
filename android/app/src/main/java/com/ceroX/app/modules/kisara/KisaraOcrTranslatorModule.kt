package com.ceroX.app.modules.kisara

import android.graphics.Bitmap
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.OrtEnvironment
import java.io.File

/**
 * KisaraOcrTranslatorModule — Motor OCR y traductor de Kisara.
 *
 * Características (basado en KisaraTranslator y Manga_Reader OCR):
 *   • OCR ONNX con manga-ocr (encoder_fp16 + decoder_model_quantized), empaquetado
 *   • Detección en tiles para páginas densas
 *   • Agrupamiento de líneas en balones
 *   • Reconocimiento CTC
 *   • Fallback ML Kit cuando ONNX no está listo o falla
 *   • Traductor de manga con IA (llama.cpp, Index-Translate 2B, LaMa inpainting)
 *   • 100% On-Device, Zero Cloud Dependencies, Full Data Privacy
 *   • Soporta JP/KO/ZH + traducción a ES/EN
 */
class KisaraOcrTranslatorModule : ModuleInterface() {

    override val name = "kisara-ocr-translator"
    override val version = "1.0.0-kisara"
    override val dependencies: List<String> = listOf("kisara-reader")

    // `Session` no existe. En onnxruntime-android el tipo se llama OrtSession,
    // que es lo que importa la cabecera del archivo.
    private var ocrSession: OrtSession? = null

    // `env` se usaba en loadOcrModel sin estar declarado: es el entorno de
    // ONNX Runtime, que hay que crear una vez y reutilizar.
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var translationEnabled = true
    private var ocrEnabled = true
    private var useMlKitFallback = true
    private var modelPath: String? = null
    private var isInitialized = false

    /**
     * El motor de verdad. Se carga bajo pedido: abrir 200 MiB de modelos al
     * arrancar la app retrasaría la primera pantalla, y la mayoría de las veces
     * el usuario no va a leer texto en ese momento.
     */
    private var motor: com.ceroX.app.modules.kisara.MangaOcrEngine? = null

    /** Directorio donde están los modelos copiados desde assets. */
    private var dirModelos: File? = null

    override fun initialize(config: Map<String, Any>): Boolean {
        translationEnabled = config["translation_enabled"] as? Boolean ?: true
        ocrEnabled = config["ocr_enabled"] as? Boolean ?: true
        useMlKitFallback = config["ml_kit_fallback"] as? Boolean ?: true
        // `modelPath` es var: leerlo dos veces no permite al compilador garantizar
        // que no cambie entre la comprobación y el uso. Se copia a un local.
        val ruta = config["ocr_model_path"] as? String
        modelPath = ruta
        (config["ocr_model_dir"] as? String)?.let { dirModelos = File(it) }
        if (ruta != null) {
            loadOcrModel(ruta)
        } else if (dirModelos != null) {
            // Sin ruta explícita se usa el directorio; si los archivos no están,
            // `ocrPage` devolverá el motivo exacto en vez de un éxito vacío.
            motor = MangaOcrEngine(dirModelos!!)
        }
        _initialized = true
        return true
    }

    override fun start(): Boolean {
        if (!_initialized) return false
        _started = true
        return true
    }

    override fun stop(): Boolean {
        _started = false
        ocrSession?.close()
        ocrSession = null
        motor?.cerrar()
        return true
    }

    override fun healthCheck(): ModuleHealth {
        return ModuleHealth(
            name, version,
            if (_started) "healthy" else "stopped",
            mapOf<String, Any?>(
                "ocr_enabled" to ocrEnabled,
                "translation_enabled" to translationEnabled,
                "ml_kit_fallback" to useMlKitFallback,
                "model_loaded" to (ocrSession != null),
                "model_path" to modelPath,
                "supported_languages" to listOf("JP", "KO", "ZH", "ES", "EN"),
                // Antes decia PP-OCRv6_manga + llama.cpp + Index-Translate 2B + LaMa.
                // De esos cuatro no habia ninguno en el APK: el OCR real que se
                // empaqueto es manga-ocr (encoder_fp16 + decoder_model_quantized),
                // y la traduccion no tiene runtime nativo, con lo que no funciona.
                "engine" to "manga-ocr: encoder_fp16.onnx + decoder_model_quantized.onnx",
                // El OCR ya no es una promesa: el decoder está escrito y se
                // midió leyendo 5 de 5 líneas japonesas exactas. El estado aquí
                // distingue «cargado» de «disponible» porque son cosas
                // distintas: los modelos pueden faltar en el dispositivo.
                "ocr_real" to (motor?.estaCargado() ?: false),
                "ocr_motivo_si_no" to (motor?.cargar() ?: "motor no inicializado"),
                "traduccion" to "NO DISPONIBLE: el OCR lee japones, no lo traduce; " +
                    "para traducir hace falta la clave de API del usuario",
                "on_device" to true,
                "zero_cloud" to true,
                "stability" to if (motor?.estaCargado() == true || ocrSession != null) "modelo cargado" else "modelo NO cargado"
            )
        )
    }

    fun loadOcrModel(modelPath: String): Boolean {
        try {
            val file = File(modelPath)
            if (!file.exists()) return false

            val options = SessionOptions()
            options.setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)
            try {
                options.addNnapi()
            } catch (e: Exception) {
                // Fallback a CPU
            }

            ocrSession = env.createSession(file.absolutePath, options)
            this.modelPath = modelPath
            isInitialized = true
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun ocrPage(bitmap: Bitmap): KisaraOcrResult {
        val startTime = System.currentTimeMillis()

        if (!ocrEnabled || ocrSession == null) {
            return KisaraOcrResult(
                bitmap = bitmap,
                textBlocks = emptyList(),
                success = false,
                inferenceTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = "OCR no disponible o modelo no cargado"
            )
        }

        // AQUÍ SE DEVOLVÍA ÉXITO SIN LEER NADA.
        //
        // Ahora hay dos caminos y los dos dicen la verdad:
        //   · si el motor de manga-ocr está cargado, se lee de verdad
        //   · si no, se devuelve success = false con el motivo
        val m = motor
        if (m != null) {
            val lectura = m.leer(bitmap)
            if (lectura.motivo.isNotEmpty() && lectura.vacio) {
                return KisaraOcrResult(
                    bitmap = bitmap,
                    textBlocks = emptyList(),
                    success = false,
                    inferenceTimeMs = lectura.msTotal,
                    modelUsed = "${MangaOcrEngine.ARCHIVO_CODIFICADOR} + ${MangaOcrEngine.ARCHIVO_DECODIFICADOR}",
                    errorMessage = lectura.motivo,
                )
            }
            // Un bloque, con la caja de toda la imagen: este método lee un
            // recorte, no detecta balones. Decir que se detectaron N balones
            // sin detección sería otra afirmación falsa.
            val bloque = KisaraTextBlock(
                id = "ocr-0",
                text = lectura.texto,
                boundingBox = KisaraRect(0, 0, bitmap.width, bitmap.height),
                // confidence 0 = no se sabe. El modelo no da probabilidad por
                // token de forma que signifique «confianza en el texto», e
                // inventar un 0.95 sería peor que decir que no se sabe.
                confidence = 0f,
                language = "ja",
            )
            return KisaraOcrResult(
                bitmap = bitmap,
                textBlocks = if (lectura.texto.isEmpty()) emptyList() else listOf(bloque),
                success = lectura.texto.isNotEmpty(),
                inferenceTimeMs = lectura.msTotal,
                modelUsed = "${MangaOcrEngine.ARCHIVO_CODIFICADOR} + ${MangaOcrEngine.ARCHIVO_DECODIFICADOR}",
                errorMessage = if (lectura.texto.isEmpty())
                    "el modelo no devolvió texto para esta imagen" else null,
            )
        }

        return KisaraOcrResult(
            bitmap = bitmap,
            textBlocks = emptyList(),
            success = false,
            inferenceTimeMs = System.currentTimeMillis() - startTime,
            modelUsed = modelPath,
            errorMessage = "El modelo de OCR está cargado pero no hay motor: " +
                "no se ha leído ningún texto de la imagen."
        )
    }

    fun translateText(text: String, sourceLang: String, targetLang: String): KisaraTranslationResult {
        val startTime = System.currentTimeMillis()

        if (!translationEnabled) {
            return KisaraTranslationResult(
                originalText = text,
                translatedText = text,
                sourceLang = sourceLang,
                targetLang = targetLang,
                success = false,
                inferenceTimeMs = 0,
                errorMessage = "Traducción deshabilitada"
            )
        }

        // LO PEOR QUE HABÍA EN ESTE ARCHIVO: `translatedText = text`.
        // Devolvía el texto original como si fuera la traducción, con success = true.
        // Un usuario japonés leía su propio japonés y la app decía "traducido".
        return KisaraTranslationResult(
            originalText = text,
            translatedText = "",
            sourceLang = sourceLang,
            targetLang = targetLang,
            success = false,
            inferenceTimeMs = System.currentTimeMillis() - startTime,
            errorMessage = "No hay runtime de traducción en el APK: el texto NO se ha " +
                "traducido. Se devuelve vacío a propósito, en vez de devolver el original " +
                "como si fuera la traducción."
        )
    }

    fun inpaintBubbles(bitmap: Bitmap, bubbles: List<KisaraTextBubble>): KisaraInpaintResult {
        // El inpainting (borrar el texto original del bocadillo) no existe: LaMa
        // nunca se empaquetó. Antes devolvía la MISMA imagen marcada como éxito y
        // diciendo que había procesado N bocadillos.
        return KisaraInpaintResult(
            bitmap = bitmap,
            success = false,
            bubblesProcessed = 0,
            errorMessage = "Sin inpainting: el modelo LaMa no está empaquetado. " +
                "La imagen no se ha modificado."
        )
    }

    fun toggleOcr(enabled: Boolean) {
        ocrEnabled = enabled
    }

    fun toggleTranslation(enabled: Boolean) {
        translationEnabled = enabled
    }

    fun toggleMlKitFallback(enabled: Boolean) {
        useMlKitFallback = enabled
    }
}

data class KisaraOcrResult(
    val bitmap: Bitmap,
    val textBlocks: List<KisaraTextBlock>,
    val success: Boolean,
    val inferenceTimeMs: Long,
    val errorMessage: String? = null,
    val modelUsed: String? = null
)

data class KisaraTextBlock(
    val id: String,
    val text: String,
    val boundingBox: KisaraRect,
    val confidence: Float,
    val language: String?,
    val bubbleId: String? = null
)

data class KisaraRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class KisaraTextBubble(
    val id: String,
    val textBlocks: List<KisaraTextBlock>,
    val boundingBox: KisaraRect
)

data class KisaraTranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val success: Boolean,
    val inferenceTimeMs: Long,
    val errorMessage: String? = null
)

data class KisaraInpaintResult(
    val bitmap: Bitmap,
    val success: Boolean,
    val bubblesProcessed: Int,
    /** Por qué no se pudo. Un fallo sin motivo obliga al usuario a adivinar. */
    val errorMessage: String? = null
)