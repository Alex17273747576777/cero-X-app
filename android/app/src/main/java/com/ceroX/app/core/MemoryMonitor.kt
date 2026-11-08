package com.ceroX.app.core

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlin.concurrent.thread

/**
 * MemoryMonitor — Monitor de memoria del sistema y de la app.
 *
 * Ejecuta en segundo plano y notifica a ModuleLifecycleManager cuando
 * hay presión de memoria para que los módulos puedan liberar recursos.
 *
 * Umbrales:
 *   • CRITICAL: < 15% memoria disponible → onLowMemory
 *   • WARNING:  < 30% memoria disponible → onTrimMemory(TRIM_MEMORY_RUNNING_LOW)
 *   • NORMAL:   >= 30% → sin acción
 */
class MemoryMonitor(private val context: Context, private val lifecycleManager: com.ceroX.app.core.engine.ModuleLifecycleManager) {

    private val TAG = "MemoryMonitor"
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private var isRunning = false
    private var monitorThread: Thread? = null
    private var checkIntervalMs = 10000 // 10 segundos

    private val memoryClass = activityManager.memoryClass // MB

    fun start() {
        if (isRunning) return
        isRunning = true
        monitorThread = thread(isDaemon = true, name = "MemoryMonitor") {
            while (isRunning) {
                try {
                    checkMemory()
                    Thread.sleep(checkIntervalMs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error en monitor de memoria", e)
                }
            }
        }
        Log.i(TAG, "MemoryMonitor iniciado (clase de memoria: $memoryClass MB)")
    }

    fun stop() {
        isRunning = false
        monitorThread?.interrupt()
        Log.i(TAG, "MemoryMonitor detenido")
    }

    private fun checkMemory() {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val availableMb = memoryInfo.availMem / (1024 * 1024)
        val totalMb = memoryInfo.totalMem / (1024 * 1024)
        val availablePercent = if (totalMb > 0) (availableMb.toDouble() / totalMb * 100).toInt() else 100

        when {
            availablePercent < 15 -> {
                Log.w(TAG, "MEMORIA CRÍTICA: $availablePercent% disponible ($availableMb MB)")
                lifecycleManager.onLowMemory()
            }
            availablePercent < 30 -> {
                Log.w(TAG, "MEMORIA BAJA: $availablePercent% disponible ($availableMb MB)")
                lifecycleManager.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
            }
            else -> {
                // Normal, sin acción
            }
        }
    }

    fun setCheckInterval(ms: Long) {
        checkIntervalMs = ms.coerceIn(1000, 300000)
    }

    fun getMemoryInfo(): MemoryInfo {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return MemoryInfo(
            availableMb = memoryInfo.availMem / (1024 * 1024),
            totalMb = memoryInfo.totalMem / (1024 * 1024),
            availablePercent = if (memoryInfo.totalMem > 0) {
                (memoryInfo.availMem.toDouble() / memoryInfo.totalMem * 100).toInt()
            } else 100,
            isLow = memoryInfo.lowMemory,
            memoryClassMb = memoryClass
        )
    }
}

data class MemoryInfo(
    val availableMb: Long,
    val totalMb: Long,
    val availablePercent: Int,
    val isLow: Boolean,
    val memoryClassMb: Int
)