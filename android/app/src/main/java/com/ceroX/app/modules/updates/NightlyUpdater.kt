package com.ceroX.app.modules.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * NightlyUpdater — estado del canal y descarga de un APK que ya exista.
 *
 * «Basado en Anizen Preview, Echo-Nightly, Namida Beta» no era cierto: no hay
 * código de ninguno. `echo-nightly` y `namida-snapshots` son repositorios de builds
 * publicadas, no de fuente utilizable, y Namida es una EULA propietaria.
 */
class NightlyUpdater(private val context: Context) {

    private var currentChannel = UpdateChannel.PREVIEW
    private var currentVersion: String = versionRealDeLaApp(context) ?: "desconocida"

    fun setChannel(channel: UpdateChannel) {
        currentChannel = channel
    }

    fun getChannel(): UpdateChannel = currentChannel

    fun getCurrentVersion(): String = currentVersion

    /**
     * Igual que `UpdatesModule.checkForUpdates()`: no hay servidor al que preguntar,
     * así que devuelve «no hay actualización» con la versión real instalada.
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

    /**
     * Dónde debe el llamador dejar el APK descargado. Null si no hay externo.
     * Se expone para que quien descarga no tenga que decidirlo por su cuenta.
     */
    fun directorioDeDescarga(): File? =
        com.ceroX.app.core.AlmacenExterno.directorio(context)?.let { File(it, "actualizaciones") }

    fun installUpdate(apkPath: String): Boolean {
        val file = File(apkPath)
        if (!file.exists()) return false
        // Un APK de actualización es un recurso y la regla lo prohíbe en el
        // almacenamiento interno. Antes aceptaba cualquier ruta, incluida una
        // dentro de /data/data/<paquete>.
        val interno = context.applicationInfo.dataDir
        if (file.absolutePath.startsWith(interno)) {
            android.util.Log.w(
                "ceroX.updates",
                "APK en el almacenamiento interno ($apkPath): no se instala desde ahí (regla)",
            )
            return false
        }

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

/**
 * La versión que el sistema dice que está instalada, leída del PackageManager.
 *
 * Existe porque los dos módulos de actualizaciones tenían la suya escrita a mano, y
 * distinta entre ellos, y la enseñaban en el healthCheck como si fuera la real. Null
 * si no se puede leer: quien la recibe decide qué decir, y ninguno de los dos vuelve
 * a inventar un número. Fuera de la clase porque la usan los dos ficheros del paquete.
 */
internal fun versionRealDeLaApp(ctx: Context?): String? = ctx?.let { c ->
    try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName
    } catch (e: Exception) {
        null
    }
}

