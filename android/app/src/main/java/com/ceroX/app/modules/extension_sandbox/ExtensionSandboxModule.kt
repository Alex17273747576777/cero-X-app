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

    companion object {
        /** Los métodos de instalación que el módulo soporta de verdad. */
        val INSTALLATION_METHODS = listOf("shizuku", "root", "saf")

        /**
         * Si `ExtensionSourceLoader` (el contrato eu.kanade.tachiyomi) está en el
         * APK. Se comprueba por reflexión en vez de escribirlo a mano: si algún
         * día se quita, esto se entera solo.
         */
        val LOADER_DISPONIBLE: Boolean by lazy {
            try {
                Class.forName("com.ceroX.app.modules.source_api.eu.kanade.tachiyomi.source.ExtensionSourceLoader")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }
    }

    override val name = "extension-sandbox"
    override val version = "1.0.0-beta"
    override val dependencies: List<String> = emptyList()

    private val sandboxManager = ExtensionSandboxManager()
    private val isolator = ExtensionIsolator()
    private lateinit var installer: ExtensionInstaller
    private val validator = ExtensionValidator()
    private val factory = ExtensionFactory()
    // Estaba importado pero nunca instanciado: el módulo hablaba de compilar
    // extensiones y no tenía compilador. `compiler.state()` no compilaba porque
    // la propiedad no existía.
    private val compiler = ExtensionCompiler()
    private val installedExtensions = mutableListOf<InstalledExtension>()

    override fun initialize(config: Map<String, Any>): Boolean {
        val appContext = config["context"] as? android.content.Context
        sandboxManager.initialize()
        isolator.initialize()
        validator.initialize()
        factory.initialize()
        compiler.initialize()
        // El instalador necesita contexto para lanzar el Intent del sistema y
        // para copiar al directorio privado. Antes se creaba sin contexto y los
        // tres metodos no hacian nada, asi que daba igual; ahora si.
        installer = ExtensionInstaller(appContext)
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
                // Estado leído de un objeto inmutable, no de una propiedad privada
                // que había que abrir para poder mirar.
                "sandbox_active" to sandboxManager.state().active,
                // Activo en memoria Y con directorio en disco son cosas distintas.
                // Sin esto, un sandbox que no pudo crear su carpeta se anunciaba
                // como activo y luego fallaba al instalar.
                "sandbox_dir_existe" to sandboxManager.state().existeEnDisco,
                "isolated_extensions" to isolator.isolatedCount(),
                "installed_extensions" to installedExtensions.size,
                "installation_methods" to INSTALLATION_METHODS.size,
                "factory_enabled" to factory.isEnabled(),
                "compiler_enabled" to compiler.state().enabled,
                // Aquí había dos `true` escritos a mano: "miyorare_compatible" y
                // "foxlations_improved". Nadie comprobaba nada. Miyorare es
                // GPL-3.0 y cero-X es Apache-2.0: decir "compatible" era, en el
                // mejor caso, no significar nada, y en el peor una promesa sobre
                // algo que no se probó ni una vez. Se sustituyen por lo que sí se
                // puede afirmar y se puede medir.
                "contrato_eu_kanade" to LOADER_DISPONIBLE,
                "carga_de_extensiones_reales" to false,
                "nota" to ("el aislamiento y la validación están hechos y probados; " +
                    "cargar una extensión de Mihon de verdad necesita un APK de " +
                    "extensión, que no se empaqueta en este repo")
            )
        )
    }

    // ========== Sandbox Management ==========

    fun installExtension(apkPath: String, method: InstallationMethod): ExtensionInstaller.InstallResult {
        if (!_started) {
            return ExtensionInstaller.InstallResult(false, "Sandbox no iniciado", null)
        }

        // 1. Validar extensión
        val validation = validator.validate(apkPath)
        if (!validation.isValid) {
            return ExtensionInstaller.InstallResult(
                false, "Extensión no válida: ${validation.errors.joinToString(", ")}", null
            )
        }

        // 2. Instalar según método
        val result = installer.install(apkPath, method)
        if (!result.success) {
            return result
        }

        // 3. Aislar en sandbox — SOLO si el instalador dio un id.
        //
        // `result.extensionId!!` con doble exclamación era una bomba: el método
        // del sistema abre el instalador de Android y devuelve extensionId = null
        // (porque no instala él, instala el usuario). Ese `!!` habría lanzado
        // NullPointerException en cuanto alguien usara el método por defecto.
        // Y encima se marcaba como ACTIVE una extensión que quizá el usuario no
        // llegó a confirmar.
        val idInstalada = result.extensionId
            ?: return ExtensionInstaller.InstallResult(
                result.success,
                result.message + " No se ha aislado nada todavía: el aislamiento " +
                    "ocurre cuando la extensión está realmente instalada.",
                null
            )

        val installed = InstalledExtension(
            id = idInstalada,
            name = java.io.File(apkPath).nameWithoutExtension,
            apkPath = apkPath,
            method = method,
            installedAt = System.currentTimeMillis(),
            status = ExtensionStatus.ACTIVE,
            permissions = validation.permissions
        )
        isolator.isolateExtension(installed)
        installedExtensions.add(installed)

        return ExtensionInstaller.InstallResult(true, result.message, installed.id)
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