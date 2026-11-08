package com.ceroX.app.core.engine

import com.ceroX.app.core.ConfigManager

import android.content.Context
import android.util.Log

/**
 * CoreEngine — Compatibilidad con código existente.
 *
 * Mantenido para retrocompatibilidad. El motor principal de ciclo de vida
 * es ahora ModuleLifecycleManager en CeroXApplication.
 */
class CoreEngine(private val context: Context) {

    val eventBus = EventBus()
    val registry = ModuleRegistry()
    val config = ConfigManager(context)

    private val TAG = "CoreEngine"

    fun registerModule(
        name: String,
        moduleClass: Class<out ModuleInterface>,
        metadata: ModuleMetadata
    ) {
        try {
            val instance = moduleClass.getDeclaredConstructor().newInstance()
            registry.register(name, metadata, instance)
            Log.i(TAG, "Módulo registrado (legacy): $name")
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando módulo $name", e)
        }
    }

    fun initializeAll() {
        for (name in registry.listModules()) {
            initializeModule(name)
        }
    }

    fun initializeModule(name: String): Boolean {
        val instance = registry.getInstance(name) ?: return false
        val meta = registry.getMetadata(name) ?: return false

        // Verificar dependencias básicas
        for (dep in meta.dependencies) {
            val depMeta = registry.getMetadata(dep)
            if (depMeta != null && depMeta.status != ModuleStatus.INITIALIZED) {
                // Dependencia no lista, saltar por ahora
                return false
            }
        }

        try {
            val success = instance.initialize(config.getModuleConfig(name))
            if (success) {
                registry.updateStatus(name, ModuleStatus.INITIALIZED)
                eventBus.publish("module.initialized", name)
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando $name", e)
            registry.updateStatus(name, ModuleStatus.ERROR)
            return false
        }
    }

    fun startAll() {
        for (name in registry.listModules()) {
            startModule(name)
        }
        eventBus.publish("system.started", null)
    }

    fun startModule(name: String): Boolean {
        val instance = registry.getInstance(name) ?: return false
        val meta = registry.getMetadata(name)
        if (meta == null || meta.status != ModuleStatus.INITIALIZED) return false

        try {
            val success = instance.start()
            if (success) {
                registry.updateStatus(name, ModuleStatus.STARTED)
                eventBus.publish("module.started", name)
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando $name", e)
            registry.updateStatus(name, ModuleStatus.ERROR)
            return false
        }
    }

    fun stopAll() {
        for (name in registry.listModules().reversed()) {
            stopModule(name)
        }
        eventBus.publish("system.stopped", null)
    }

    fun stopModule(name: String): Boolean {
        val instance = registry.getInstance(name) ?: return false
        try {
            val success = instance.stop()
            if (success) {
                registry.updateStatus(name, ModuleStatus.STOPPED)
                eventBus.publish("module.stopped", name)
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo $name", e)
            return false
        }
    }

    fun healthCheckAll(): Map<String, ModuleHealth> {
        val result = mutableMapOf<String, ModuleHealth>()
        for (name in registry.listModules()) {
            val instance = registry.getInstance(name) ?: continue
            try {
                result[name] = instance.healthCheck()
            } catch (e: Exception) {
                result[name] = ModuleHealth(name, "unknown", "error",
                    mapOf<String, Any>("exception" to e.message ?: "Unknown error"))
            }
        }
        return result
    }
}

/** Status of a module */
enum class ModuleStatus {
    UNLOADED, INITIALIZED, STARTED, STOPPED, ERROR
}

/** Health check result */
data class ModuleHealth(
    val name: String,
    val version: String,
    val status: String,
    val details: Map<String, Any?> = emptyMap()
)