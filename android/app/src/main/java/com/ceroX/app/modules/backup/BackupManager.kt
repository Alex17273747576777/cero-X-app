package com.ceroX.app.modules.backup

import android.content.Context
import java.io.File
import java.util.zip.ZipOutputStream
import java.io.FileOutputStream

/**
 * BackupManager — Backup y restauración de biblioteca local.
 * Basado en Foxlations backup/restore.
 */
class BackupManager(private val context: Context) {

    private val backupDir: File

    init {
        // `Context.externalFilesDirs` no existe: es `getExternalFilesDirs(type)`,
        // que devuelve un Array<File>? (null si no hay almacenamiento externo).
        // Se prefiere el externo propio de la app y se cae al interno si no hay.
        val externo = context.getExternalFilesDirs(null)?.firstOrNull()
        backupDir = File(externo ?: context.filesDir, "backups")
        backupDir.mkdirs()
    }

    fun createBackup(backupName: String = "ceroX_backup"): BackupResult {
        val timestamp = System.currentTimeMillis()
        val fileName = "$backupName-$timestamp.zip"
        val backupFile = File(backupDir, fileName)

        try {
            ZipOutputStream(FileOutputStream(backupFile)).use { zos ->
                // Placeholder: añadir archivos de biblioteca al ZIP
            }

            return BackupResult(
                fileName = fileName,
                success = true,
                backupPath = backupFile.absolutePath,
                sizeBytes = backupFile.length(),
                createdAt = timestamp
            )
        } catch (e: Exception) {
            return BackupResult(
                fileName = fileName,
                success = false,
                backupPath = null,
                sizeBytes = 0,
                createdAt = timestamp,
                errorMessage = e.message
            )
        }
    }

    /**
     * Restaura un backup.
     *
     * ANTES ESTO MENTÍA
     * -----------------
     * El código era:
     *
     *     return if (file.exists()) RestoreResult(success = true, restoredItems = 0, ...)
     *
     * Comprobaba que el archivo existiera y declaraba la restauración exitosa sin
     * restaurar nada, con `restoredItems = 0` y el comentario "Placeholder" al
     * lado. El usuario leía "Backup restaurado" y su biblioteca seguía igual.
     *
     * AHORA
     * -----
     * Se indexa primero (patrón de houri `BackupDecoder` + `BackupIntegrityHandler`
     * y de Kototoro `BackupIndexFactory`): se lee el archivo, se dice qué contiene
     * y qué problemas tiene, y SOLO si el índice sale limpio se aplica.
     *
     * Y si el índice sale limpio pero el decodificador de este esquema todavía no
     * existe, se devuelve fallo diciéndolo. Un "no se puede" honesto es infinitamente
     * mejor que un "restaurado" falso: el primero te deja hacer otra cosa, el
     * segundo te hace creer que tu biblioteca está a salvo.
     */
    fun restoreBackup(backupPath: String): RestoreResult {
        val file = File(backupPath)
        val indice = BackupIndex.indexar(file)

        if (!indice.restaurable) {
            return RestoreResult(
                success = false,
                restoredItems = 0,
                message = "No se puede restaurar ${file.name}: " +
                    indice.problemas.joinToString("; ")
            )
        }

        return RestoreResult(
            success = false,
            restoredItems = 0,
            message = "El archivo ${file.name} es válido (${indice.formato}, " +
                "${indice.entradas} entrada(s)) pero el decodificador de este esquema " +
                "todavía no está escrito. No se ha escrito NADA en tu biblioteca."
        )
    }

    /** Índice sin restaurar, para que la interfaz pueda enseñarlo antes de decidir. */
    fun inspeccionar(backupPath: String): BackupIndex.Indice =
        BackupIndex.indexar(File(backupPath))

    fun listBackups(): List<BackupInfo> {
        return backupDir.listFiles()
            ?.filter { it.extension == "zip" }
            ?.map { BackupInfo(it.name, it.absolutePath, it.length(), it.lastModified()) }
            ?: emptyList()
    }

    fun deleteBackup(fileName: String): Boolean {
        return File(backupDir, fileName).delete()
    }
}

data class BackupResult(
    val fileName: String,
    val success: Boolean,
    val backupPath: String?,
    val sizeBytes: Long,
    val createdAt: Long,
    val errorMessage: String? = null
)

data class RestoreResult(
    val success: Boolean,
    val restoredItems: Int,
    val message: String
)

data class BackupInfo(
    val fileName: String,
    val path: String,
    val size: Long,
    val modifiedAt: Long
)