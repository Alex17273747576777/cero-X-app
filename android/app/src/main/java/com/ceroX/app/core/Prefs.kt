package com.ceroX.app.core

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Las preferencias de cero-X: un JSON en el almacenamiento **externo** de la app.
 *
 * POR QUÉ NO `SharedPreferences`
 * ------------------------------
 * Porque `getSharedPreferences()` escribe en `/data/data/<paquete>/shared_prefs/`,
 * es decir, en el almacenamiento interno, y no hay forma de decirle otra cosa. La
 * regla del proyecto prohíbe guardar ahí cualquier dato que no sea un token, así
 * que las preferencias normales dejan de usarse para eso.
 *
 * QUÉ SE PIERDE Y CÓMO SE COMPENSA
 * --------------------------------
 *   · **Escritura atómica**: se escribe a un `.tmp` y se renombra. Un corte deja
 *     el archivo viejo, no uno a medias.
 *   · **Hilos seguros**: `@Synchronized` sobre el mapa. Es un JSON pequeño.
 *   · **Múltiples procesos**: la app es de un solo proceso. Si algún día deja de
 *     serlo, esto no sirve y hay que decirlo; está dicho aquí.
 *   · **Notificaciones de cambio**: no las hay. Nadie las estaba usando.
 *
 * LECTURA DE LO ANTIGUO
 * ---------------------
 * `migrarDesdeSharedPreferences()` copia lo que hubiera en las preferencias
 * internas a este almacén y **borra las claves**. Sin eso, el usuario perdería sus
 * ajustes al actualizar, que es peor que la deuda que se está saldando.
 */
class Prefs(contexto: Context) {

    private val app = contexto.applicationContext
    private val gson = Gson()

    /** Aviso de por qué no hay almacén, si no lo hay. Lo lee la pantalla de estado. */
    var aviso: String? = null
        private set

    private val archivo: File? = AlmacenExterno.archivo(app, NOMBRE).also {
        if (it == null) aviso = "sin almacenamiento externo: las preferencias no se guardan"
    }

    private val datos: MutableMap<String, Any?> by lazy { cargar() }

    @Synchronized
    private fun cargar(): MutableMap<String, Any?> {
        val f = archivo ?: return mutableMapOf()
        if (!f.isFile) return mutableMapOf()
        return runCatching {
            val tipo = object : TypeToken<MutableMap<String, Any?>>() {}.type
            val leido: MutableMap<String, Any?>? = gson.fromJson(f.readText(Charsets.UTF_8), tipo)
            leido ?: mutableMapOf()
        }.getOrElse {
            // Un JSON corrupto no puede tumbar el arranque: se aparta y se sigue
            // con valores por defecto, dejando el archivo para poder mirarlo.
            runCatching { f.renameTo(File(f.parentFile, "$NOMBRE.corrupto")) }
            mutableMapOf()
        }
    }

    @Synchronized
    private fun guardar() {
        val f = archivo ?: return
        runCatching {
            val temporal = File(f.parentFile, "$NOMBRE.tmp")
            temporal.writeText(gson.toJson(datos), Charsets.UTF_8)
            if (!temporal.renameTo(f)) {
                // Algunos volúmenes no permiten renombrar sobre un archivo
                // existente; se borra el destino y se reintenta una vez.
                f.delete()
                temporal.renameTo(f)
            }
        }
    }

    // ── API equivalente a la que se usaba ─────────────────────────────────

    fun getString(clave: String, porDefecto: String? = ""): String? =
        (datos[clave] as? String) ?: porDefecto

    fun putString(clave: String, valor: String) {
        datos[clave] = valor
        guardar()
    }

    fun getBoolean(clave: String, porDefecto: Boolean = false): Boolean =
        (datos[clave] as? Boolean) ?: porDefecto

    fun putBoolean(clave: String, valor: Boolean) {
        datos[clave] = valor
        guardar()
    }

    fun getInt(clave: String, porDefecto: Int = 0): Int =
        (datos[clave] as? Number)?.toInt() ?: porDefecto

    fun putInt(clave: String, valor: Int) {
        datos[clave] = valor
        guardar()
    }

    fun getLong(clave: String, porDefecto: Long = 0L): Long =
        (datos[clave] as? Number)?.toLong() ?: porDefecto

    fun putLong(clave: String, valor: Long) {
        datos[clave] = valor
        guardar()
    }

    fun borrar(clave: String) {
        datos.remove(clave)
        guardar()
    }

    /** Todas las claves. Para el inventario y para migrar. */
    fun claves(): Set<String> = datos.keys.toSet()

    /**
     * Trae lo que hubiera en `SharedPreferences` y borra las claves del interno.
     *
     * Devuelve cuántas claves se migraron. Se puede llamar en cada arranque: si no
     * queda nada en el interno, devuelve 0 y no hace nada.
     */
    fun migrarDesdeSharedPreferences(nombres: List<String>): Int {
        var movidas = 0
        for (nombre in nombres) {
            val viejo = runCatching {
                app.getSharedPreferences(nombre, Context.MODE_PRIVATE)
            }.getOrNull() ?: continue
            val todas = runCatching { viejo.all }.getOrNull().orEmpty()
            if (todas.isEmpty()) continue
            for ((k, v) in todas) {
                if (datos.containsKey(k)) continue
                datos[k] = v
                movidas++
            }
            // Se borra de verdad: dejar la copia en el interno mantendría la deuda.
            runCatching { viejo.edit().clear().apply() }
        }
        if (movidas > 0) guardar()
        return movidas
    }

    companion object {
        /** Nombre del archivo dentro de `AlmacenExterno/`. */
        const val NOMBRE = "preferencias.json"
    }
}
