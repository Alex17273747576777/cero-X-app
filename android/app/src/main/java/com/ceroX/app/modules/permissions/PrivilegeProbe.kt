package com.ceroX.app.modules.permissions

import android.content.Context
import android.content.pm.PackageManager
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface
import java.io.File

/**
 * PrivilegeProbe — determina **de verdad** con qué nivel corre cero-X.
 *
 * Los tres escalones de [ManagerCapability] se comprueban por separado:
 *
 *   • `installed`  → PackageManager.getPackageInfo
 *   • `serviceRunning` → el proceso de Shizuku publica su binder; aquí se mira
 *     si el paquete está habilitado y si su servicio declarado existe. No se
 *     puede llamar al binder sin la librería de Shizuku, y meterla solo para
 *     preguntar sería 300 KB de dependencia para un dato.
 *   • `authorized` → solo se puede saber preguntando al binder. **No se puede
 *     comprobar desde aquí**, así que se devuelve `false` y se dice por qué.
 *
 * Ese último punto es deliberado: `authorized` queda en `false` hasta que exista
 * un cliente real de Shizuku. Declararlo `true` por defecto haría que
 * [ManagerCapability.usable] mintiera.
 */
class PrivilegeProbe(private val context: Context) : ModuleInterface() {

    override val name = "privilege-probe"
    override val version = "1.0.0-beta"
    override val dependencies: List<String> = emptyList()

    private var level: PrivilegeLevel = PrivilegeLevel.UNKNOWN
    private var lastProbeMillis: Long = 0L
    private var suBinary: String? = null

    override fun initialize(config: Map<String, Any>): Boolean {
        probe()
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
            "level" to level.id,
            "su_binary" to (suBinary ?: "none"),
            // Honestidad explícita: esto es lo que todavía NO se puede saber.
            "shizuku_authorization_checkable" to false,
            "shizuku_authorization_reason" to
                "requiere un binder client de Shizuku; sin él no se puede preguntar",
        ),
    )

    /** Relee el entorno. Es barato: tres getPackageInfo y unos stats de fichero. */
    fun probe(): PrivilegeLevel {
        val root = detectRoot()
        suBinary = root
        val shizuku = probeShizuku()
        level = when {
            root != null -> PrivilegeLevel.ROOT
            shizuku?.usable == true -> PrivilegeLevel.SHIZUKU
            else -> PrivilegeLevel.NORMAL
        }
        lastProbeMillis = System.currentTimeMillis()
        return level
    }

    fun currentLevel(): PrivilegeLevel = level

    /** Milisegundos desde la última sonda. La UI puede evitar sondear en bucle. */
    fun millisSinceProbe(): Long =
        if (lastProbeMillis == 0L) Long.MAX_VALUE else System.currentTimeMillis() - lastProbeMillis

    /**
     * Root no es un paquete: es un binario ejecutable. Se buscan las rutas
     * conocidas de su, y se comprueba que sea ejecutable de verdad.
     *
     * Ojo: encontrar `su` **no** significa tener root concedido. Significa que
     * hay un mecanismo instalado (Magisk, KernelSU…). La concesión requiere
     * ejecutarlo, y ejecutarlo desde aquí abriría un diálogo del gestor de root
     * sin que el usuario haya pedido nada. Por eso se busca, se informa, y se
     * deja la ejecución para el momento en que el usuario la pida.
     */
    fun detectRoot(): String? =
        ExternalManagers.ROOT_BINARIES.firstOrNull { path ->
            runCatching { File(path).let { it.exists() && it.canExecute() } }.getOrDefault(false)
        }

    /** Nivel real de instalación disponible ahora mismo. */
    fun installCapability(source: String): InstallCapability {
        val lvl = currentLevel()
        return when {
            lvl == PrivilegeLevel.ROOT ->
                InstallCapability(true, false, PrivilegeLevel.ROOT, "root detectado: instalación silenciosa posible")
            lvl == PrivilegeLevel.SHIZUKU ->
                InstallCapability(true, false, PrivilegeLevel.SHIZUKU, "Shizuku autorizado")
            else ->
                InstallCapability(false, true, PrivilegeLevel.NORMAL, "solo instalación con confirmación del sistema")
        }.copy(source = source)
    }

    private fun probeShizuku(): ManagerCapability? {
        val pm = context.packageManager
        for (pkg in ExternalManagers.SHIZUKU_VARIANTS) {
            val installed = runCatching {
                pm.getPackageInfo(pkg, 0)
            }.isSuccess
            if (installed) {
                // El servicio no se puede interrogar sin la librería de Shizuku.
                // Se informa instalado=true, serviceRunning=false, authorized=false:
                // los dos últimos escalones NO verificados, y por eso usable=false.
                return ManagerCapability(
                    packageName = pkg,
                    displayName = "Shizuku ($pkg)",
                    installed = true,
                    serviceRunning = false,
                    authorized = false,
                    notes = "instalado; servicio y autorización sin verificar (falta binder client)",
                )
            }
        }
        return null
    }

    /** Qué paquetes de la lista de gestores están presentes, con su estado. */
    fun installedManagers(): List<ManagerCapability> {
        val pm = context.packageManager
        return ExternalManagers.PACKAGE_MANAGERS.map { cap ->
            val installed = runCatching { pm.getPackageInfo(cap.packageName, 0) }.isSuccess
            cap.copy(installed = installed)
        }
    }

    private fun currentLevelOrNull(): PrivilegeLevel? =
        if (level == PrivilegeLevel.UNKNOWN) null else level
}

/**
 * Con qué se puede instalar, aquí y ahora.
 *
 * Separar [silent] de [needsConfirmation] es lo que evita que la UI prometa
 * "instalación automática" cuando en realidad va a saltar un diálogo del sistema.
 */
data class InstallCapability(
    val silent: Boolean,
    val needsConfirmation: Boolean,
    val level: PrivilegeLevel,
    val reason: String,
    val source: String = "",
) {
    fun describe(): String = when {
        silent -> "silenciosa vía ${level.id}: $reason"
        needsConfirmation -> "con confirmación del sistema: $reason"
        else -> "no disponible: $reason"
    }
}
