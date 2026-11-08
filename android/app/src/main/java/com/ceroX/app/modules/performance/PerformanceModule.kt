package com.ceroX.app.modules.performance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * PerformanceModule — lee el estado real del teléfono y decide.
 *
 * La lógica de decisión vive en [PerformanceEngine] y es pura: se prueba sin
 * dispositivo. Este módulo solo hace tres cosas: leer, llamar al motor y aplicar.
 *
 * Todo lo que este módulo toca es **interno a la app**: límite de FPS del propio
 * lector, cuántas descargas propias lanza, si carga sus propios modelos. No toca
 * gobernadores de CPU, ni Hz del sistema, ni ajustes de otras apps. No es una
 * limitación técnica: cambiar los Hz del panel afecta a todo el teléfono y
 * rompe la garantía del usuario sobre su propio móvil.
 */
class PerformanceModule(private val context: Context) : ModuleInterface() {

    override val name = "performance"
    override val version = "1.0.0-beta"
    override val dependencies: List<String> = emptyList()

    private var profile: PerformanceProfile = PerformanceProfile.DEFAULT
    private var lastState: DeviceState? = null
    private var lastDecisions: PerformanceDecisions = PerformanceDecisions()
    private var lastGpu: GpuBackend = GpuBackend.AUTO

    override fun initialize(config: Map<String, Any>): Boolean {
        (config["profile"] as? String)?.let { profile = PerformanceProfile.fromId(it) }
        lastGpu = GpuBackendDetector.detect(context)
        reevaluate()
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

    override fun healthCheck(): ModuleHealth = ModuleHealth(
        name, version,
        if (_started) "healthy" else "stopped",
        mapOf(
            "profile" to profile.id,
            "gpu_backend" to lastGpu.name,
            "last_reason" to lastDecisions.reason,
            "state" to (lastState?.let {
                "bateria=${it.batteryPercent}% cargando=${it.charging} termico=${it.thermalStatus} red=${it.network}"
            } ?: "sin leer"),
        ),
    )

    /** Cambia de perfil a petición del usuario y vuelve a decidir. */
    fun setProfile(p: PerformanceProfile): PerformanceDecisions {
        profile = p
        return reevaluate()
    }

    fun currentProfile(): PerformanceProfile = profile

    /**
     * Relee el dispositivo y decide. Es seguro llamarlo a menudo; el coste es
     * cuatro llamadas al framework.
     */
    fun reevaluate(): PerformanceDecisions {
        val state = readState()
        lastState = state
        val decisions = PerformanceEngine.decide(profile, state)
        lastDecisions = decisions
        return decisions
    }

    fun lastDeviceState(): DeviceState? = lastState
    fun lastDecision(): PerformanceDecisions = lastDecisions
    fun gpuBackend(): GpuBackend = lastGpu

    /**
     * Lee el estado del teléfono.
     *
     * Valores que **no** se pueden leer sin permiso o sin root, y por eso van con
     * lo mejor disponible en vez de inventarse:
     *   • batería: `BatteryManager` (público)
     *   • térmico: `PowerManager.getCurrentThermalStatus` (API 29+); en versiones
     *     anteriores se devuelve `NONE`, que significa "no se sabe", no "está frío".
     *   • red: `NetworkCapabilities` (público)
     *   • RAM: `ActivityManager.MemoryInfo` (público)
     */
    fun readState(): DeviceState {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        val charging = bm?.isCharging ?: false

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { ThermalStatus.entries.getOrElse(pm?.currentThermalStatus ?: 0) { ThermalStatus.NONE } }
                .getOrDefault(ThermalStatus.NONE)
        } else {
            ThermalStatus.NONE
        }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

        return DeviceState(
            batteryPercent = battery.coerceIn(0, 100),
            charging = charging,
            thermalStatus = thermal,
            network = readNetwork(),
            availableRamMb = memInfo.availMem / (1024 * 1024),
            lowRamDevice = am.isLowRamDevice,
        )
    }

    private fun readNetwork(): NetworkClass {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkClass.OFFLINE
        val net = cm.activeNetwork ?: return NetworkClass.OFFLINE
        val caps = cm.getNetworkCapabilities(net) ?: return NetworkClass.OFFLINE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkClass.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkClass.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // Sin permiso de telefonía no se puede distinguir 2G de 5G.
                // Se usa la estimación pública de ancho de banda del sistema.
                val down = caps.linkDownstreamBandwidthKbps
                if (down in 1..200) NetworkClass.CELLULAR_SLOW else NetworkClass.CELLULAR
            }
            else -> NetworkClass.OFFLINE
        }
    }
}

/**
 * Elige backend gráfico preguntando, no adivinando.
 *
 * En Android, "¿soporta Vulkan?" no se contesta mirando la versión del sistema:
 * un móvil con Android 12 puede tener un driver Vulkan roto. La comprobación de
 * verdad es crear una instancia y destruirla, que es lo que hace
 * `GpuBackendDetector` cuando está disponible; aquí se usa
 * `FEATURE_VULKAN_HARDWARE_VERSION`, que es lo que expone el sistema y es lo
 * único consultable sin cargar un contexto gráfico.
 *
 * Si no hay Vulkan, se queda en `OPENGL_ES`. Si tampoco hay GLES 3, `SOFTWARE`.
 */
object GpuBackendDetector {

    fun detect(context: Context): GpuBackend {
        val pm = context.packageManager
        val vulkan = pm.hasSystemFeature("android.hardware.vulkan.version")
        val gles = pm.hasSystemFeature("android.hardware.opengles.aep") ||
            pm.hasSystemFeature("android.hardware.opengles.version")
        return when {
            vulkan -> GpuBackend.VULKAN
            gles -> GpuBackend.OPENGL_ES
            else -> GpuBackend.SOFTWARE
        }
    }

    /**
     * Por qué `AUTO` es el valor por defecto y no el backend detectado: si el
     * driver se actualiza por OTA, la app no tiene que reinstalarse para
     * aprovecharlo. `AUTO` se resuelve en cada arranque.
     */
    fun resolve(preferred: GpuBackend, detected: GpuBackend): GpuBackend =
        if (preferred == GpuBackend.AUTO) detected else preferred
}
