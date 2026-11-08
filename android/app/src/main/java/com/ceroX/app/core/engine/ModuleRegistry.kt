package com.ceroX.app.core.engine

import java.util.concurrent.ConcurrentHashMap

class ModuleRegistry {

    private val metadata = ConcurrentHashMap<String, ModuleMetadata>()
    private val instances = ConcurrentHashMap<String, ModuleInterface>()

    fun register(name: String, meta: ModuleMetadata, instance: ModuleInterface) {
        metadata[name] = meta.copy(status = ModuleStatus.UNLOADED)
        instances[name] = instance
    }

    fun getInstance(name: String): ModuleInterface? = instances[name]

    fun getMetadata(name: String): ModuleMetadata? = metadata[name]

    fun listModules(): List<String> = metadata.keys.toList()

    fun updateStatus(name: String, status: ModuleStatus) {
        metadata[name]?.let { metadata[name] = it.copy(status = status) }
    }
}

/** Module metadata */
data class ModuleMetadata(
    val name: String,
    val version: String,
    val dependencies: List<String>,
    var status: ModuleStatus = ModuleStatus.UNLOADED
)