package com.ceroX.app.sources

import android.content.Context
import eu.kanade.tachiyomi.source.Source

/**
 * Dónde viven las fuentes que la app conoce.
 *
 * POR QUÉ UN OBJETO GLOBAL Y NO UNA INYECCIÓN BONITA
 * ------------------------------------------------
 * Hay dos consumidores que no se hablan: el cargador de extensiones (que
 * descubre APKs en tiempo de ejecución y no sabe nada de Compose) y el lector
 * (que se abre desde un `Intent` y no sabe nada de extensiones). Un grafo de
 * dependencias completo para dos consumidores sería ceremonia. Lo que hace falta
 * es un sitio donde registrar y otro donde preguntar.
 *
 * LO QUE **NO** ES
 * ----------------
 * No es un `ServiceLoader` disfrazado ni una lista que se rellena sola. Si nadie
 * registra nada, está vacía, y el lector lo dice en vez de fingir que busca.
 *
 * La fuente local se registra siempre que se pueda leer la carpeta; es la única
 * que no depende de terceros y la que permite que el lector funcione en modo
 * avión. Si la carpeta no existe, no se registra: una fuente que siempre falla
 * es peor que no tenerla.
 */
object RepositorioGlobal {

    private val fuentes = java.util.concurrent.CopyOnWriteArrayList<Source>()

    @Volatile
    private var repositorio: ContentRepository? = null

    /** Registra una fuente y devuelve true si era nueva (por id). */
    fun registrar(fuente: Source): Boolean {
        if (fuentes.any { it.id == fuente.id }) return false
        fuentes.add(fuente)
        repositorio = null // invalida el que hubiera
        return true
    }

    fun registrarTodas(nuevas: List<Source>): Int {
        var n = 0
        nuevas.forEach { if (registrar(it)) n++ }
        return n
    }

    fun baja(fuenteId: Long): Boolean {
        val quitada = fuentes.removeIf { it.id == fuenteId }
        if (quitada) repositorio = null
        return quitada
    }

    fun fuentes(): List<Source> = fuentes.toList()

    /**
     * Depuración honesta: cuántas fuentes hay de verdad.
     *
     * Antes, el módulo de extensiones solo declaraba nombres de gestores que no
     * se comprobaban. Esto devuelve un número que se puede comparar con cero.
     */
    fun resumen(): Map<String, Any?> = mapOf(
        "fuentes" to fuentes.size,
        "navegables" to fuentes.count { it is eu.kanade.tachiyomi.source.CatalogueSource },
        "fuente_local" to fuentes.any { it is LocalFolderSource },
        "detalle" to fuentes.map { mapOf("id" to it.id, "nombre" to it.name, "lang" to it.lang) },
    )

    fun obtener(): ContentRepository = repositorio ?: synchronized(this) {
        repositorio ?: ContentRepository(fuentes()).also { repositorio = it }
    }

    /**
     * Arranque: registra la carpeta local si el usuario tiene una.
     *
     * Se mira `filesDir/lector` y `getExternalFilesDir(null)/lector` — carpetas
     * propias de la app, sin permisos de almacenamiento— y además una carpeta
     * `ceroX` en el almacenamiento compartido si ya hay permiso concedido. No se
     * pide permiso aquí: pedir permisos de almacenamiento al arrancar para algo
     * que el usuario no ha pedido es exactamente lo que no se debe hacer.
     */
    fun arrancar(context: Context) {
        val candidatas = buildList {
            add(java.io.File(context.filesDir, "lector"))
            context.getExternalFilesDir(null)?.let { add(java.io.File(it, "lector")) }
        }
        candidatas.firstOrNull { it.isDirectory }?.let { registrar(LocalFolderSource(raiz = it)) }
    }

    /** Solo para pruebas: deja el objeto como recién creado. */
    fun limpiar() {
        fuentes.clear()
        repositorio = null
    }
}
