package com.ceroX.app.modules.updates

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * UpdatesModule — canales de actualización e instalación de un APK descargado.
 *
 * La cabecera decía «Fusión de Anizen Preview + Echo-Nightly + Namida Beta» con
 * «Auto-updater de builds». No hay código de ninguno de los tres (Namida es una
 * EULA propietaria, ver `NOTICE`) y no hay auto-updater: lo que existe es un canal
 * elegido en memoria, un historial de cambios de canal, y `installUpdate()`, que
 * lanza el Intent del sistema sobre un APK que ya esté descargado.
 *
 * Lo que falta y no se finge: no hay extremo que consultar. `checkForUpdates()`
 * devuelve «no hay nada» sin preguntar a ningún sitio, porque no hay servidor de
 * actualizaciones; las releases se publican en GitHub y la instalación es manual
 * (ver `docs/FIRMA-NUEVA.md`).
 */
class UpdatesModule : ModuleInterface() {

    override val name = "updates-channel"
    override val version = "0.1.0-fusion"
    override val dependencies: List<String> = emptyList()

    private var currentChannel = UpdateChannel.PREVIEW

    /**
     * La versión instalada, leída del PackageManager en `initialize()`.
     *
     * Estaba escrita a mano, y `NightlyUpdater` tenía otra distinta, también
     * inventada y con un número de Android que la interfaz ya había dejado de
     * afirmar por falso. Un canal de actualizaciones que no sabe qué versión tiene
     * instalada no puede comparar con nada, y el healthCheck enseñaba ese número
     * como si fuera el real.
     */
    private var currentVersion = "desconocida"
    private val updateHistory = mutableListOf<UpdateRecord>()

    override fun initialize(config: Map<String, Any>): Boolean {
        // El instalador de actualizaciones necesita contexto para lanzar el Intent
        // del sistema. Antes no lo tenía porque el método no instalaba nada.
        appContext = config["context"] as? android.content.Context
        currentVersion = versionRealDeLaApp(appContext) ?: "desconocida"
        val channelStr = config["channel"] as? String ?: "preview"
        currentChannel = when (channelStr.lowercase()) {
            "stable" -> UpdateChannel.STABLE
            "beta" -> UpdateChannel.BETA
            else -> UpdateChannel.PREVIEW
        }
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
                "channel" to currentChannel.name.lowercase(),
                "version" to currentVersion,
                // Aquí había una clave a true que afirmaba una actualización
                // automática inexistente: no hay comprobación periódica ni descarga.
                // Y otra que decía «no hay actualizaciones» sin distinguir «pregunté
                // y no hay nada» de «no pregunté a nadie», que es lo que pasa.
                "servidor_de_actualizaciones" to "ninguno",
                "instalador_de_apk_listo" to (appContext != null)
            )
        )
    }

    /**
     * No consulta nada porque no hay nada que consultar: cero-X no tiene servidor de
     * actualizaciones. Devuelve `available = false` con la versión real instalada,
     * que es lo único que se puede afirmar sin inventar. Cuando exista un extremo
     * (las releases de GitHub ya lo son, de hecho) se compara aquí contra
     * `currentVersion`, que ya no es un número escrito a mano.
     */
    fun checkForUpdates(): UpdateCheckResult {
        return UpdateCheckResult(
            available = false,
            currentVersion = currentVersion,
            latestVersion = currentVersion,
            channel = currentChannel,
            downloadUrl = null
        )
    }

    fun switchChannel(channel: UpdateChannel) {
        currentChannel = channel
        updateHistory.add(UpdateRecord(
            channel = channel,
            version = currentVersion,
            switchedAt = System.currentTimeMillis()
        ))
    }

    fun listUpdateHistory(): List<UpdateRecord> = updateHistory.toList()

    /**
     * Instala una actualización descargada.
     *
     * Antes: añadía una entrada al historial y devolvía `true` sin tocar el APK.
     * El historial decía "actualizado a la versión X" cuando no se había instalado
     * nada — y encima registraba `currentVersion`, o sea que apuntaba la versión
     * que ya tenías como si fuera la nueva.
     *
     * Ahora delega en el mismo instalador de extensiones, que lanza el Intent real
     * del sistema. Solo se apunta en el historial si el Intent se lanzó.
     */
    private var appContext: android.content.Context? = null

    fun installUpdate(apkPath: String): Boolean {
        val apk = java.io.File(apkPath)
        if (!apk.exists() || apk.length() == 0L) return false
        val lanzado = try {
            val ctx = appContext ?: return false
            val uri = androidx.core.content.FileProvider.getUriForFile(
                ctx, "${ctx.packageName}.fileprovider", apk
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
        if (!lanzado) return false
        updateHistory.add(UpdateRecord(
            channel = currentChannel,
            version = currentVersion,
            switchedAt = System.currentTimeMillis()
        ))
        return true
    }
}

enum class UpdateChannel {
    PREVIEW, BETA, STABLE
}

data class UpdateCheckResult(
    val available: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val channel: UpdateChannel,
    val downloadUrl: String?
)

data class UpdateRecord(
    val channel: UpdateChannel,
    val version: String,
    val switchedAt: Long
)