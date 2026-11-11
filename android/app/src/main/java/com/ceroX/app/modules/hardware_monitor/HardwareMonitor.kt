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

    /** Instantanea anterior de /proc/stat, para medir el intervalo entre llamadas. */
    private var cpuAnterior: Pair<Long, Long>? = null

    /**
     * Porcentaje de CPU entre esta llamada y la anterior, o `null` si no se puede
     * saber. Devolvía `0.0f` fijo, que en pantalla se leía como "CPU al 0 %".
     *
     * La primera llamada devuelve `null` a proposito: un porcentaje hace falta
     * medirlo entre dos instantes. Y en Android 8+ `/proc/stat` suele estar
     * bloqueado por SELinux, así que `null` es lo normal y significa "no se puede
     * leer", no "no hay carga".
     */
    fun getCpuUsage(): Float? {
        val lineas = try {
            java.io.File("/proc/stat").readLines()
        } catch (e: Exception) {
            return null
        }
        val cpu = lineas.firstOrNull { it.startsWith("cpu ") } ?: return null
        val campos = cpu.trim().split(' ').drop(1).filter { it.isNotEmpty() }.mapNotNull { it.toLongOrNull() }
        if (campos.size < 4) return null

        // idle + iowait son el tiempo sin trabajar; el resto es trabajo.
        val ocioso = campos[3] + campos.getOrElse(4) { 0L }
        val total = campos.sum()
        val anterior = cpuAnterior
        cpuAnterior = ocioso to total
        if (anterior == null) return null

        val dTotal = total - anterior.second
        val dOcioso = ocioso - anterior.first
        if (dTotal <= 0L) return null
        return ((dTotal - dOcioso).toFloat() / dTotal.toFloat() * 100f).coerceIn(0f, 100f)
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

    /**
     * Temperatura mas alta de las zonas térmicas legibles, en grados, o `null`.
     *
     * Devolvía `null` siempre, con una nota diciendo que faltaba leer los
     * sensores. Se lee el archivo `temp` de cada `thermal_zone<N>` de
     * `/sys/class/thermal`. Segun el
     * fabricante vienen en grados o en miligrados, y no todas las zonas se pueden
     * leer, así que se descartan los valores que no son una temperatura creíble.
     */
    fun getDeviceTemperature(): Float? {
        val zonas = java.io.File("/sys/class/thermal").listFiles { f ->
            f.isDirectory && f.name.startsWith("thermal_zone")
        } ?: return null

        var maxima: Float? = null
        for (z in zonas.sortedBy { it.name }) {
            val crudo = try {
                java.io.File(z, "temp").readText().trim().toLongOrNull()
            } catch (e: Exception) {
                null
            } ?: continue
            val grados = if (crudo > 1000L) crudo / 1000f else crudo.toFloat()
            if (grados in 0f..150f && (maxima == null || grados > maxima)) maxima = grados
        }
        return maxima
    }

    /**
     * Estado térmico del sistema tal como lo da `PowerManager` (API 29+), o -1 si
     * el dispositivo no lo expone. Son las constantes del sistema, no una escala
     * inventada aqui.
     */
    fun getThermalStatus(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return -1
        return try {
            pm.currentThermalStatus
        } catch (e: Exception) {
            -1
        }
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