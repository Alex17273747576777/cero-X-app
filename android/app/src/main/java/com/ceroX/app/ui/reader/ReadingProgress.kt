package com.ceroX.app.ui.reader

import android.content.Context

/**
 * Dónde se quedó el usuario, guardado de verdad.
 *
 * POR QUÉ UN ARCHIVO PEQUEÑO Y NO ROOM
 * ------------------------------------
 * El marcapáginas es una fila por capítulo y se escribe una vez por cambio de
 * página. Meter eso en Room significa una transacción por página; con un archivo
 * de propiedades es un `commit()` de tres líneas que se lee entero al abrir.
 *
 * Se escribe **con `commit()` y no con `apply()`** a propósito: esta escritura
 * ocurre al salir del lector, y `apply()` es asíncrona — si el proceso muere
 * justo después (lo que hace el sistema al salir de una app pesada), el
 * marcapáginas se pierde. Una escritura de 200 B en el hilo de salida no cuesta
 * nada; perder la página donde ibas, sí.
 *
 * LAS CLAVES
 * ----------
 * Se indexa por `fuente|obra|capítulo`, no por título: dos fuentes distintas
 * pueden tener el mismo título, y entonces el marcapáginas de una abriría la
 * otra.
 */
class ReadingProgress(private val context: Context) {

    /** Un marcapáginas. `pagina` es 0-based, como el índice del lector. */
    data class Marca(
        val fuente: String,
        val obra: String,
        val capitulo: String,
        val pagina: Int,
        val total: Int,
        /** Cuándo se tocó por última vez, para ordenar «continuar leyendo». */
        val actualizadoMs: Long,
    ) {
        val progreso: Float get() = if (total <= 0) 0f else (pagina + 1).toFloat() / total
    }

    private val archivo: java.io.File
        get() = java.io.File(context.filesDir, "lector-progreso.properties")

    private fun clave(fuente: String, obra: String, capitulo: String) = "$fuente\u0000$obra\u0000$capitulo"

    /** Guarda o actualiza una marca. No lanza: perder una marca no puede cerrar la app. */
    fun guardar(marca: Marca) {
        try {
            val props = leerTodas().toMutableMap()
            props[clave(marca.fuente, marca.obra, marca.capitulo)] = listOf(
                marca.pagina.toString(),
                marca.total.toString(),
                marca.actualizadoMs.toString(),
                marca.fuente,
                marca.obra,
                marca.capitulo,
            )
            escribir(props)
        } catch (e: Throwable) {
            // Silencio deliberado: es un marcapáginas, no un pago.
        }
    }

    /** La marca de un capítulo, o null si nunca se abrió. */
    fun marca(fuente: String, obra: String, capitulo: String): Marca? =
        leerTodas()[clave(fuente, obra, capitulo)]?.aMarca()

    /** Todas las marcas, de la más reciente a la más antigua. */
    fun recientes(): List<Marca> =
        leerTodas().values.mapNotNull { it.aMarca() }.sortedByDescending { it.actualizadoMs }

    /** Lo que alimenta «continuar leyendo» en la pantalla principal. */
    fun continuarLeyendo(limite: Int = 10): List<Marca> = recientes().take(limite)

    fun borrar(fuente: String, obra: String, capitulo: String) {
        try {
            val props = leerTodas().toMutableMap()
            props.remove(clave(fuente, obra, capitulo))
            escribir(props)
        } catch (e: Throwable) {
        }
    }

    private fun leerTodas(): Map<String, List<String>> = try {
        val f = archivo
        if (!f.exists()) {
            emptyMap()
        } else {
            val props = java.util.Properties()
            f.inputStream().use { props.load(it) }
            props.stringPropertyNames().associateWith { k ->
                (props.getProperty(k) ?: "").split("\u0001")
            }
        }
    } catch (e: Throwable) {
        // Archivo corrupto: se empieza de cero en vez de cerrar la app al abrir
        // un capítulo. El marcapáginas es comodidad, no un dato crítico.
        emptyMap()
    }

    private fun escribir(props: Map<String, List<String>>) {
        val p = java.util.Properties()
        props.forEach { (k, v) -> p.setProperty(k, v.joinToString("\u0001")) }
        // Se escribe a un temporal y se renombra: si el proceso muere a mitad,
        // el archivo bueno sigue entero en vez de quedar truncado.
        val temporal = java.io.File(archivo.parentFile, archivo.name + ".tmp")
        temporal.outputStream().use { p.store(it, "cero-X — marcapáginas del lector") }
        if (!temporal.renameTo(archivo)) {
            archivo.delete()
            temporal.renameTo(archivo)
        }
    }

    private fun List<String>.aMarca(): Marca? {
        if (size < 6) return null
        val pagina = getOrNull(0)?.toIntOrNull() ?: return null
        val total = getOrNull(1)?.toIntOrNull() ?: return null
        val cuando = getOrNull(2)?.toLongOrNull() ?: return null
        return Marca(get(3), get(4), get(5), pagina, total, cuando)
    }
}
