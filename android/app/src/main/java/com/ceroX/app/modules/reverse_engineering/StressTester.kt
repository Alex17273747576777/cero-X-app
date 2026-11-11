package com.ceroX.app.modules.reverse_engineering

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * StressTester — Pruebas de estrés al extremo.
 * 6 escenarios: biblioteca masiva, descargas concurrentes, IA inferencia,
 * extensiones múltiples, estrés térmico, compatibilidad 8-16.
 */
class StressTester(private val termometro: (() -> Float?)? = null) {

    private val executor = Executors.newFixedThreadPool(10)

    /**
     * No se mide ninguna carga: este probador no tiene biblioteca conectada.
     *
     * El cuerpo era un bucle vacio de 1 a `seriesCount` y el resultado decia
     * "Cargadas N series en X ms", con X siendo lo que tarda un bucle que no hace
     * nada. Se devuelve el fallo y el motivo.
     */
    fun runLibraryStressTest(seriesCount: Int): LibraryStressResult {
        return LibraryStressResult(
            seriesCount = 0,
            loadTimeMs = 0,
            passed = false,
            details = "No se cargo ninguna serie: StressTester no tiene biblioteca conectada (se pidieron $seriesCount)"
        )
    }

    /**
     * Mide de verdad el pool de hilos, y lo dice sin llamarlo descarga.
     *
     * Cada tarea espera 100 ms: es trabajo de reloj, no de red. Lo que se mide es
     * cuantas tareas concurrentes completa el pool y en cuanto tiempo, que es lo
     * unico que este probador puede medir sin un descargador conectado.
     */
    fun runDownloadStressTest(concurrentDownloads: Int): DownloadStressResult {
        val startTime = System.currentTimeMillis()

        val futures = (1..concurrentDownloads).map {
            executor.submit {
                Thread.sleep(100)
                true
            }
        }

        val results = futures.map { it.get() }
        val totalTime = System.currentTimeMillis() - startTime
        val successCount = results.count { it }

        return DownloadStressResult(
            concurrentDownloads = concurrentDownloads,
            successCount = successCount,
            totalTimeMs = totalTime,
            passed = successCount == concurrentDownloads,
            details = "$successCount/$concurrentDownloads tareas concurrentes de 100 ms en $totalTime ms (no son descargas reales)"
        )
    }

    /**
     * Sin modelo no hay inferencia que estresar.
     *
     * El bucle estaba vacio y el resultado daba un tiempo medio por pagina
     * (`totalTime / pageCount`) que salia de no hacer nada: como 0 ms es menor
     * que el umbral de 2000, la prueba "pasaba" siempre.
     */
    fun runAiInferenceStressTest(pageCount: Int): AIStressResult {
        return AIStressResult(
            pageCount = 0,
            totalTimeMs = 0,
            avgTimePerPageMs = 0f,
            passed = false,
            details = "No se ejecuto ninguna inferencia: este probador no tiene un modelo ONNX conectado (se pidieron $pageCount paginas)"
        )
    }

    /**
     * No se cuentan extensiones: hace falta el contador del sandbox.
     *
     * Devolvía el propio parametro como si fuera una medida, y `passed` salia de
     * comparar con el reloj el tiempo que tarda un metodo que no hace nada.
     */
    fun runExtensionStressTest(extensionCount: Int): ExtensionStressResult {
        return ExtensionStressResult(
            extensionCount = 0,
            totalTimeMs = 0,
            passed = false,
            details = "No se conto ninguna extension real: hace falta el contador del sandbox (se pidieron $extensionCount)"
        )
    }

    /**
     * Muestra la temperatura tres segundos, o dice que no hay sensor.
     *
     * Dos cosas estaban mal. Sin termometro el bucle dormia la duracion pedida y
     * devolvía `passed = true` con la temperatura en null: dos horas de hilo
     * bloqueado para no medir nada. Y con `throttlingDetected` en false fijo,
     * `passed` era siempre true.
     *
     * Aqui se muestrea tres segundos y el resultado dice cuantas lecturas hubo.
     * El throttling no se puede saber sin una carga sostenida, así que no se
     * afirma y `passed` depende de haber leido algo, no de ese campo.
     */
    fun runThermalStressTest(durationMinutes: Int): ThermalStressResult {
        val sensor = termometro
            ?: return ThermalStressResult(
                durationMinutes = 0,
                maxTemperatureCelsius = null,
                throttlingDetected = false,
                passed = false,
                details = "No se midio temperatura: no hay sensor conectado a este probador"
            )

        val t0 = System.currentTimeMillis()
        var maxima: Float? = null
        var muestras = 0
        while (System.currentTimeMillis() - t0 < 3000L) {
            val lectura = sensor()
            if (lectura != null) {
                muestras++
                if (maxima == null || lectura > maxima) maxima = lectura
            }
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                break
            }
        }

        return ThermalStressResult(
            durationMinutes = 0,
            maxTemperatureCelsius = maxima,
            throttlingDetected = false,
            passed = muestras > 0,
            details = if (muestras == 0) {
                "El sensor no dio ninguna lectura en 3 s"
            } else {
                "Muestra de 3 s: $muestras lecturas, maxima $maxima C. Throttling sin medir y no son los $durationMinutes min pedidos"
            }
        )
    }

    /**
     * Un dispositivo no puede probar otros niveles de API.
     *
     * Se marcaba `true` para cada nivel pedido y el resultado decia
     * "Compatibilidad verificada en N niveles". Eso lo hace el CI con emuladores,
     * no una app en ejecucion.
     */
    fun runCompatibilityStressTest(apiLevels: List<Int>): CompatibilityStressResult {
        return CompatibilityStressResult(
            apiLevels = apiLevels,
            results = emptyMap(),
            passed = false,
            details = "No se probo ningun nivel de API: se pidieron ${apiLevels.size} y este dispositivo solo puede informar del suyo (${android.os.Build.VERSION.SDK_INT})"
        )
    }

    fun shutdown() {
        executor.shutdown()
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
    }
}

data class LibraryStressResult(
    val seriesCount: Int,
    val loadTimeMs: Long,
    val passed: Boolean,
    val details: String
)

data class DownloadStressResult(
    val concurrentDownloads: Int,
    val successCount: Int,
    val totalTimeMs: Long,
    val passed: Boolean,
    val details: String
)

data class AIStressResult(
    val pageCount: Int,
    val totalTimeMs: Long,
    val avgTimePerPageMs: Float,
    val passed: Boolean,
    val details: String
)

data class ExtensionStressResult(
    val extensionCount: Int,
    val totalTimeMs: Long,
    val passed: Boolean,
    val details: String
)

data class ThermalStressResult(
    val durationMinutes: Int,
    val maxTemperatureCelsius: Float?,
    val throttlingDetected: Boolean,
    val passed: Boolean,
    val details: String
)

data class CompatibilityStressResult(
    val apiLevels: List<Int>,
    val results: Map<Int, Boolean>,
    val passed: Boolean,
    val details: String
)