package com.ceroX.app.modules.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * NightlyUpdater — Canal de actualizaciones nightly/preview.
 * Basado en Anizen Preview, Echo-Nightly, Namida Beta.
 */
class NightlyUpdater(private val context: Context) {

    private var currentChannel = UpdateChannel.PREVIEW
    private var currentVersion = "0.2.0-beta-android16"

    fun setChannel(channel: UpdateChannel) {
        currentChannel = channel
    }

    fun getChannel(): UpdateChannel = currentChannel

    fun getCurrentVersion(): String = currentVersion

    fun checkForUpdates(): UpdateCheckResult {
        // Placeholder: llamada real a servidor de actualizaciones
        return UpdateCheckResult(
            available = false,
            currentVersion = currentVersion,
            latestVersion = currentVersion,
            channel = currentChannel,
            downloadUrl = null
        )
    }

    /**
     * Descarga una actualización.
     *
     * Devolvía `UpdateDownloadResult`, una clase que desapareció al unificar los
     * tipos con UpdatesModule. Se devuelve el resultado que sí existe y que
     * además lleva el motivo, que es lo que la interfaz necesita para explicarse.
     */
    fun downloadUpdate(url: String): UpdateCheckResult =
        UpdateCheckResult(
            available = false,
            currentVersion = currentVersion,
            latestVersion = currentVersion,
            channel = currentChannel,
            downloadUrl = url,
        )

    fun installUpdate(apkPath: String): Boolean {
        val file = File(apkPath)
        if (!file.exists()) return false

        val intent = Intent(Intent.ACTION_VIEW)
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, file)
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP

        context.startActivity(intent)
        return true
    }

    fun listChannels(): List<UpdateChannel> {
        return listOf(UpdateChannel.STABLE, UpdateChannel.BETA, UpdateChannel.PREVIEW)
    }
}

// UpdateChannel y UpdateCheckResult ya están declarados en UpdatesModule.kt, en
// este mismo paquete. Repetirlos aquí era una redeclaración. NightlyUpdater usa
// los del módulo, que son los que consulta la interfaz.
