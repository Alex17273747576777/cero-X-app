package com.ceroX.app.modules.houri

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Upscaler — Escalado de imágenes con cache de PNG.
 * Basado en PineappleTwilight/houri upscaler.
 */
class Upscaler {

    private var isEnabled = false
    private var currentModel = "default"
    private val pngCache = mutableMapOf<String, Bitmap>()

    fun setEnabled(enabled: Boolean, model: String = "default") {
        isEnabled = enabled
        currentModel = model
    }

    fun upscale(imagePath: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (!isEnabled) return null

        val cacheKey = "$imagePath:$targetWidth:$targetHeight:$currentModel"
        if (pngCache.containsKey(cacheKey)) {
            return pngCache[cacheKey]
        }

        val file = File(imagePath)
        if (!file.exists()) return null

        val original = BitmapFactory.decodeFile(imagePath)
        if (original == null) return null

        val scaled = Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
        pngCache[cacheKey] = scaled

        // Limpiar cache si excede 50 imágenes
        if (pngCache.size > 50) {
            val firstKey = pngCache.keys.first()
            pngCache.remove(firstKey)
        }

        return scaled
    }

    fun clearCache() {
        pngCache.clear()
    }

    fun getCacheSize(): Int = pngCache.size
    fun getModel(): String = currentModel
}