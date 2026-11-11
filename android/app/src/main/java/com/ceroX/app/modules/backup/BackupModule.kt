package com.ceroX.app.modules.backup

import android.content.Context
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface
import java.io.File

/**
 * BackupModule — Fusión de Foxlations.
 *
 * Características fusionadas:
 * - Backup local de biblioteca (ZIP) (Foxlations)
 * - Restauración de biblioteca desde backup (Foxlations)
 * - Exportación JSON/CSV
 * - Todo local, sin nube obligatoria
 */
class BackupModule : ModuleInterface() {

    override val name = "backup-restore"
    override val version = "0.1.0-fusion"
    override val dependencies: List<String> = emptyList()

    private var backupDir: File? = null
    private val backupHistory = mutableListOf<BackupRecord>()

    override fun initialize(config: Map<String, Any>): Boolean {
        backupDir = config["backup_dir"] as? File
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
            mapOf<String, Any?>(
                "backup_dir" to (backupDir?.absolutePath ?: "none"),
                "backups_created" to backupHistory.size,
                "cloud_required" to false
            )
        )
    }

    /**
     * Crea un backup de verdad: un ZIP con el contenido dentro.
     *
     * Antes el nombre decía `.zip`, la extensión decía `.zip`, y **no se escribía
     * ningún archivo**: `success = true` con el comentario "Relleno:
     * implementar compresión ZIP real". El historial apuntaba un backup que no
     * existía en disco. Un usuario podía perder su biblioteca por confiar en esa
     * línea.
     */
    fun createBackup(libraryData: String): BackupResult {
        val timestamp = System.currentTimeMillis()
        val fileName = "ceroX_backup_$timestamp.zip"
        val dir = backupDir
            ?: return BackupResult(
                fileName = "", success = false, backupPath = null,
                sizeBytes = 0, createdAt = timestamp,
                errorMessage = "No hay directorio de backups disponible"
            )

        val destino = File(dir, fileName)
        return try {
            dir.mkdirs()
            java.util.zip.ZipOutputStream(destino.outputStream().buffered()).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("library.json"))
                zip.write(libraryData.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            // Comprobación que antes no existía: que el archivo esté y tenga bytes.
            if (!destino.exists() || destino.length() == 0L) {
                return BackupResult(fileName, success = false, backupPath = null,
                    sizeBytes = 0, createdAt = timestamp,
                    errorMessage = "El ZIP se creó vacío")
            }
            val result = BackupResult(
                fileName = fileName,
                success = true,
                backupPath = destino.absolutePath,
                sizeBytes = destino.length(),
                createdAt = timestamp
            )
            backupHistory.add(BackupRecord(fileName, timestamp, true))
            result
        } catch (e: Exception) {
            BackupResult(fileName, success = false, backupPath = null, sizeBytes = 0,
                createdAt = timestamp,
                errorMessage = "No se pudo escribir el backup: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Restaura un backup.
     *
     * Antes: `if (file.exists()) success = true, restoredItems = 0, "Backup
     * restaurado"`. El mismo fallo que en BackupManager, en otro archivo.
     *
     * Ahora se indexa y se dice qué contiene. No se escribe nada en la biblioteca.
     */
    fun restoreBackup(backupPath: String): RestoreResult {
        val file = File(backupPath)
        return if (file.exists()) {
            val indice = BackupIndex.indexar(file)
            RestoreResult(
                success = false,
                restoredItems = 0,
                message = if (indice.restaurable) {
                    "El backup es válido (${indice.formato}, ${indice.entradas} entrada(s)) " +
                        "pero el decodificador de este esquema todavía no está escrito. " +
                        "No se ha escrito NADA en tu biblioteca."
                } else {
                    "No se puede restaurar: ${indice.problemas.joinToString("; ")}"
                }
            )
        } else {
            RestoreResult(
                success = false,
                restoredItems = 0,
                message = "Archivo de backup no encontrado: $backupPath"
            )
        }
    }

    fun listBackups(): List<BackupRecord> = backupHistory.toList()

    /**
     * Construye el sobre de exportacion y valida lo que entra.
     *
     * Devolvía `libraryData` tal cual, o sea que "exportar" era no hacer nada y
     * llamarlo exportacion. Aqui se comprueba que la entrada sea JSON y se
     * envuelve con el formato, la version y la fecha, que es lo que necesita un
     * backup para poder reconocerse al restaurarlo.
     */
    fun exportLibraryJson(libraryData: String): String {
        val raiz = try {
            org.json.JSONObject(libraryData)
        } catch (e: org.json.JSONException) {
            return org.json.JSONObject()
                .put("error", "libraryData no es un JSON valido, no se exporto nada")
                .toString()
        }
        return org.json.JSONObject()
            .put("formato", "cero-X-biblioteca")
            .put("version", 1)
            .put("exportadoEn", System.currentTimeMillis())
            .put("entradas", raiz.length())
            .put("biblioteca", raiz)
            .toString()
    }
}

data class BackupRecord(
    val fileName: String,
    val createdAt: Long,
    val success: Boolean
)