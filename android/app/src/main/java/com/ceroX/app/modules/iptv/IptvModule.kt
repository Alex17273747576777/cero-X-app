package com.ceroX.app.modules.iptv

import android.content.Context
import android.view.SurfaceView
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface
import java.io.File

/**
 * Módulo de IPTV de cero-X. Se registra como `iptv` (el módulo 38).
 *
 * NEUTRO QUIERE DECIR ESTO
 * ------------------------
 * El módulo no trae ni un canal. Arranca con la lista de fuentes vacía, el usuario
 * añade las suyas, y cada una se descarga de fuera y se comprueba contra la huella
 * que declare su publicador. Nada de listas dentro del APK: lo vigilan dos pruebas
 * de `IptvTest`, una sobre `assets` y `res/raw` y otra sobre las URLs escritas a
 * mano en este paquete.
 *
 * QUÉ HACE EN CADA FASE
 * ---------------------
 * `initialize()` sólo guarda el contexto y crea el almacén de fuentes, que es un
 * objeto `File`: no abre red ni lee disco. Es a propósito —la app tuvo un ANR por
 * inicializar treinta y seis módulos haciendo trabajo pesado en el hilo principal, y
 * este no va a ser el que lo repita—. La red ocurre en [cargar], cuando lo pide el
 * usuario.
 *
 * Sin contexto no se puede guardar nada, así que `initialize()` devuelve false con el
 * motivo en el health check en vez de devolver true y quedarse sin almacén.
 */
class IptvModule : ModuleInterface() {

    override val name = "iptv"
    override val version = "0.1.0"
    override val dependencies: List<String> = emptyList()

    private val cargador = CargadorExterno()
    private var contexto: Context? = null
    private var almacen: AlmacenFuentes? = null
    private var reproductor: ReproductorIptv? = null
    private var canales: List<Canal> = emptyList()
    private var guia: ResultadoGuia? = null
    private var fuenteDeCanales: String? = null
    private var fuenteDeGuia: String? = null
    private var ultimoMotivo: String? = null

    override fun initialize(config: Map<String, Any>): Boolean {
        val c = config["context"] as? Context
        if (c == null) {
            ultimoMotivo = "No se inyectó el contexto: sin él no hay directorio privado " +
                "donde guardar las fuentes que añada el usuario"
            return false
        }
        contexto = c
        almacen = AlmacenFuentes(File(c.filesDir, NOMBRE_ALMACEN))
        _initialized = true
        return true
    }

    override fun start(): Boolean {
        if (!_initialized) return false
        _started = true
        return true
    }

    override fun stop(): Boolean {
        reproductor?.liberar()
        reproductor = null
        _started = false
        return true
    }

    override fun onDestroy() {
        // Un ExoPlayer sin soltar se queda el decodificador de hardware, y el
        // siguiente reproductor que se cree no lo va a tener.
        reproductor?.liberar()
        reproductor = null
        super.onDestroy()
    }

    override fun healthCheck(): ModuleHealth {
        val listado = almacen?.listar()
        return ModuleHealth(
            name, version,
            if (_started) "healthy" else "stopped",
            mapOf(
                "almacen_creado" to (almacen != null),
                "fuentes" to (listado?.fuentes?.size ?: 0),
                "motivo_del_almacen" to listado?.motivo,
                "canales_cargados" to canales.size,
                "fuente_de_canales" to fuenteDeCanales,
                "programas_de_guia" to (guia?.programas?.size ?: 0),
                "descartados_de_guia" to (guia?.descartados?.size ?: 0),
                "fuente_de_guia" to fuenteDeGuia,
                "reproductor_creado" to (reproductor != null),
                "motivo_reproductor" to reproductor?.ultimoMotivo(),
                "ultimo_motivo" to ultimoMotivo,
            ),
        )
    }

    // ── fuentes ──────────────────────────────────────────────────────────────

    /** Las fuentes del usuario, y el motivo si el almacén no se pudo leer. */
    fun fuentes(): Listado {
        val a = almacen
            ?: return Listado(emptyList(), ultimoMotivo ?: "El módulo no está inicializado")
        return a.listar()
    }

    fun anadirFuente(nombre: String, url: String, tipo: String, huellaEsperada: String?): Anadido {
        val a = almacen ?: return Anadido(null, "El módulo no está inicializado: no hay almacén")
        return a.anadir(nombre, url, tipo, huellaEsperada)
    }

    fun quitarFuente(nombre: String): String? {
        val a = almacen ?: return "El módulo no está inicializado: no hay almacén"
        return a.quitar(nombre)
    }

    /**
     * Descarga una fuente y la lee según su tipo.
     *
     * El orden importa: primero la política de URLs, luego la descarga con su tope,
     * luego la huella y sólo entonces el parseo. Si la fuente declara una huella y
     * no cuadra, no se usa nada de lo descargado; si no declara ninguna, se anota en
     * el motivo que no se pudo comprobar el origen, que no es lo mismo que decir que
     * está bien.
     */
    fun cargar(nombre: String): Carga {
        val a = almacen ?: return Carga(false, "El módulo no está inicializado: no hay almacén", 0, null)
        val fuente = a.listar().fuentes.firstOrNull { it.nombre.equals(nombre.trim(), ignoreCase = true) }
            ?: return Carga(false, "No hay ninguna fuente llamada '$nombre'", 0, null)

        val descarga = cargador.descargar(fuente.url)
        if (!descarga.correcta) {
            ultimoMotivo = descarga.motivo
            return Carga(false, descarga.motivo, 0, descarga.huella)
        }
        val contenido = descarga.contenido
            ?: return Carga(false, "La descarga dio por buena una respuesta sin contenido", 0, descarga.huella)

        // Si la descarga dio por buena la respuesta, trae la huella: se calcula de los
        // bytes recién leídos. Que falte querría decir que MessageDigest falló, y eso
        // no se tapa pasándole una cadena vacía al verificador.
        val huella = descarga.huella ?: return Carga(
            false,
            "La descarga se dio por buena pero no trajo huella, así que no hay forma de comprobar el origen",
            0,
            null,
        )
        val falloHuella = Verificador.comprobar(fuente.huellaEsperada, huella)
        if (fuente.huellaEsperada != null && falloHuella != null) {
            ultimoMotivo = falloHuella
            return Carga(false, falloHuella, 0, huella)
        }
        a.registrarHuella(fuente.nombre, huella)

        return when (fuente.tipo) {
            "xmltv" -> {
                val g = LectorXmltv.leer(contenido, fuente.nombre)
                guia = g
                fuenteDeGuia = fuente.nombre
                ultimoMotivo = g.motivo ?: falloHuella
                Carga(g.correcta, g.motivo ?: falloHuella, g.programas.size, huella)
            }

            else -> {
                val r = ListaM3U.parsear(contenido, fuente.nombre)
                if (r.esListaHls) {
                    // No es una lista de canales: es un flujo. Se dice y no se guarda
                    // como si fueran canales.
                    ultimoMotivo = r.motivo
                    return Carga(false, r.motivo, 0, huella)
                }
                canales = r.canales
                fuenteDeCanales = fuente.nombre
                ultimoMotivo = r.motivo
                Carga(r.correcta, r.motivo, r.canales.size, huella)
            }
        }
    }

    // ── contenido ────────────────────────────────────────────────────────────

    fun canales(): List<Canal> = canales

    fun buscar(texto: String): List<Canal> = ListaM3U.buscar(canales, texto)

    fun grupos(): List<String> = ListaM3U.grupos(canales)

    fun guia(): ResultadoGuia? = guia

    /** Lo que se emite ahora en un canal de la guía, si la guía está cargada. */
    fun enEmision(canalId: String, ahora: Long): Programa? =
        Guia.enEmision(guia?.programas ?: emptyList(), canalId, ahora)

    // ── reproducción ─────────────────────────────────────────────────────────

    /**
     * El reproductor, creado la primera vez que se pide.
     *
     * No se crea en `initialize()`: crear un ExoPlayer reserva recursos de
     * decodificación que no hacen falta hasta que el usuario pulsa un canal.
     */
    fun reproductor(): ReproductorIptv? {
        val existente = reproductor
        if (existente != null) return existente
        val c = contexto ?: return null
        val nuevo = ReproductorIptv(c)
        val fallo = nuevo.crear()
        if (fallo != null) {
            ultimoMotivo = fallo
            return null
        }
        reproductor = nuevo
        return nuevo
    }

    /**
     * Prepara y reproduce un canal. Null si salió; la razón si no.
     *
     * La superficie puede ser null: hay canales que son sólo audio, y en ese caso se
     * reproduce sin vídeo en vez de fallar.
     */
    fun reproducir(canal: Canal, superficie: SurfaceView?): String? {
        val r = reproductor() ?: return ultimoMotivo ?: "No se pudo crear el reproductor"
        val fallo = r.preparar(canal)
        if (fallo != null) return fallo
        r.adjuntar(superficie)
        return r.empezar()
    }

    fun parar() {
        reproductor?.pausar()
    }

    companion object {
        /** En el directorio privado de la app: no es contenido del usuario que haya
         *  que exponer, y no viaja en el APK. */
        const val NOMBRE_ALMACEN = "iptv-fuentes.json"
    }
}

/** Lo que dio una carga de fuente. */
data class Carga(
    val correcta: Boolean,
    val motivo: String?,
    /** Canales o programas, según el tipo de fuente. */
    val cuantos: Int,
    /** La huella de lo descargado, que es lo que permite notar si cambió. */
    val huella: String?,
)
