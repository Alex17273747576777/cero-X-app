package com.ceroX.app.core

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Implementa la interfaz que exige extensions-lib sin escribir en shared_prefs.
 * Cada fuente tiene un JSON independiente en el almacenamiento externo propio.
 */
object PreferenciasExtensiones {
    private val instancias = ConcurrentHashMap<String, PreferenciaArchivo>()

    fun obtener(context: Context, nombre: String): SharedPreferences {
        require(nombre.matches(Regex("[A-Za-z0-9_.-]{1,160}"))) { "Nombre de preferencias no válido" }
        val raiz = requireNotNull(AlmacenExterno.directorio(context)) {
            "No hay almacenamiento externo para las preferencias de la extensión"
        }
        val dir = File(raiz, "host-extensiones/preferencias")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("No se pudo crear el directorio de preferencias")
        val archivo = File(dir, "$nombre.json")
        return instancias.computeIfAbsent(archivo.absolutePath) { PreferenciaArchivo(archivo) }
    }

    /** Sólo para pruebas: no borra archivos del usuario. */
    internal fun olvidar() = instancias.clear()
}

private class PreferenciaArchivo(private val archivo: File) : SharedPreferences {
    private val gson = Gson()
    private val lock = Any()
    private val listeners = CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()
    private var datos: MutableMap<String, Any?> = cargar()

    private fun cargar(): MutableMap<String, Any?> {
        if (!archivo.isFile) return mutableMapOf()
        require(archivo.length() <= 4L * 1024 * 1024) { "Preferencias de extensión excesivas" }
        val tipo = object : TypeToken<MutableMap<String, Any?>>() {}.type
        val leido = runCatching {
            gson.fromJson<MutableMap<String, Any?>>(archivo.readText(Charsets.UTF_8), tipo)
        }.getOrElse { throw IOException("Preferencias corruptas: ${archivo.name}", it) }
        return leido ?: mutableMapOf()
    }

    override fun getAll(): MutableMap<String, *> = synchronized(lock) {
        datos.mapValuesTo(LinkedHashMap()) { (_, v) -> copiar(v) }
    }

    override fun getString(key: String, defValue: String?): String? =
        synchronized(lock) { datos[key] as? String ?: defValue }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        synchronized(lock) {
            val valor = datos[key]
            when (valor) {
                is Collection<*> -> valor.filterIsInstance<String>().toMutableSet()
                else -> defValues?.toMutableSet()
            }
        }

    override fun getInt(key: String, defValue: Int): Int =
        synchronized(lock) { (datos[key] as? Number)?.toInt() ?: defValue }

    override fun getLong(key: String, defValue: Long): Long =
        synchronized(lock) { (datos[key] as? Number)?.toLong() ?: defValue }

    override fun getFloat(key: String, defValue: Float): Float =
        synchronized(lock) { (datos[key] as? Number)?.toFloat() ?: defValue }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        synchronized(lock) { datos[key] as? Boolean ?: defValue }

    override fun contains(key: String): Boolean = synchronized(lock) { datos.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) { listeners += listener }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) { listeners -= listener }

    private fun copiar(valor: Any?): Any? = when (valor) {
        is Set<*> -> valor.toSet()
        is List<*> -> valor.toList()
        else -> valor
    }

    private inner class Editor : SharedPreferences.Editor {
        private val cambios = LinkedHashMap<String, Any?>()
        private val borradas = mutableSetOf<String>()
        private var limpiar = false

        override fun putString(key: String, value: String?) = poner(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) =
            poner(key, values?.toSet())
        override fun putInt(key: String, value: Int) = poner(key, value)
        override fun putLong(key: String, value: Long) = poner(key, value)
        override fun putFloat(key: String, value: Float) = poner(key, value)
        override fun putBoolean(key: String, value: Boolean) = poner(key, value)

        private fun poner(key: String, value: Any?) = apply {
            cambios[key] = value
            borradas -= key
        }

        override fun remove(key: String) = apply {
            cambios -= key
            borradas += key
        }

        override fun clear() = apply { limpiar = true }

        override fun commit(): Boolean {
            var modificadas = emptySet<String>()
            val exito = synchronized(lock) {
                val anterior = datos.toMap()
                if (limpiar) datos.clear()
                borradas.forEach(datos::remove)
                cambios.forEach { (k, v) -> if (v == null) datos.remove(k) else datos[k] = copiar(v) }
                modificadas = (cambios.keys + borradas + if (limpiar) anterior.keys else emptySet())
                    .filter { anterior[it] != datos[it] }.toSet()
                if (modificadas.isEmpty()) {
                    true
                } else {
                    try {
                        escribir()
                        true
                    } catch (_: IOException) {
                        datos = anterior.toMutableMap()
                        false
                    }
                }
            }
            // Nunca se ejecuta código de la extensión mientras se mantiene lock.
            if (exito) modificadas.forEach { key ->
                listeners.forEach { listener ->
                    listener.onSharedPreferenceChanged(this@PreferenciaArchivo, key)
                }
            }
            return exito
        }

        override fun apply() {
            // Es una escritura pequeña y atómica. Hacerla síncrona evita perder
            // credenciales/ajustes si Android mata el proceso justo después.
            commit()
        }

        private fun escribir() {
            val temporal = File(archivo.parentFile, "${archivo.name}.tmp")
            try {
                temporal.writeText(gson.toJson(datos), Charsets.UTF_8)
                if (archivo.exists() && !archivo.delete()) throw IOException("No se pudo renovar ${archivo.name}")
                if (!temporal.renameTo(archivo)) throw IOException("No se pudo guardar ${archivo.name}")
            } finally {
                temporal.delete()
            }
        }
    }
}
