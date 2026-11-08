package com.ceroX.app.modules.hardware_monitor

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs

/**
 * HardwareMonitor — Telemetría de CPU/GPU/RAM en tiempo real.
 * Basado en Kisara hardware monitoring.
 */
class HardwareMonitor(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun getCpuUsage(): Float {
        // Placeholder: lectura real de /proc/stat
        return 0.0f
    }

    fun getRamUsage(): RamInfo {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return RamInfo(
            totalBytes = memoryInfo.totalMem,
            availableBytes = memoryInfo.availMem,
            lowMemory = memoryInfo.lowMemory
        )
    }

    fun getStorageInfo(path: String): StorageInfo {
        val statFs = StatFs(path)
        val blockSize = statFs.blockSizeLong
        return StorageInfo(
            totalBytes = statFs.blockCountLong * blockSize,
            availableBytes = statFs.availableBlocksLong * blockSize
        )
    }

    fun getDeviceTemperature(): Float? {
        // Placeholder: lectura de sensores térmicos
        return null
    }

    fun getScreenDimensions(): ScreenDimensions {
        // Display.getSize() y WindowManager.getDefaultDisplay() estan obsoletos
        // desde API 30 y ya no existen en el SDK 36: compilar contra ellos da
        // "unresolved reference". La via vigente es WindowMetrics, disponible
        // desde API 30, con un camino de respaldo para API 26-29.
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
            ?: return ScreenDimensions(0, 0)

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            ScreenDimensions(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val display = wm.defaultDisplay
            val punto = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getSize(punto)
            ScreenDimensions(punto.x, punto.y)
        }
    }

    fun getFullStats(): HardwareStats {
        val ram = getRamUsage()
        // Se pregunta por el volumen de datos de la app, que es el que le importa
        // al usuario, pero se pasa la **ruta** sin exponer un File escribible: esta
        // función solo consulta StatFs y podría recibir cualquier ruta.
        val storage = getStorageInfo(context.applicationInfo.dataDir)
        val screen = getScreenDimensions()

        return HardwareStats(
            cpuPercent = getCpuUsage(),
            ramTotalBytes = ram.totalBytes,
            ramAvailableBytes = ram.availableBytes,
            ramLowMemory = ram.lowMemory,
            storageTotalBytes = storage.totalBytes,
            storageAvailableBytes = storage.availableBytes,
            screenWidth = screen.width,
            screenHeight = screen.height,
            temperatureCelsius = getDeviceTemperature()
        )
    }
}

data class RamInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val lowMemory: Boolean
)

data class StorageInfo(
    val totalBytes: Long,
    val availableBytes: Long
)

data class ScreenDimensions(
    val width: Int,
    val height: Int
)

data class HardwareStats(
    val cpuPercent: Float,
    val ramTotalBytes: Long,
    val ramAvailableBytes: Long,
    val ramLowMemory: Boolean,
    val storageTotalBytes: Long,
    val storageAvailableBytes: Long,
    val screenWidth: Int,
    val screenHeight: Int,
    val temperatureCelsius: Float?
)