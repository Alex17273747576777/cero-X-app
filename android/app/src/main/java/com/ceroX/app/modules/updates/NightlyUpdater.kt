package com.ceroX.app.modules.updates

import android.content.Context
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
            downloadUrl = null,
            releaseNotes = null
        )
    }

    fun downloadUpdate(url: String): UpdateDownloadResult {
        // Placeholder: descarga real de APK
        return UpdateDownloadResult(url, false, "Descarga no implementada")
    }

    fun installUpdate(apkPath: String): Boolean {
        val file = File(apkPath)
        if (!file.exists()) return false

        val intent = android.content.Intent(Intent.ACTION_VIEW)
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, file)
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
        intent.flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP

        context.startActivity(intent)
        return true
    }

    fun listChannels(): List<UpdateChannel> {
        return listOf(UpdateChannel.STABLE, UpdateChannel.BETA, UpdateChannel.PREVIEW)
    }
}

enum class UpdateChannel {
    STABLE, BETA, PREVIEW
}

data class UpdateCheckResult(
    val available: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val channel: UpdateChannel,
    val downloadUrl: String?,
    val releaseNotes: String?
)

data class UpdateDownloadResult(
    val url: String,
    val success: Boolean,
    val message: String
)