package com.ceroX.app.core.engine

/**
 * Las reglas del arranque, en un sitio puro y probable.
 *
 * Nace con la 1.0.1 («arranque honesto»). Antes, la política de qué se inicia al
 * abrir vivía repartida: `CeroXApplication.onCreate` inicializaba los 30 módulos
 * en el hilo principal, y los que cargan librerías nativas de ONNX o modelos de
 * cientos de megas lo hacían ahí mismo, con reintentos que duermen el hilo. En un
 * teléfono donde un solo módulo fallara, la suma pasaba del timeout del sistema:
 * eso es el ANR «cero-X sigue sin funcionar» que se veía al instalar.
 *
 * Aquí no hay Context, ni Thread, ni IO: sólo hechos y texto. Por eso puede
 * probarlo `testDebugUnitTest` en la JVM del CI.
 */
object ReglasArranque {

    /**
     * Módulos que NO se inician al abrir: cargan runtime o modelos ONNX que
     * cuestan segundos y megas. Se quedan perezosos y el panel los inicia al
     * pedirlos. Los nombres son los de `ModuleMetadata` en
     * `CeroXApplication.registerModules`; una prueba exige que sigan existiendo
     * ahí, para que esta lista no derive en silencio.
     *
     * Ojo con los nombres: `KisaraUpscaler` es una CLASE que usa el colorizador,
     * no un módulo registrado, así que no aparece aquí aunque cargue ONNX. La
     * app registra 36 módulos; cinco son pesados.
     */
    val PESADOS: Set<String> = setOf(
        "kisara-neural-colorizer",
        "kisara-ocr-translator",
        "neural-ai",
        "ai-hub",
        "ai-training",
    )

    fun esPesado(name: String): Boolean = name in PESADOS

    /**
     * Cómo se llama cada estado cuando lo ve una persona. Sin adornos: un módulo
     * que falló dice «falló», no desaparece de la lista.
     */
    fun descripcionEstado(status: ModuleStatus, pesado: Boolean): String = when (status) {
        ModuleStatus.UNLOADED -> if (pesado) "pendiente: se inicia al pedirlo" else "sin iniciar"
        ModuleStatus.INITIALIZED -> "listo"
        ModuleStatus.STARTED -> "funcionando"
        ModuleStatus.STOPPED -> "parado"
        ModuleStatus.ERROR -> "falló"
        else -> status.name
    }

    /** Una fila del panel: lo que el gestor sabe, tal cual. */
    data class ResumenModulo(
        val nombre: String,
        val version: String,
        val estado: String,
        val reintentos: Int,
        val pesado: Boolean,
    )
}
