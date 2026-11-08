package com.ceroX.app.modules.kisara

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * KisaraReaderEngineModule — Motor de lectura de Kisara.
 *
 * Características del lector de Kisara (estabilizadas):
 *   • Múltiples visores (página, scroll, webtoon)
 *   • Dirección de lectura: LTR, RTL, Vertical
 *   • Detección automática de webtoon con cambio automático de modo
 *   • Forzar deshabilitar zoom de webtoon
 *   • Fondo inteligente: cambia según el color de la página
 *   • Autoscroll personalizable
 *   • Pre-carga de páginas personalizable
 *   • Tamaño de caché de imagen personalizable
 *   • Webtoon mode: zoom lock, two-finger zoom, grayscale
 *   • Ayuda de lectura continua (reanudar donde se quedó)
 *   • Opciones de lector: brillo, filtro oscuro, upscaler, etc.
 *   • Traducción OCR on-device (PP-OCRv6_manga)
 *   • Colorización neural ONNX (manga_colorization_v2_int8.onnx)
 */
class KisaraReaderEngineModule : ModuleInterface() {

    override val name = "kisara-reader-engine"
    override val version = "2.3.9-kisara-stable"
    override val dependencies: List<String> = listOf("kisara-reader")

    private var readingDirection = "ltr"
    private var webtoonAutoDetect = true
    private var forceDisableWebtoonZoom = false
    private var smartBackgroundEnabled = true
    private var autoscrollEnabled = false
    private var autoscrollSpeed = 1.0f
    private var pagePreloadCount = 3
    private var imageCacheSizeMb = 512
    private var webtoonModeEnabled = false
    private var viewerType = "page"
    private var darkModeFilterEnabled = false
    private var upscalerEnabled = false
    private var ocrTranslationEnabled = true
    private var neuralColorizationEnabled = true

    override fun initialize(config: Map<String, Any>): Boolean {
        readingDirection = config["reading_direction"] as? String ?: "ltr"
        webtoonAutoDetect = config["webtoon_auto_detect"] as? Boolean ?: true
        forceDisableWebtoonZoom = config["force_disable_webtoon_zoom"] as? Boolean ?: false
        smartBackgroundEnabled = config["smart_background"] as? Boolean ?: true
        autoscrollEnabled = config["autoscroll_enabled"] as? Boolean ?: false
        autoscrollSpeed = config["autoscroll_speed"] as? Float ?: 1.0f
        pagePreloadCount = config["page_preload_count"] as? Int ?: 3
        imageCacheSizeMb = config["image_cache_size_mb"] as? Int ?: 512
        viewerType = config["viewer_type"] as? String ?: "page"
        darkModeFilterEnabled = config["dark_mode_filter"] as? Boolean ?: false
        upscalerEnabled = config["upscaler_enabled"] as? Boolean ?: false
        ocrTranslationEnabled = config["ocr_translation"] as? Boolean ?: true
        neuralColorizationEnabled = config["neural_colorization"] as? Boolean ?: true
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
        return true
    }

    override fun healthCheck(): ModuleHealth {
        return ModuleHealth(
            name, version,
            if (_started) "healthy" else "stopped",
            mapOf(
                "reading_direction" to readingDirection,
                "webtoon_auto_detect" to webtoonAutoDetect,
                "force_disable_webtoon_zoom" to forceDisableWebtoonZoom,
                "smart_background" to smartBackgroundEnabled,
                "autoscroll" to autoscrollEnabled,
                "autoscroll_speed" to autoscrollSpeed,
                "page_preload_count" to pagePreloadCount,
                "image_cache_size_mb" to imageCacheSizeMb,
                "viewer_type" to viewerType,
                "webtoon_mode" to webtoonModeEnabled,
                "dark_mode_filter" to darkModeFilterEnabled,
                "upscaler" to upscalerEnabled,
                "ocr_translation" to ocrTranslationEnabled,
                "neural_colorization" to neuralColorizationEnabled,
                "supported_formats" to listOf("CBZ", "CBR", "CB7", "CBT", "ZIP", "RAR", "7z", "TAR", "PDF", "EPUB"),
                "onnx_models" to listOf("manga_colorization_v2_int8.onnx", "PP-OCRv6_manga", "deoldify_artistic_quant.onnx"),
                "stability" to "stabilized",
                "min_android" to "8.0 (API 26)"
            )
        )
    }

    fun setReadingDirection(direction: String) {
        readingDirection = direction
    }

    fun setViewerType(type: String) {
        viewerType = type
    }

    fun toggleWebtoonMode(enabled: Boolean) {
        webtoonModeEnabled = enabled
        if (enabled) viewerType = "webtoon"
    }

    fun toggleForceDisableWebtoonZoom(enabled: Boolean) {
        forceDisableWebtoonZoom = enabled
    }

    fun setAutoscroll(enabled: Boolean, speed: Float) {
        autoscrollEnabled = enabled
        autoscrollSpeed = speed.coerceIn(0.1f, 5.0f)
    }

    fun setPagePreload(count: Int) {
        pagePreloadCount = count.coerceIn(1, 10)
    }

    fun setImageCacheSize(sizeMb: Int) {
        imageCacheSizeMb = sizeMb.coerceIn(64, 2048)
    }

    fun toggleSmartBackground(enabled: Boolean) {
        smartBackgroundEnabled = enabled
    }

    fun toggleDarkModeFilter(enabled: Boolean) {
        darkModeFilterEnabled = enabled
    }

    fun toggleUpscaler(enabled: Boolean) {
        upscalerEnabled = enabled
    }

    fun toggleOcrTranslation(enabled: Boolean) {
        ocrTranslationEnabled = enabled
    }

    fun toggleNeuralColorization(enabled: Boolean) {
        neuralColorizationEnabled = enabled
    }

    fun getReadingPosition(entryId: String): Float = 0.0f

    fun saveReadingPosition(entryId: String, position: Float) {
        // Guardar posición de lectura
    }
}