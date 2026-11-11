package com.ceroX.app.core

import android.content.Context
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CrashHandler — Manejador global de excepciones no capturadas.
 *
 * Captura cualquier excepción no controlada en la app, genera un reporte
 * detallado con stack trace, información del dispositivo, estado de módulos
 * y lo guarda en almacenamiento privado para análisis posterior.
 *
 * Evita que la app cierre silenciosamente sin dejar rastro del error.
 */
class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val TAG = "CrashHandler"
    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()
    private val installed = AtomicBoolean(false)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    fun install() {
        if (installed.compareAndSet(false, true)) {
            Thread.setDefaultUncaughtExceptionHandler(this)
            Log.i(TAG, "CrashHandler instalado")
        }
    }

    fun uninstall() {
        if (installed.compareAndSet(true, false)) {
            Thread.setDefaultUncaughtExceptionHandler(defaultHandler)
            Log.i(TAG, "CrashHandler desinstalado")
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "EXCEPCIÓN NO CAPTURADA en thread ${thread.name}", throwable)

        val report = generateCrashReport(thread, throwable)
        saveCrashReport(report)

        // Notificar al EventBus si está disponible
        try {
            val app = context.applicationContext as com.ceroX.app.core.CeroXApplication
            // Iba al bus del motor legacy, que no es el que escucha el gestor de
            // módulos: el evento se publicaba y no lo recibía nadie.
            app.lifecycleManager.getEventBus().publish("system.crash", report)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo publicar evento de crash", e)
        }

        // Delegar al handler predeterminado después de guardar el reporte
        if (defaultHandler != null) {
            defaultHandler!!.uncaughtException(thread, throwable)
        } else {
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun generateCrashReport(thread: Thread, throwable: Throwable): CrashReport {
        val writer = StringWriter()
        val printWriter = PrintWriter(writer)
        throwable.printStackTrace(printWriter)

        return CrashReport(
            timestamp = System.currentTimeMillis(),
            date = dateFormat.format(Date()),
            threadName = thread.name,
            threadId = thread.id,
            exceptionClass = throwable::class.java.name,
            exceptionMessage = throwable.message ?: "Sin mensaje",
            stackTrace = writer.toString(),
            deviceModel = android.os.Build.MODEL,
            deviceManufacturer = android.os.Build.MANUFACTURER,
            androidVersion = android.os.Build.VERSION.RELEASE,
            apiLevel = android.os.Build.VERSION.SDK_INT,
            appVersion = "1.0.0-beta-kisara",
            appPackage = context.packageName,
            availableMemoryMb = getAvailableMemoryMb(),
            totalMemoryMb = getTotalMemoryMb()
        )
    }

    private fun saveCrashReport(report: CrashReport) {
        try {
            // Sin caída al almacenamiento interno: un informe de fallo tampoco es
            // un token. Si no hay externo, no se escribe y se dice en el log.
            val base = com.ceroX.app.core.AlmacenExterno.directorio(context)
            if (base == null) {
                android.util.Log.w("ceroX.crash", "sin almacenamiento externo: no se guarda el informe")
                return
            }
            val crashDir = java.io.File(base, "crash_reports")
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }

            val fileName = "crash_${report.date}.json"
            val file = java.io.File(crashDir, fileName)

            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
            val json = gson.toJson(report)

            file.writeText(json)
            Log.i(TAG, "Reporte de crash guardado en ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando reporte de crash", e)
        }
    }

    private fun getAvailableMemoryMb(): Long {
        return Runtime.getRuntime().freeMemory() / (1024 * 1024)
    }

    private fun getTotalMemoryMb(): Long {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024)
    }
}

data class CrashReport(
    val timestamp: Long,
    val date: String,
    val threadName: String,
    val threadId: Long,
    val exceptionClass: String,
    val exceptionMessage: String,
    val stackTrace: String,
    val deviceModel: String,
    val deviceManufacturer: String,
    val androidVersion: String,
    val apiLevel: Int,
    val appVersion: String,
    val appPackage: String,
    val availableMemoryMb: Long,
    val totalMemoryMb: Long
)