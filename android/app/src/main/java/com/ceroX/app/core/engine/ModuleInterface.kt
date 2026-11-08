package com.ceroX.app.core.engine

import android.content.ComponentCallbacks2

/**
 * Base interface that every cero-X module must implement.
 *
 * Estabilizado con:
 *   • Callbacks de ciclo de vida automáticos (onLowMemory, onTrimMemory, onDestroy)
 *   • Gestión de estado interna (initialized/started)
 *   • Validación de configuración
 */
abstract class ModuleInterface : ComponentCallbacks2 {

    open val name: String = "base"
    open val version: String = "0.0.0"
    open val dependencies: List<String> = emptyList()

    protected var _initialized = false
    protected var _started = false

    abstract fun initialize(config: Map<String, Any>): Boolean
    abstract fun start(): Boolean
    abstract fun stop(): Boolean
    abstract fun healthCheck(): ModuleHealth

    /** Verifica si el módulo está inicializado */
    fun isInitialized(): Boolean = _initialized

    /** Verifica si el módulo está iniciado */
    fun isStarted(): Boolean = _started

    /** Callback cuando el módulo está a punto de ser destruido */
    open fun onDestroy() {
        stop()
    }

    /** Callback de baja memoria del sistema */
    override open fun onLowMemory() {
        // Los módulos pueden sobrescribir para liberar recursos
    }

    /**
     * Callback obligatorio de ComponentCallbacks.
     *
     * ModuleInterface implementa ComponentCallbacks2, y esa interfaz exige
     * onConfigurationChanged. Sin esta implementación por defecto, los 39 módulos
     * heredaban un abstracto sin resolver y ninguno de ellos compilaba.
     * La base lo absorbe aquí para que cada módulo no tenga que repetirlo.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        // Los módulos que dependan del idioma o del tema pueden sobrescribirlo.
    }

    /** Callback de recorte de memoria */
    override open fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // Liberar recursos no críticos
            }
        }
    }

    fun getMetadata() = ModuleMetadata(name, version, dependencies,
        if (_started) ModuleStatus.STARTED else if (_initialized) ModuleStatus.INITIALIZED else ModuleStatus.UNLOADED)
}