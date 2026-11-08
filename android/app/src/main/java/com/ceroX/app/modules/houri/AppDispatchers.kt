package com.ceroX.app.modules.houri

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * AppDispatchers — Foundation de concurrencia.
 * Basado en PineappleTwilight/houri AppDispatchers (Metro + Injekt DI pattern).
 */
@ExperimentalCoroutinesApi
object AppDispatchers {

    // Dispatcher para operaciones de red (IO intensivo)
    val Network: CoroutineDispatcher = Dispatchers.IO

    // Dispatcher para operaciones de lectura de disco (IO intensivo)
    val DiskIO: CoroutineDispatcher = Dispatchers.IO

    // Dispatcher para procesamiento de CPU (decodificación, upscaling, IA)
    val Cpu: CoroutineDispatcher = Dispatchers.Default

    // Dispatcher para UI principal
    val Main: CoroutineDispatcher = Dispatchers.Main.immediate

    // Dispatcher para base de datos (Room)
    val Database: CoroutineDispatcher = Dispatchers.IO

    // Dispatcher para WebGPU renderizado
    val Render: CoroutineDispatcher = Dispatchers.Default

    // Dispatcher para descargas en background
    val Download: CoroutineDispatcher = Dispatchers.IO

    /**
     * Version limitada de un dispatcher, para no saturar un movil de 2 nucleos.
     *
     * Antes habia aquí `dispatcher.fallback`, que no existe en kotlinx-coroutines:
     * la libreria no ofrece ningun `fallback`. Lo que si existe desde la 1.6 es
     * `limitedParallelism(n)`, que es lo que de verdad hace falta para acotar.
     */
    fun limited(dispatcher: CoroutineDispatcher, parallelism: Int): CoroutineDispatcher =
        dispatcher.limitedParallelism(parallelism.coerceAtLeast(1))
}