package com.ceroX.app.modules.houri

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.fallback

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

    fun fallback(dispatcher: CoroutineDispatcher): CoroutineDispatcher = dispatcher.fallback
}