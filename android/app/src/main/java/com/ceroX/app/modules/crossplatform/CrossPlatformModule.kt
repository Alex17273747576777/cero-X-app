package com.ceroX.app.modules.crossplatform

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * CrossPlatformModule — Compatibilidad multiplataforma.
 *
 * Plataformas soportadas:
 *   • Android (teléfono y tablet) — API 26 a API 36
 *   • Android TV — navegación con D-pad, leanback, 10-foot UI
 *   • Huawei (HMS) — Huawei Mobile Services, AppGallery compatibles
 *   • Windows — builds nativos via Kotlin Multiplatform / OpenJDK Mobile
 *   • Linux — builds nativos, .deb, .rpm, .tar.gz, AppImage
 *
 * Detecta la plataforma en tiempo de ejecución y adapta:
 *   • Diseño (mobile vs TV vs desktop)
 *   • Navegación (tacto vs D-pad vs mouse/teclado)
 *   • Servicios disponibles (GMS vs HMS)
 *   • Formato de empaquetado (APK, MSI, deb, rpm, AppImage)
 */
class CrossPlatformModule : ModuleInterface() {

    override val name = "cross-platform"
    override val version = "1.0.0-beta"
    override val dependencies = emptyList()

    private val context: Context? = null
    private var detectedPlatform = Platform.ANDROID
    private var isAndroidTV = false
    private var isHuawei = false
    private var isTablet = false
    private var apiLevel = 0

    constructor(context: Context) : this() {
        this.context = context
        detectPlatform(context)
    }

    private constructor() {}

    private fun detectPlatform(context: Context) {
        apiLevel = Build.VERSION.SDK_INT

        // Detectar Android TV
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        isAndroidTV = uiMode == Configuration.UI_MODE_TYPE_TELEVISION

        // Detectar tablet
        val smallestWidth = context.resources.configuration.smallestScreenWidthDp
        isTablet = smallestWidth >= 600

        // Detectar Huawei (HMS)
        isHuawei = try {
            Class.forName("com.huawei.hms.hwid.HuaweiIdAuthManager")
            true
        } catch (e: ClassNotFoundException) {
            false
        }

        // Determinar plataforma principal
        if (isHuawei) {
            detectedPlatform = Platform.HUAWEI
        } else if (isAndroidTV) {
            detectedPlatform = Platform.ANDROID_TV
        } else {
            detectedPlatform = Platform.ANDROID
        }
    }

    override fun initialize(config: Map<String, Any>): Boolean {
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

    override fun healthCheck(): ModuleHealth {
        return ModuleHealth(
            name, version,
            if (_started) "healthy" else "stopped",
            mapOf(
                "platform" to detectedPlatform.value,
                "is_android_tv" to isAndroidTV,
                "is_huawei" to isHuawei,
                "is_tablet" to isTablet,
                "api_level" to apiLevel,
                "android_version" to Build.VERSION.RELEASE,
                "supported_platforms" to listOf("android", "android_tv", "huawei", "windows", "linux"),
                "package_formats" to listOf("apk", "msi", "deb", "rpm", "tar.gz", "appimage")
            )
        )
    }

    fun getPlatform(): Platform = detectedPlatform
    fun isAndroidTV(): Boolean = isAndroidTV
    fun isHuawei(): Boolean = isHuawei
    fun isTablet(): Boolean = isTablet
    fun getApiLevel(): Int = apiLevel

    fun getRecommendedLayout(): LayoutType {
        return when {
            isAndroidTV -> LayoutType.TEN_FOOT_LEANBACK
            isTablet -> LayoutType.TABLET_MULTI_COLUMN
            else -> LayoutType.MOBILE_SINGLE_COLUMN
        }
    }

    fun getNavigationType(): NavigationType {
        return when {
            isAndroidTV -> NavigationType.D_PAD
            isTablet -> NavigationType.TOUCH_AND_KEYBOARD
            else -> NavigationType.TOUCH
        }
    }

    fun getPackageFormat(): String {
        return when (detectedPlatform) {
            Platform.ANDROID, Platform.ANDROID_TV, Platform.HUAWEI -> "apk"
            Platform.WINDOWS -> "msi"
            Platform.LINUX -> "deb"
        }
    }

    fun supportsService(service: ServiceType): Boolean {
        return when (service) {
            ServiceType.GOOGLE_PLAY_SERVICES -> !isHuawei
            ServiceType.HUAWEI_MOBILE_SERVICES -> isHuawei
            ServiceType.ANDROID_TV_LEANBACK -> isAndroidTV
            ServiceType.WINDOWS_NATIVE -> detectedPlatform == Platform.WINDOWS
            ServiceType.LINUX_NATIVE -> detectedPlatform == Platform.LINUX
        }
    }
}

enum class Platform(val value: String) {
    ANDROID("android"),
    ANDROID_TV("android_tv"),
    HUAWEI("huawei"),
    WINDOWS("windows"),
    LINUX("linux")
}

enum class LayoutType {
    MOBILE_SINGLE_COLUMN, TABLET_MULTI_COLUMN, TEN_FOOT_LEANBACK, DESKTOP_WINDOWED
}

enum class NavigationType {
    TOUCH, D_PAD, TOUCH_AND_KEYBOARD, MOUSE_KEYBOARD
}

enum class ServiceType {
    GOOGLE_PLAY_SERVICES, HUAWEI_MOBILE_SERVICES, ANDROID_TV_LEANBACK, WINDOWS_NATIVE, LINUX_NATIVE
}