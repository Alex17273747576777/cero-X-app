package com.ceroX.app.modules.web

import android.content.Context
import com.ceroX.app.core.AlmacenExterno
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/** Estado del módulo web y lectura de sus reglas externas. */
class WebModule : ModuleInterface() {
    override val name = "web"
    override val version = "0.2.0"
    override val dependencies: List<String> = emptyList()

    var motor: String = MotorBusqueda.POR_DEFECTO
    var baseSearx: String = ""

    @Volatile
    var reglasExtra: List<String> = emptyList()
        private set

    @Volatile
    var motivoReglas: String = "todavía no se ha leído la lista del usuario"
        private set

    override fun initialize(config: Map<String, Any>): Boolean {
        (config["context"] as? Context)?.let { recargarReglas(it) }
        _initialized = true
        return true
    }

    /**
     * Lee `web/bloqueo.txt` del almacenamiento externo aprobado. Si no existe,
     * crea una plantilla comentada; nunca cae al almacenamiento interno.
     */
    fun recargarReglas(contexto: Context): Int {
        val archivo = AlmacenExterno.archivo(contexto, "web/bloqueo.txt")
        if (archivo == null) {
            reglasExtra = emptyList()
            motivoReglas = AlmacenExterno.estado(contexto)
            return 0
        }
        return runCatching {
            if (!archivo.isFile) {
                archivo.writeText("# Un dominio por línea. Ejemplo: anuncios.example\n")
            }
            reglasExtra = Bloqueador.analizarLista(archivo.readText())
            motivoReglas = "${reglasExtra.size} reglas propias en ${archivo.absolutePath}"
            reglasExtra.size
        }.getOrElse {
            reglasExtra = emptyList()
            motivoReglas = "No se pudo leer la lista propia: ${it.javaClass.simpleName}"
            0
        }
    }

    override fun start(): Boolean {
        _started = true
        return true
    }

    override fun stop(): Boolean {
        _started = false
        return true
    }

    override fun healthCheck(): ModuleHealth = ModuleHealth(
        name, version,
        if (_started) "healthy" else "stopped",
        mapOf<String, Any?>(
            "motor" to motor,
            "reglas_incluidas" to Bloqueador.INCLUIDAS.size,
            "reglas_usuario" to reglasExtra.size,
            "estado_lista" to motivoReglas,
            "politica_cookies" to Privacidad.POR_DEFECTO.name,
            "anti_muro" to "opcional, apagado por defecto",
        ),
    )
}
