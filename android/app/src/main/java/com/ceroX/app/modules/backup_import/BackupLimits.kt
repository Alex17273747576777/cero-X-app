package com.ceroX.app.modules.backup_import

/**
 * Límites de importación.
 *
 * Un backup es un archivo que viene de fuera y que se descomprime en memoria.
 * Eso es, literalmente, la receta de una bomba de descompresión: 10 MB de gzip
 * pueden dar varios GB. Se limita lo que se acepta antes de abrirlo, no después.
 */
object BackupLimits {
    /** Tamaño máximo del archivo de backup en disco. */
    const val MAX_FILE_BYTES = 256L * 1024 * 1024

    /** Tope de bytes descomprimidos. Aquí es donde se corta la bomba. */
    const val MAX_INFLATED_BYTES = 512L * 1024 * 1024

    /** Entradas máximas al recorrer un zip. */
    const val MAX_ZIP_ENTRIES = 10_000

    /** Profundidad máxima de anidamiento (zip dentro de gzip dentro de zip). */
    const val MAX_NESTING = 2

    /** Fichas y capítulos máximos que se aceptan de golpe. */
    const val MAX_ENTRIES = 500_000
}
