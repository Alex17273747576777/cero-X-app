package com.ceroX.app.modules.downloads

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import java.security.MessageDigest

/**
 * DownloadTask — Gestor de descargas con verificación SHA-256.
 */
class DownloadTask(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val activeDownloads = mutableMapOf<Long, DownloadInfo>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            activeDownloads.remove(id)
        }
    }

    init {
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    fun enqueue(url: String, fileName: String, destinationDir: String = Environment.DIRECTORY_DOWNLOADS): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Descargando $fileName...")
            .setDestinationInExternalFilesDir(context, destinationDir, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val id = downloadManager.enqueue(request)
        activeDownloads[id] = DownloadInfo(id, url, fileName, "pending")
        return id
    }

    fun verifySha256(filePath: String, expectedHash: String): Boolean {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            java.io.FileInputStream(filePath).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            return hash.equals(expectedHash, ignoreCase = true)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getActiveDownloads(): List<DownloadInfo> = activeDownloads.values.toList()

    fun cancelDownload(id: Long): Boolean {
        // DownloadManager.remove() devuelve el numero de descargas borradas
        // (0 o 1), no un Boolean. Devolverlo tal cual no compila.
        return downloadManager.remove(id) > 0
    }

    /**
     * Quita de la lista las descargas que ya terminaron.
     *
     * Era un metodo vacio. Se pregunta al DownloadManager del sistema por cada id
     * activo y se retiran las que están completas o fallidas, que es lo unico que
     * se puede saber de verdad desde aqui.
     */
    fun cleanup() {
        val ids = activeDownloads.keys.toLongArray()
        if (ids.isEmpty()) return

        val query = android.app.DownloadManager.Query()
        query.setFilterById(*ids)
        val cursor = try {
            downloadManager.query(query)
        } catch (e: Exception) {
            null
        } ?: return

        cursor.use { c ->
            val colId = c.getColumnIndex(android.app.DownloadManager.COLUMN_ID)
            val colEstado = c.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
            if (colId < 0 || colEstado < 0) return
            val terminadas = mutableListOf<Long>()
            while (c.moveToNext()) {
                val estado = c.getInt(colEstado)
                if (estado == android.app.DownloadManager.STATUS_SUCCESSFUL ||
                    estado == android.app.DownloadManager.STATUS_FAILED
                ) {
                    terminadas.add(c.getLong(colId))
                }
            }
            for (id in terminadas) activeDownloads.remove(id)
        }
    }
}

data class DownloadInfo(
    val id: Long,
    val url: String,
    val fileName: String,
    val status: String
)