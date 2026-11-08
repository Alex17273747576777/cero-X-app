package com.ceroX.app.modules.extension_sandbox

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface
import com.ceroX.app.modules.extension_factory.CompileResult
import com.ceroX.app.modules.extension_factory.ExportResult
import com.ceroX.app.modules.extension_factory.ExtensionCompiler
import com.ceroX.app.modules.extension_factory.ExtensionConfig
import com.ceroX.app.modules.extension_factory.ExtensionFactory
import com.ceroX.app.modules.extension_factory.ExtensionTemplate
import com.ceroX.app.modules.extension_factory.FactoryResult

/**
 * ExtensionSandboxModule — SanTBox de extensiones para cero-X.
 *
 * Inspirado en Miyorare (Private / Sandbox — Internal) y mejorado respecto a
 * Foxlations RepoForge.
 *
 * Características:
 * - Sandbox aislado para ejecución de extensiones de terceros
 * - 3 métodos de instalación (Shizuku Silent, Sistema Normal, Sandbox Interno)
 * - Validador de extensiones antes de instalación
 * - Isolador de extensiones (cada una en su propio contexto)
 * - Fábrica de extensiones propias (mejor que RepoForge)
 * - Tienda de extensiones integrada
 */
class ExtensionSandboxModule : ModuleInterface() {

    override val name = "extension-sandbox"
    override val version = "1.0.0-beta"
    override val dependencies: List<String> = emptyList()

    private val sandboxManager = ExtensionSandboxManager()
    private val isolator = ExtensionIsolator()
    private val installer = ExtensionInstaller()
    private val validator = ExtensionValidator()
    private val factory = ExtensionFactory()
    private val installedExtensions = mutableListOf<InstalledExtension>()

    override fun initialize(config: Map<String, Any>): Boolean {
        sandboxManager.initialize()
        isolator.initialize()
        installer.initialize()
        validator.initialize()
        factory.initialize()
        _initialized = true
        return true
    }

    override fun start(): Boolean {
        if (!_initialized) return false
        _started = true
        return true
    }

    override fun stop(): Boolean {
        isolator.stopAllExtensions()
        _started = false
        return true
    }

    override fun healthCheck(): ModuleHealth {
        return ModuleHealth(
            name, version,
            if (_started) "healthy" else "stopped",
            mapOf<String, Any?>(
                "sandbox_active" to sandboxManager.isSandboxActive(),
                "isolated_extensions" to isolator.isolatedCount(),
                "installed_extensions" to installedExtensions.size,
                "installation_methods" to 3,
                "factory_enabled" to factory.isEnabled(),
                "miyorare_compatible" to true,
                "foxlations_improved" to true
            )
        )
    }

    // ========== Sandbox Management ==========

    fun installExtension(apkPath: String, method: InstallationMethod): InstallResult {
        if (!_started) {
            return InstallResult(false, "Sandbox no iniciado", null)
        }

        // 1. Validar extensión
        val validation = validator.validate(apkPath)
        if (!validation.isValid) {
            return InstallResult(false, "Extensión no válida: ${validation.errors.joinToString(", ")}", null)
        }

        // 2. Instalar según método
        val result = installer.install(apkPath, method)
        if (!result.success) {
            return result
        }

        // 3. Aislar en sandbox
        val installed = InstalledExtension(
            id = result.extensionId!!,
            name = java.io.File(apkPath).nameWithoutExtension,
            apkPath = apkPath,
            method = method,
            installedAt = System.currentTimeMillis(),
            status = ExtensionStatus.ACTIVE,
            permissions = validation.permissions
        )
        isolator.isolateExtension(installed)
        installedExtensions.add(installed)

        return InstallResult(true, "Extensión instalada en sandbox", installed.id)
    }

    fun uninstallExtension(extensionId: String): Boolean {
        val ext = installedExtensions.firstOrNull { it.id == extensionId } ?: return false
        isolator.releaseExtension(extensionId)
        installedExtensions.remove(ext)
        return true
    }

    fun listInstalledExtensions(): List<InstalledExtension> = installedExtensions.toList()

    fun getExtensionStatus(extensionId: String): ExtensionStatus? {
        return installedExtensions.firstOrNull { it.id == extensionId }?.status
    }

    fun enableExtension(extensionId: String) {
        installedExtensions.firstOrNull { it.id == extensionId }?.let {
            it.status = ExtensionStatus.ACTIVE
            isolator.enableExtension(extensionId)
        }
    }

    fun disableExtension(extensionId: String) {
        installedExtensions.firstOrNull { it.id == extensionId }?.let {
            it.status = ExtensionStatus.DISABLED
            isolator.disableExtension(extensionId)
        }
    }

    // ========== Extension Factory (Mejor que RepoForge) ==========

    fun createExtension(template: ExtensionTemplate, config: ExtensionConfig): FactoryResult {
        return factory.createExtension(template, config)
    }

    fun listTemplates(): List<ExtensionTemplate> = factory.listTemplates()

    fun compileExtension(projectPath: String): CompileResult {
        return factory.compileExtension(projectPath)
    }

    fun exportExtension(extensionId: String, outputPath: String): ExportResult {
        val ext = installedExtensions.firstOrNull { it.id == extensionId }
            ?: return ExportResult(false, "Extensión no encontrada", null)
        // ExtensionFactory no exporta: quien exporta a APK es ExtensionCompiler.
        val projectPath = java.io.File(ext.apkPath).parent ?: ext.apkPath
        return ExtensionCompiler().exportToApk(projectPath, outputPath)
    }
}

enum class InstallationMethod {
    SHIZUKU_SILENT,    // Como Miyorare: Silent Install más automático
    SYSTEM_NORMAL,     // Como Miyorare: Cara standar Android
    SANDBOX_INTERNAL   // Como Miyorare: Hanya di dalam cero-X (Private Sandbox)
}

data class InstallResult(
    val success: Boolean,
    val message: String,
    val extensionId: String?
)

data class InstalledExtension(
    val id: String,
    val name: String,
    val apkPath: String,
    val method: InstallationMethod,
    val installedAt: Long,
    // `status` es var: enableExtension/disableExtension cambian el estado de una
    // extensión ya instalada. Con val, esas dos funciones no compilaban porque
    // intentaban reasignar una propiedad inmutable.
    var status: ExtensionStatus,
    val permissions: List<String>
)

enum class ExtensionStatus {
    ACTIVE, DISABLED, ERROR, UPDATING
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val permissions: List<String>
)