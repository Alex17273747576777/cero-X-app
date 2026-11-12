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
    private val propietarios = java.util.concurrent.ConcurrentHashMap<Long, String>()

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

    /** Sustituye una versión del mismo APK sin dejar fuentes viejas mezcladas. */
    @Synchronized
    fun registrarExtension(paquete: String, nuevas: List<Source>): Int {
        require(nuevas.isNotEmpty()) { "La extensión no produjo fuentes" }
        val idsNuevos = nuevas.map { it.id }
        require(idsNuevos.toSet().size == idsNuevos.size) { "IDs duplicados dentro de $paquete" }
        val colision = nuevas.firstOrNull { candidata ->
            fuentes.any { it.id == candidata.id && propietarios[it.id] != paquete }
        }
        require(colision == null) { "La fuente ${colision?.id} ya pertenece a otra extensión o a cero-X" }
        val antiguas = fuentes.filter { propietarios[it.id] == paquete }
        fuentes.removeAll(antiguas.toSet())
        antiguas.forEach { propietarios.remove(it.id) }
        nuevas.forEach {
            fuentes.add(it)
            propietarios[it.id] = paquete
        }
        repositorio = null
        return nuevas.size
    }

    @Synchronized
    fun bajaExtension(paquete: String): Int {
        val quitadas = fuentes.filter { propietarios[it.id] == paquete }
        fuentes.removeAll(quitadas.toSet())
        quitadas.forEach { propietarios.remove(it.id) }
        if (quitadas.isNotEmpty()) repositorio = null
        return quitadas.size
    }

    fun baja(fuenteId: Long): Boolean {
        val quitada = fuentes.removeIf { it.id == fuenteId }
        propietarios.remove(fuenteId)
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
        "navegables" to fuentes.size,
        "extensiones_nox" to propietarios.values.distinct().size,
        "fuente_local" to fuentes.any { it is LocalFolderSource },
        "detalle" to fuentes.map {
            mapOf(
                "id" to it.id, "nombre" to it.name, "lang" to it.lang,
                "paquete" to propietarios[it.id],
            )
        },
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
        // Solo la carpeta externa de la app: la de `filesDir` era el almacenamiento
        // interno, que el usuario no puede llenar sin root, así que no aportaba
        // nada y sí ensuciaba el inventario.
        val candidatas = buildList {
            com.ceroX.app.core.AlmacenExterno.directorio(context)
                ?.let { add(java.io.File(it, "lector")) }
        }
        candidatas.firstOrNull { it.isDirectory }?.let { registrar(LocalFolderSource(raiz = it)) }
        // Sólo se restauran APK que el usuario autorizó antes con «Ejecutar en
        // cero-X». Descargar o instalar para Mihon nunca basta para ejecutarlos.
        val app = context.applicationContext as? android.app.Application
        if (app != null) {
            eu.kanade.tachiyomi.HostExtensiones.inicializar(app)
            Thread({ RegistroNox.restaurar(app) }, "cerox-restaurar-nox").start()
        }
    }

    /** Solo para pruebas: deja el objeto como recién creado. */
    fun limpiar() {
        fuentes.clear()
        propietarios.clear()
        repositorio = null
    }
}
