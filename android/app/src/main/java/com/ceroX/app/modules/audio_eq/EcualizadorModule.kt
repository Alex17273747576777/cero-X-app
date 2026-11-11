package com.ceroX.app.modules.audio_eq

import android.content.Context
import android.media.AudioManager
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface
import com.ceroX.app.modules.audio.AudioModule

/**
 * Módulo de ecualizador de cero-X.
 *
 * Se registra como `audio-eq` y no depende del módulo `audio`: puede ecualizar la
 * mezcla de salida del dispositivo (sesión 0), que es lo que hace un ecualizador
 * independiente, o la sesión de un reproductor concreto.
 *
 * CÓMO SE ADJUNTA
 * ---------------
 * Adjuntar efectos requiere un id de sesión, y hasta que no hay audio sonando no
 * está claro cuál usar. Por eso `initialize()` no adjunta nada: sería adjuntarse a
 * una sesión que quizá no existe. Se adjunta al pedirlo, con [adjuntarSalida] para
 * la mezcla del sistema o con [adjuntarSesion] para un reproductor, y el estado
 * queda en [estado].
 *
 * LO QUE NO HACE
 * --------------
 * No procesa audio por su cuenta: todo va por `android.media.audiofx`, o sea que el
 * trabajo lo hace el DSP del dispositivo. Cuando el dispositivo no tiene DSP, o el
 * fabricante no implementa el efecto, el resultado lo dice y no hay ecualización.
 */
class EcualizadorModule : ModuleInterface() {

    override val name = "audio-eq"
    override val version = "0.1.0"
    override val dependencies: List<String> = emptyList()

    private val ecualizador = Ecualizador()
    private var contexto: Context? = null
    private var adjunto: Adjunto? = null
    private var perfilActivo: String? = null
    private var graves: Int = 0
    private var envolvente: Int = 0
    private var gananciaFina: Int = 0
    private var intentosDeAdjuntar = 0

    override fun initialize(config: Map<String, Any>): Boolean {
        contexto = config["context"] as? Context
        _initialized = true
        return true
    }

    override fun start(): Boolean {
        if (!_initialized) return false
        _started = true
        return true
    }

    override fun stop(): Boolean {
        ecualizador.liberar()
        adjunto = null
        perfilActivo = null
        _started = false
        return true
    }

    override fun onDestroy() {
        // Los efectos del sistema son recursos compartidos: si no se sueltan, el
        // siguiente que los pida se queda sin ellos.
        ecualizador.liberar()
        super.onDestroy()
    }

    override fun healthCheck(): ModuleHealth = ModuleHealth(
        name, version,
        if (_started) "healthy" else "stopped",
        mapOf(
            "efectos_adjuntos" to (adjunto?.activo == true),
            "sesion" to (adjunto?.sesion?.toString() ?: "ninguna"),
            "intentos_de_adjuntar" to intentosDeAdjuntar,
            "bandas" to (adjunto?.bandas?.size ?: 0),
            "presets_del_sistema" to (adjunto?.presetsDelSistema?.size ?: 0),
            "graves_soportados" to (adjunto?.gravesSoportados == true),
            "envolvente_soportada" to (adjunto?.envolventeSoportada == true),
            "ganancia_fina_disponible" to (adjunto?.gananciaFinaDisponible == true),
            "perfil_activo" to (perfilActivo ?: "ninguno"),
            "perfiles_disponibles" to Perfiles.todos.size,
            "motivo_sin_efectos" to (adjunto?.motivo ?: "no se ha intentado adjuntar"),
            "contexto_inyectado" to (contexto != null),
        ),
    )

    /**
     * Se adjunta a la mezcla de salida del dispositivo (sesión 0).
     *
     * Es lo que afecta a todo lo que suena, incluidos otros reproductores. Hace
     * falta el permiso `MODIFY_AUDIO_SETTINGS`; sin él, el resultado lleva el
     * motivo y `activo` sale en false.
     */
    fun adjuntarSalida(): Adjunto = adjuntarSesion(0)

    /** Se adjunta a una sesión concreta, por ejemplo la del reproductor de audio. */
    fun adjuntarSesion(audioSession: Int): Adjunto {
        intentosDeAdjuntar++
        val resultado = ecualizador.adjuntar(audioSession)
        adjunto = resultado
        if (!resultado.activo) perfilActivo = null
        return resultado
    }

    /**
     * Se adjunta a la sesión del reproductor de cero-X si la hay.
     *
     * Recibe la instancia que devolvió `CeroXApplication.getModuleInstance("audio")`
     * —que es como el resto de pantallas consigue módulos— y le pregunta su id de
     * sesión. Si ese módulo no existe, no está inicializado o no tiene reproductor,
     * devuelve `null`: no se cae a la mezcla de salida sin decirlo, porque no es lo
     * mismo ecualizar la app que ecualizar el dispositivo entero.
     */
    fun adjuntarAlReproductor(moduloAudio: ModuleInterface?): Adjunto? {
        val sesion = (moduloAudio as? AudioModule)?.audioSessionId() ?: return null
        return adjuntarSesion(sesion)
    }

    /** Perfiles disponibles, en el orden en que se enseñan. */
    fun perfiles(): List<String> = Perfiles.nombres()

    /**
     * Aplica un perfil a las bandas reales del dispositivo.
     *
     * Devuelve `null` si se aplicó. Si no hay nada adjunto, devuelve el motivo en
     * vez de decir que sí: un perfil aplicado a un ecualizador que no existe no se
     * oye, y enseñarlo como aplicado es justo lo que este proyecto viene quitando.
     */
    fun aplicarPerfil(nombre: String): String? {
        val perfil = Perfiles.porNombre(nombre) ?: return "No existe ningun perfil llamado $nombre"
        if (adjunto?.activo != true) {
            return "No hay efectos adjuntos: " + (adjunto?.motivo ?: "no se ha intentado adjuntar")
        }
        val fallo = ecualizador.aplicarPerfil(perfil)
        if (fallo != null) return fallo
        perfilActivo = nombre
        return null
    }

    /** Bandas con su nivel actual, leídas del DSP. Vacía si no hay nada adjunto. */
    fun bandas(): List<Banda> = ecualizador.bandas()

    /** La curva del perfil sobre las bandas reales, antes de aplicarla. */
    fun curvaDe(nombre: String): List<Int> {
        val perfil = Perfiles.porNombre(nombre) ?: return emptyList()
        return Perfiles.curvaPara(perfil, bandas())
    }

    fun fijarBanda(indice: Int, mb: Int): String? {
        val fallo = ecualizador.ponerNivel(indice, mb)
        // Al tocar una banda a mano el perfil deja de ser lo que suena.
        if (fallo == null) perfilActivo = null
        return fallo
    }

    fun fijarGraves(fuerza: Int): String? {
        val fallo = ecualizador.fijarGraves(fuerza)
        if (fallo == null) graves = fuerza.coerceIn(0, 1000)
        return fallo
    }

    fun fijarEnvolvente(fuerza: Int): String? {
        val fallo = ecualizador.fijarEnvolvente(fuerza)
        if (fallo == null) envolvente = fuerza.coerceIn(0, 1000)
        return fallo
    }

    fun fijarGananciaFina(mb: Int): String? {
        val fallo = ecualizador.fijarGananciaFina(mb)
        if (fallo == null) gananciaFina = mb.coerceIn(-1500, 1500)
        return fallo
    }

    /**
     * Volumen del sistema y ganancia fina, o `null` si no se inyectó el contexto.
     *
     * El contexto llega por `config["context"]`, que inyecta el gestor de ciclo de
     * vida al inicializar. Sin él no hay `AudioManager`, y se dice en vez de
     * devolver un estado con ceros.
     */
    fun volumen(): EstadoVolumen? {
        val c = contexto ?: return null
        val gestor = c.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return PasosDeVolumen(gestor).estado(ecualizador.gananciaFinaActual())
    }

    /** Lo que hay adjunto ahora mismo, para la interfaz. */
    fun estado(): Adjunto? = adjunto

    fun ajustesActuales(): AjustesEq = AjustesEq(
        perfil = perfilActivo,
        graves = graves,
        envolvente = envolvente,
        gananciaFinaMb = gananciaFina,
        bandas = bandas(),
    )
}

data class AjustesEq(
    val perfil: String?,
    val graves: Int,
    val envolvente: Int,
    val gananciaFinaMb: Int,
    val bandas: List<Banda>,
)
