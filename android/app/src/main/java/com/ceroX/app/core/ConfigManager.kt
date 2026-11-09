package com.ceroX.app.core

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Los ajustes de la app.
 *
 * ANTES: `getSharedPreferences("ceroX_config")`, que vive en
 * `/data/data/<paquete>/shared_prefs/`, es decir, en el almacenamiento interno.
 * AHORA: [Prefs], un JSON en el almacenamiento externo de la app. La API es la
 * misma para no tocar los veinte sitios que la llaman.
 */
class ConfigManager(private val context: Context) {

    private val prefs: Prefs = Prefs(context)
    private val gson = Gson()

    init {
        // Se trae lo que hubiera quedado en el interno de versiones anteriores y
        // se borra de allí. Sin esto, actualizar borraría los ajustes.
        prefs.migrarDesdeSharedPreferences(listOf("ceroX_config"))
    }

    /** Por qué no hay dónde guardar, si no lo hay. */
    val aviso: String? get() = prefs.aviso

    private val defaults: Map<String, Map<String, Any>> = mapOf(
        "ai-training" to mapOf(
            "backend" to "onnx",
            "device" to "cpu",
            "max_workers" to 2
        ),
        "downloads" to mapOf(
            "max_concurrent" to 5,
            "verify_hash" to true,
            "chunk_size" to 8192
        ),
        "reader" to mapOf(
            "engine" to "mihon",
            "extensions_enabled" to true,
            "cache_size_mb" to 512
        ),
        "audio" to mapOf(
            "backend" to "wotaku",
            "sample_rate" to 44100,
            "buffer_size" to 4096
        ),
        "video" to mapOf(
            "backend" to "ffmpeg",
            "hw_accel" to false
        ),
        "reverse-engineering" to mapOf(
            "tools" to listOf("antigravity", "deepseek-harness"),
            "auto_clone" to true
        ),
        "environments" to mapOf(
            "preferred_distro" to "debian",
            "debian_version" to "testing",
            "container_runtime" to "podman"
        )
    )

    fun getModuleConfig(moduleName: String): Map<String, Any> {
        val json = prefs.getString("module_$moduleName", null)
        if (json != null) {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val saved: Map<String, Any>? = gson.fromJson(json, type)
            if (saved != null) return saved
        }
        return defaults[moduleName] ?: emptyMap()
    }

    fun saveModuleConfig(moduleName: String, config: Map<String, Any>) {
        prefs.putString("module_$moduleName", gson.toJson(config))
    }

    fun getString(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    fun putString(key: String, value: String) {
        prefs.putString(key, value)
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun putBoolean(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
    }

    fun getInt(key: String, default: Int = 0): Int =
        prefs.getInt(key, default)

    fun putInt(key: String, value: Int) {
        prefs.putInt(key, value)
    }
}