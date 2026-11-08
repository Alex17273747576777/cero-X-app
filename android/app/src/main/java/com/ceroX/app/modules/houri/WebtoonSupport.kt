package com.ceroX.app.modules.houri

/**
 * WebtoonSupport — Soporte webtoon con zoom lock, two-finger zoom, grayscale filter.
 * Basado en PineappleTwilight/houri webtoon support.
 */
class WebtoonSupport {

    private var isEnabled = false
    private var zoomLocked = false
    private var twoFingerZoom = false
    private var grayscaleFilter = false
    private var continuousScroll = true

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun setZoomLocked(locked: Boolean) {
        zoomLocked = locked
    }

    fun setTwoFingerZoom(enabled: Boolean) {
        twoFingerZoom = enabled
    }

    fun setGrayscaleFilter(enabled: Boolean) {
        grayscaleFilter = enabled
    }

    fun setContinuousScroll(enabled: Boolean) {
        continuousScroll = enabled
    }

    fun getStatus(): Map<String, Any> {
        return mapOf(
            "enabled" to isEnabled,
            "zoom_locked" to zoomLocked,
            "two_finger_zoom" to twoFingerZoom,
            "grayscale_filter" to grayscaleFilter,
            "continuous_scroll" to continuousScroll
        )
    }
}