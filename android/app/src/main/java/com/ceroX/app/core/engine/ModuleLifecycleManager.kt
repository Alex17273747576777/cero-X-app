package com.ceroX.app.core.engine

import com.ceroX.app.core.ConfigManager

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * ModuleLifecycleManager — Gestor de ciclo de vida de módulos con resolución
 * de dependencias (topological sort), validación, retry y aislado por errores.
 *
 * Responsabilidades:
 *   • Topological sort para inicializar módulos en orden correcto de dependencias
 *   • Validación de dependencias al momento del registro
 *   • Reintentos (retry) con backoff exponencial para módulos fallidos
 *   • Aislamiento: un módulo que falla no detiene a los demás
 *   • Callbacks de ciclo de vida: onLowMemory, onTrimMemory, onDestroy
 *   • Estado persistente y recuperable
 */
class ModuleLifecycleManager(private val context: Context) {

    private val TAG = "ModuleLifecycleManager"
    private val registry = ModuleRegistry()
    private val eventBus = EventBus()
    private val config = ConfigManager(context)

    private val moduleInstances = ConcurrentHashMap<String, ModuleInterface>()
    private val moduleStatus = ConcurrentHashMap<String, ModuleStatus>()
    private val initRetries = ConcurrentHashMap<String, Int>()
    private val MAX_INIT_RETRIES = 3

    private val executor = Executors.newFixedThreadPool(4)

    /** Registra un módulo y valida que sus dependencias existan */
    fun registerModule(
        name: String,
        moduleClass: Class<out ModuleInterface>,
        metadata: ModuleMetadata
    ): Boolean {
        try {
            // Validar que las dependencias estén registradas o se registren después
            for (dep in metadata.dependencies) {
                if (!registry.metadata.containsKey(dep) && dep != name) {
                    Log.w(TAG, "Módulo $name depende de $dep que aún no está registrado")
                }
            }

            val instance = moduleClass.getDeclaredConstructor().newInstance()
            registry.register(name, metadata, instance)
            moduleInstances[name] = instance
            moduleStatus[name] = ModuleStatus.UNLOADED
            initRetries[name] = 0

            Log.i(TAG, "Módulo registrado: $name v${metadata.version} (deps: ${metadata.dependencies.joinToString()})")
            eventBus.publish("module.registered", name)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando módulo $name", e)
            eventBus.publish("module.register.error", Pair(name, e))
            return false
        }
    }

    /** Inicializa todos los módulos en orden de dependencias */
    fun initializeAll(): Map<String, Boolean> {
        val results = ConcurrentHashMap<String, Boolean>()
        val order = topologicalSort()

        Log.i(TAG, "Iniciando inicialización de ${order.size} módulos en orden: $order")

        for (name in order) {
            results[name] = initializeModuleWithRetry(name)
        }

        // Segundo pase: módulos que fallaron por dependencias no resueltas
        val failed = results.filter { !it.value }.keys.toList()
        if (failed.isNotEmpty()) {
            Log.w(TAG, "Reintentando módulos fallidos: $failed")
            for (name in failed) {
                results[name] = initializeModuleWithRetry(name)
            }
        }

        return results.toMap()
    }

    private fun initializeModuleWithRetry(name: String): Boolean {
        var attempt = 0
        while (attempt < MAX_INIT_RETRIES) {
            attempt++
            if (initializeModule(name)) {
                initRetries[name] = 0
                return true
            }
            initRetries[name] = attempt
            Log.w(TAG, "Intento $attempt/$MAX_INIT_RETRIES fallido para $name")
            try {
                Thread.sleep((1000L * attempt).coerceAtMost(5000))
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        Log.e(TAG, "Módulo $name falló después de $MAX_INIT_RETRIES intentos")
        moduleStatus[name] = ModuleStatus.ERROR
        eventBus.publish("module.error", name)
        return false
    }

    private fun initializeModule(name: String): Boolean {
        val instance = moduleInstances[name] ?: return false
        val meta = registry.getMetadata(name) ?: return false

        if (moduleStatus[name] == ModuleStatus.INITIALIZED) {
            return true // Ya inicializado
        }

        // Verificar que todas las dependencias estén INITIALIZED
        for (dep in meta.dependencies) {
            val depStatus = moduleStatus[dep]
            if (depStatus != ModuleStatus.INITIALIZED && depStatus != ModuleStatus.STARTED) {
                Log.w(TAG, "Saltando $name: dependencia $dep no está lista (${depStatus ?: "desconocida"})")
                return false
            }
        }

        try {
            val configMap = config.getModuleConfig(name)
            val success = instance.initialize(configMap)
            if (success) {
                moduleStatus[name] = ModuleStatus.INITIALIZED
                registry.updateStatus(name, ModuleStatus.INITIALIZED)
                eventBus.publish("module.initialized", name)
                Log.i(TAG, "Módulo inicializado: $name")
            } else {
                Log.e(TAG, "Módulo $name retornó false en initialize()")
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Excepción inicializando módulo $name", e)
            eventBus.publish("module.initialize.exception", Pair(name, e))
            return false
        }
    }

    /** Inicia todos los módulos INITIALIZED en orden de dependencias */
    fun startAll(): Map<String, Boolean> {
        val results = ConcurrentHashMap<String, Boolean>()
        val order = topologicalSort().filter {
            moduleStatus[it] == ModuleStatus.INITIALIZED
        }

        Log.i(TAG, "Iniciando ${order.size} módulos")

        for (name in order) {
            results[name] = startModule(name)
        }

        eventBus.publish("system.started", null)
        return results.toMap()
    }

    private fun startModule(name: String): Boolean {
        val instance = moduleInstances[name] ?: return false
        val meta = registry.getMetadata(name)
        if (meta == null || moduleStatus[name] != ModuleStatus.INITIALIZED) {
            return false
        }

        // Verificar dependencias STARTED
        for (dep in meta.dependencies) {
            if (moduleStatus[dep] != ModuleStatus.STARTED && moduleStatus[dep] != ModuleStatus.INITIALIZED) {
                Log.w(TAG, "Saltando inicio de $name: dependencia $dep no iniciada")
                return false
            }
        }

        try {
            val success = instance.start()
            if (success) {
                moduleStatus[name] = ModuleStatus.STARTED
                registry.updateStatus(name, ModuleStatus.STARTED)
                eventBus.publish("module.started", name)
                Log.i(TAG, "Módulo iniciado: $name")
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Excepción iniciando módulo $name", e)
            eventBus.publish("module.start.exception", Pair(name, e))
            return false
        }
    }

    /** Detiene todos los módulos en orden inverso */
    fun stopAll() {
        val order = topologicalSort().reversed()
        for (name in order) {
            stopModule(name)
        }
        eventBus.publish("system.stopped", null)
    }

    private fun stopModule(name: String): Boolean {
        val instance = moduleInstances[name] ?: return false
        if (moduleStatus[name] != ModuleStatus.STARTED && moduleStatus[name] != ModuleStatus.INITIALIZED) {
            return true
        }

        try {
            val success = instance.stop()
            if (success) {
                moduleStatus[name] = ModuleStatus.STOPPED
                registry.updateStatus(name, ModuleStatus.STOPPED)
                eventBus.publish("module.stopped", name)
                Log.i(TAG, "Módulo detenido: $name")
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Excepción deteniendo módulo $name", e)
            return false
        }
    }

    /** Topological sort: ordena módulos por dependencias */
    private fun topologicalSort(): List<String> {
        val visited = ConcurrentHashMap<String, Boolean>()
        val result = ConcurrentHashMap<Int, String>()
        var index = 0

        fun visit(name: String) {
            if (visited[name] == true) return
            visited[name] = true

            val meta = registry.getMetadata(name) ?: return
            for (dep in meta.dependencies) {
                if (moduleInstances.containsKey(dep)) {
                    visit(dep)
                }
            }
            result[index] = name
            index++
        }

        for (name in registry.listModules()) {
            visit(name)
        }

        return result.entries.sortedBy { it.key }.map { it.value }
    }

    /** Callback de baja memoria del sistema */
    fun onLowMemory() {
        Log.w(TAG, "Recibido onLowMemory — notificando a módulos")
        for (name in registry.listModules()) {
            val instance = moduleInstances[name]
            try {
                // Los módulos pueden sobrescribir onLowMemory si implementan LowMemoryAware
                if (instance is LowMemoryAware) {
                    instance.onLowMemory()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en onLowMemory de $name", e)
            }
        }
        eventBus.publish("system.low_memory", null)
    }

    /** Callback de recorte de memoria */
    fun onTrimMemory(level: Int) {
        for (name in registry.listModules()) {
            val instance = moduleInstances[name]
            try {
                if (instance is TrimMemoryAware) {
                    instance.onTrimMemory(level)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en onTrimMemory de $name", e)
            }
        }
        eventBus.publish("system.trim_memory", level)
    }

    /** Callback de destrucción del sistema */
    fun onDestroy() {
        stopAll()
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    // Getters
    fun getRegistry(): ModuleRegistry = registry
    fun getEventBus(): EventBus = eventBus
    fun getConfig(): ConfigManager = config
    fun getStatus(name: String): ModuleStatus? = moduleStatus[name]
    fun isModuleStarted(name: String): Boolean = moduleStatus[name] == ModuleStatus.STARTED
    fun getAllHealth(): Map<String, ModuleHealth> {
        val result = ConcurrentHashMap<String, ModuleHealth>()
        for (name in registry.listModules()) {
            val instance = moduleInstances[name] ?: continue
            try {
                result[name] = instance.healthCheck()
            } catch (e: Exception) {
                result[name] = ModuleHealth(name, "unknown", "error",
                    mapOf<String, Any>("exception" to e.message))
            }
        }
        return result.toMap()
    }

    fun getModuleInstance(name: String): ModuleInterface? = moduleInstances[name]
}

/** Módulo que quiere recibir notificaciones de baja memoria */
interface LowMemoryAware {
    fun onLowMemory()
}

/** Módulo que quiere recibir notificaciones de recorte de memoria */
interface TrimMemoryAware {
    fun onTrimMemory(level: Int)
}