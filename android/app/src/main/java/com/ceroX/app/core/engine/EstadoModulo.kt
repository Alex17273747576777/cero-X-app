package com.ceroX.app.core.engine

/**
 * Los dos tipos que describen el estado de un módulo.
 *
 * Estaban declarados al final de `CoreEngine.kt`, el motor «legacy» que está borrado
 * desde el commit anterior porque no registraba nada y seis pantallas se cerraban al
 * pedirle su módulo. Al borrarlo se fueron con él dos tipos que usa media
 * aplicación —`ModuleInterface.healthCheck()`, `ModuleLifecycleManager`,
 * `ModuleRegistry.updateStatus()`, el panel de arranque— y el build se rompió con
 * «Unresolved reference: ModuleStatus».
 *
 * La lección no es «no borrar el motor», que estaba roto de verdad, sino que un
 * fichero no debe ser el sitio donde viven tipos que no le pertenecen. Aquí están
 * solos, en el paquete donde siempre se importaron (`core.engine`), y el import de
 * quien los usa no cambia.
 */

/**
 * Ciclo de vida de un módulo.
 *
 * `UNLOADED` es el estado inicial tras registrarse; `INITIALIZED` cuando su
 * `initialize()` devolvió true; `STARTED` cuando además arrancó; `STOPPED` y
 * `ERROR` se explican solos. El panel de arranque enseña estos estados tal cual los
 * lee del gestor.
 */
enum class ModuleStatus {
    UNLOADED, INITIALIZED, STARTED, STOPPED, ERROR
}

/**
 * Resultado del `healthCheck()` de un módulo.
 *
 * `status` es texto libre y no el enum de arriba a propósito: cada módulo informa lo
 * que le importa ("healthy", "stopped", "degraded" en el de licencia) y `details`
 * lleva las claves concretas. Quien lo pinta no debe dar por supuesto un vocabulario
 * cerrado; el panel muestra lo que venga.
 */
data class ModuleHealth(
    val name: String,
    val version: String,
    val status: String,
    val details: Map<String, Any?> = emptyMap()
)
