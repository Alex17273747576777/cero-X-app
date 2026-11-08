package com.ceroX.app.core

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class ConfigManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ceroX_config", Context.MODE_PRIVATE)
    private val gson = Gson()

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
        val json = gson.toJson(config)
        prefs.edit().putString("module_$moduleName", json).apply()
    }

    fun getString(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getInt(key: String, default: Int = 0): Int =
        prefs.getInt(key, default)

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }
}