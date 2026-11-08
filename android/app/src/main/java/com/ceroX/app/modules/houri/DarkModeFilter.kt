package com.ceroX.app.modules.houri

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View

/**
 * DarkModeFilter — Filtro oscuro inteligente con AMOLED subtoggle.
 * Basado en PineappleTwilight/houri dark-mode filter.
 */
class DarkModeFilter {

    private var isEnabled = false
    private var amoledSubtoggle = false
    private val paint = Paint()

    fun setEnabled(enabled: Boolean, amoled: Boolean = false) {
        isEnabled = enabled
        amoledSubtoggle = amoled
    }

    fun applyTo(view: View) {
        if (!isEnabled) {
            view.layerType = View.LAYER_TYPE_NONE
            return
        }

        view.layerType = View.LAYER_TYPE_HARDWARE

        val matrix = ColorMatrix()
        if (amoledSubtoggle) {
            // AMOLED: negros profundos, contraste alto
            matrix.setScale(0.6f, 0.6f, 0.6f, 1.0f)
        } else {
            // Dark mode estándar
            matrix.setScale(0.8f, 0.8f, 0.8f, 1.0f)
        }

        paint.colorFilter = ColorMatrixColorFilter(matrix)
        view.layerType = View.LAYER_TYPE_HARDWARE
        view.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }

    fun isActive(): Boolean = isEnabled
    fun isAmoled(): Boolean = amoledSubtoggle
}