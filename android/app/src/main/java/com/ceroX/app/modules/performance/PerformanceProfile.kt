package com.ceroX.app.modules.performance

/**
 * Los cinco perfiles de rendimiento.
 *
 * Elegir perfil es elegir **qué se sacrifica**, porque no se puede tener todo.
 * Cada perfil declara explícitamente qué pierde: si un perfil dice "ahorro" y no
 * dice qué se apaga, no es un perfil, es una etiqueta.
 */
enum class PerformanceProfile(
    val id: String,
    val displayName: String,
    /** Qué gana el usuario. */
    val gains: String,
    /** Qué pierde. Nunca vacío: si no pierde nada, no ahorra nada. */
    val costs: String,
) {
    POWER_SAVER(
        id = "ahorro",
        displayName = "Ahorro de energía",
        gains = "Menos batería por hora de lectura, menos calor",
        costs = "Sin IA de imagen, sin prefetch agresivo, sin aceleración de red. Las páginas tardan más.",
    ),
    BALANCED(
        id = "equilibrio",
        displayName = "Equilibrio",
        gains = "Todo funciona, nada se nota",
        costs = "Ni la mejor calidad ni el menor consumo: los dos a medias.",
    ),
    PERFORMANCE(
        id = "rendimiento",
        displayName = "Rendimiento",
        gains = "Páginas al instante, IA de imagen activa",
        costs = "Batería y temperatura suben. En un móvil de gama media se nota en la mano.",
    ),
    QUALITY(
        id = "calidad",
        displayName = "Calidad",
        gains = "Mejor imagen posible: escalado y color",
        costs = "esrgan_x4 tarda 24,6 s por imagen. No es un modo de lectura, es una espera.",
    ),
    ADAPTIVE(
        id = "adaptativo",
        displayName = "Adaptativo",
        gains = "Cambia solo según batería, temperatura y red",
        costs = "El usuario no controla cuándo cambia. Hay que poder verlo y forzarlo, o es magia opaca.",
    );

    companion object {
        val DEFAULT = ADAPTIVE
        fun fromId(id: String): PerformanceProfile =
            values().firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Estado del dispositivo que el modo adaptativo mira. Todos los campos son medidos. */
data class DeviceState(
    val batteryPercent: Int,
    val charging: Boolean,
    val thermalStatus: ThermalStatus,
    val network: NetworkClass,
    val availableRamMb: Long,
    val lowRamDevice: Boolean,
) {
    init {
        require(batteryPercent in 0..100) { "batería fuera de rango: $batteryPercent" }
        require(availableRamMb >= 0) { "RAM disponible negativa" }
    }
}

/**
 * Estado térmico, en los mismos escalones que reporta Android
 * (`PowerManager.getCurrentThermalStatus`). Se replican para no atar el modelo a
 * una API de framework y poder probarlo sin dispositivo.
 */
enum class ThermalStatus {
    NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN;

    val isThrottling: Boolean get() = this >= MODERATE
}

/**
 * Clase de red, ordenada de peor a mejor.
 *
 * El orden importa: el código compara con `>=`, así que el `ordinal` es
 * semántico. Reordenar este enum cambia el comportamiento, y por eso hay una
 * prueba que lo fija.
 */
enum class NetworkClass {
    OFFLINE, CELLULAR_SLOW, CELLULAR, WIFI, ETHERNET;

    val canPrefetch: Boolean get() = this >= WIFI
    val prefetchPages: Int
        get() = when (this) {
            ETHERNET, WIFI -> 3
            CELLULAR -> 1
            CELLULAR_SLOW, OFFLINE -> 0
        }
}

/**
 * Qué se decide para cada subsistema. Es el contrato entre el perfil y el motor.
 *
 * Todo tiene valor por defecto a propósito: así se puede añadir un campo nuevo
 * sin romper los que ya existen, y cada valor nuevo se ve en la revisión como un
 * cambio de comportamiento, no como un accidente.
 */
data class PerformanceDecisions(
    /** Imagen IA: escalado y color. Null = ninguna. */
    val imageModel: ImagePolicy = ImagePolicy.NONE,
    /** Páginas que se descargan por delante. */
    val prefetchPages: Int = 0,
    /** Descargas simultáneas. Medido: 7 degradaban el hit rate a cero. */
    val concurrentDownloads: Int = 1,
    /** Conexiones paralelas por archivo en el descargador. */
    val networkAcceleration: Boolean = false,
    /** Tope de FPS del lector/reproductor. 0 = sin tope (deja decidir al sistema). */
    val maxFps: Int = 0,
    /** Si se permite bajar la tasa de refresco para ahorrar. */
    val dynamicRefreshRate: Boolean = true,
    /** Backend gráfico preferido. */
    val gpuBackend: GpuBackend = GpuBackend.AUTO,
    /** Si se activa el extractor de recursos GPU. */
    val gpuResourceExtractor: Boolean = false,
    /** Traducción/OCR a demanda. */
    val onDeviceAi: Boolean = true,
    /** Por qué se eligió esto. Se muestra al usuario; no es un comentario. */
    val reason: String = "",
)

enum class ImagePolicy { NONE, COLORIZE, UPSCALE_FAST, UPSCALE_MAX }

/**
 * Backend gráfico.
 *
 * `AUTO` no significa "el mejor": significa que se pregunta al dispositivo. Si
 * el driver de Vulkan falla, se cae a OpenGL, y si OpenGL falla, a software. Una
 * app que elige Vulkan a ciegas se cierra en los móviles con driver roto.
 */
enum class GpuBackend { AUTO, VULKAN, OPENGL_ES, SOFTWARE }

/**
 * El motor. **Es una función pura**: mismo estado de dispositivo + mismo perfil
 * → mismas decisiones. Sin esto no se puede probar, y sin pruebas vuelven las
 * mentiras.
 */
object PerformanceEngine {

    /**
     * Umbrales, en constantes con nombre. Los números mágicos repartidos por el
     * código son la razón de que nadie sepa por qué la app va lenta.
     */
    const val BATTERY_CRITICAL = 15
    const val BATTERY_LOW = 30
    const val RAM_LOW_MB = 512L
    const val RAM_HEALTHY_MB = 1_536L

    fun decide(profile: PerformanceProfile, state: DeviceState): PerformanceDecisions =
        when (profile) {
            PerformanceProfile.POWER_SAVER -> powerSaver(state)
            PerformanceProfile.BALANCED -> balanced(state)
            PerformanceProfile.PERFORMANCE -> performance(state)
            PerformanceProfile.QUALITY -> quality(state)
            PerformanceProfile.ADAPTIVE -> adaptive(state)
        }

    private fun powerSaver(state: DeviceState) = PerformanceDecisions(
        imageModel = ImagePolicy.NONE,
        prefetchPages = if (state.network.canPrefetch) 1 else 0,
        concurrentDownloads = 1,
        networkAcceleration = false,
        maxFps = 30,
        dynamicRefreshRate = true,
        gpuBackend = GpuBackend.AUTO,
        gpuResourceExtractor = false,
        onDeviceAi = false,
        reason = "ahorro: sin IA de imagen, 30 fps, 1 descarga",
    )

    private fun balanced(state: DeviceState) = PerformanceDecisions(
        imageModel = ImagePolicy.COLORIZE,
        prefetchPages = state.network.prefetchPages,
        concurrentDownloads = 1,
        networkAcceleration = state.network >= NetworkClass.WIFI,
        maxFps = 60,
        dynamicRefreshRate = true,
        gpuBackend = GpuBackend.AUTO,
        gpuResourceExtractor = false,
        onDeviceAi = true,
        reason = "equilibrio: color a demanda, prefetch ${state.network.prefetchPages} según red",
    )

    private fun performance(state: DeviceState) = PerformanceDecisions(
        imageModel = ImagePolicy.UPSCALE_FAST,   // anime4x: 2 673 ms, tolerable
        prefetchPages = maxOf(state.network.prefetchPages, 3),
        concurrentDownloads = if (state.network >= NetworkClass.WIFI) 2 else 1,
        networkAcceleration = true,
        maxFps = 120,
        dynamicRefreshRate = false,   // FPS estables: no bajar Hz a media lectura
        gpuBackend = GpuBackend.AUTO,
        gpuResourceExtractor = true,
        onDeviceAi = true,
        reason = "rendimiento: anime4x (2,7 s), prefetch 3, FPS fijos",
    )

    private fun quality(state: DeviceState) = PerformanceDecisions(
        // esrgan_x4 son 24 587 ms medidos. Se declara, no se esconde.
        imageModel = ImagePolicy.UPSCALE_MAX,
        prefetchPages = state.network.prefetchPages,
        concurrentDownloads = 1,
        networkAcceleration = state.network >= NetworkClass.WIFI,
        maxFps = 60,
        dynamicRefreshRate = false,
        gpuBackend = GpuBackend.AUTO,
        gpuResourceExtractor = false,
        onDeviceAi = true,
        reason = "calidad: esrgan_x4 a 24,6 s por imagen. Se avisa antes de activarlo.",
    )

    /**
     * El adaptativo: coge el estado y decide como si fuera otro perfil.
     *
     * Orden de prioridad, y es deliberado:
     *   1. Térmico severo → ahorro, sin discusión. Un móvil a 45 °C va a bajar
     *      Hz solo, mejor hacerlo antes de que se note como tirones.
     *   2. Batería crítica y sin cargar → ahorro.
     *   3. Cargando + térmico bien + RAM sana → rendimiento. Cargando es el único
     *      momento en que gastar batería no tiene coste.
     *   4. Batería baja o RAM justa → ahorro.
     *   5. El resto → equilibrio.
     *
     * Lo que el adaptativo **nunca** elige solo es CALIDAD: 24,6 s por imagen no
     * se decide por el usuario.
     */
    private fun adaptive(state: DeviceState): PerformanceDecisions {
        val thermal = state.thermalStatus
        val lowBattery = state.batteryPercent <= BATTERY_CRITICAL
        val lowBatterySoft = state.batteryPercent <= BATTERY_LOW
        val ramTight = state.availableRamMb < RAM_LOW_MB || state.lowRamDevice
        val ramSane = state.availableRamMb >= RAM_HEALTHY_MB && !state.lowRamDevice
        val goodNetwork = state.network >= NetworkClass.WIFI

        return when {
            thermal >= ThermalStatus.SEVERE ->
                powerSaver(state).copy(
                    maxFps = 30,
                    dynamicRefreshRate = true,
                    reason = "adaptativo → ahorro: térmico $thermal (el móvil ya está limitándose solo)",
                )

            lowBattery && !state.charging ->
                powerSaver(state).copy(
                    reason = "adaptativo → ahorro: batería ${state.batteryPercent}% y sin cargar",
                )

            state.charging && thermal <= ThermalStatus.LIGHT && ramSane && goodNetwork ->
                performance(state).copy(
                    reason = "adaptativo → rendimiento: cargando, térmico $thermal, ${state.availableRamMb} MB libres",
                )

            lowBatterySoft && !state.charging ->
                powerSaver(state).copy(
                    reason = "adaptativo → ahorro: batería ${state.batteryPercent}%, sin cargar",
                )

            ramTight ->
                powerSaver(state).copy(
                    reason = "adaptativo → ahorro: solo ${state.availableRamMb} MB libres",
                )

            else -> balanced(state).copy(
                reason = "adaptativo → equilibrio: batería ${state.batteryPercent}%, térmico $thermal, red ${state.network.name.lowercase()}",
            )
        }
    }
}
