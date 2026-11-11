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

    /** `null` cuando no hay almacenamiento externo: entonces no se escribe nada. */
    private val backupDir: File?

    init {
        // `Context.externalFilesDirs` no existe: es `getExternalFilesDirs(type)`,
        // que devuelve un Array<File>? (null si no hay almacenamiento externo).
        // Se prefiere el externo propio de la app y se cae al interno si no hay.
        // ANTES se caía a `context.filesDir` cuando no había externo, y eso metía
        // las copias de seguridad del usuario en el almacenamiento interno. Ahora
        // NO hay caída: sin externo, `createBackup` devuelve un fallo con el motivo.
        val base = com.ceroX.app.core.AlmacenExterno.directorio(context)
        backupDir = base?.let { File(it, "backups").also { d -> d.mkdirs() } }
    }

    fun createBackup(
        backupName: String = "ceroX_backup",
        /** Qué guardar: nombre de archivo dentro del ZIP → contenido. */
        contenido: Map<String, ByteArray> = emptyMap(),
    ): BackupResult {
        val timestamp = System.currentTimeMillis()
        val fileName = "$backupName-$timestamp.zip"
        val dir = backupDir ?: return BackupResult(
            fileName = "",
            success = false,
            backupPath = null,
            sizeBytes = 0,
            createdAt = timestamp,
            errorMessage = "no hay almacenamiento externo disponible: la copia NO se escribe en " +
                "el almacenamiento interno (regla del proyecto)",
        )
        val backupFile = File(dir, fileName)

        try {
            // Antes esto abría un ZipOutputStream con un comentario dentro y lo
            // cerraba: producía un ZIP VÁLIDO Y VACÍO, y la línea siguiente lo
            // declaraba exitoso. El usuario tenía un "backup" de 22 bytes sin nada
            // dentro y no había forma de saberlo hasta el día que lo necesitara.
            //
            // Lo que no puede hacer este método es inventarse la biblioteca: quien
            // llama tiene que pasarle el contenido. Se añade el parámetro y, si va
            // vacío, se devuelve FALLO en vez de un ZIP vacío con éxito.
            ZipOutputStream(FileOutputStream(backupFile)).use { zos ->
                if (contenido.isNotEmpty()) {
                    contenido.forEach { (nombre, datos) ->
                        zos.putNextEntry(java.util.zip.ZipEntry(nombre))
                        zos.write(datos)
                        zos.closeEntry()
                    }
                }
            }

            if (contenido.isEmpty()) {
                backupFile.delete()
                return BackupResult(
                    fileName = fileName, success = false, backupPath = null,
                    sizeBytes = 0, createdAt = timestamp,
                    errorMessage = "Sin contenido que guardar: no se ha creado ningún " +
                        "backup. Un backup vacío con éxito es peor que no tener backup."
                )
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
     * restaurar nada, con `restoredItems = 0` y el comentario "Relleno" al
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
        return backupDir?.listFiles()
            ?.filter { it.extension == "zip" }
            ?.map { BackupInfo(it.name, it.absolutePath, it.length(), it.lastModified()) }
            ?: emptyList()
    }

    fun deleteBackup(fileName: String): Boolean {
        val dir = backupDir ?: return false
        return File(dir, fileName).delete()
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