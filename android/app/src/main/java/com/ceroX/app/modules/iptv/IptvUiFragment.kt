package com.ceroX.app.modules.iptv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ceroX.app.R
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.Pantallas
import com.ceroX.app.databinding.FragmentIptvBinding
import com.ceroX.app.databinding.ItemCanalIptvBinding
import com.ceroX.app.databinding.ItemFuenteIptvBinding

/**
 * La pantalla de IPTV: las fuentes que instala el usuario, los canales que traen y
 * la reproducción.
 *
 * CÓMO CONSIGUE EL MÓDULO
 * -----------------------
 * Por [Pantallas.modulo], que es el único camino que queda: devuelve null en vez de
 * reventar y, si el módulo todavía no se inicializó —el arranque va en su propio hilo
 * desde la 1.0.1—, lo inicializa en segundo plano y llama a [pintar] al terminar. Una
 * pantalla vacía que dice por qué está vacía es un fallo visible; una excepción es un
 * cierre, que es lo que pasaba antes con seis pantallas.
 *
 * LO QUE AQUÍ NO HAY
 * ------------------
 * Ninguna lista de canales ni ningún «catálogo» de fuentes sugeridas. cero-X no lleva
 * fuentes dentro del APK: las pone el usuario, una por una, y se descargan al
 * cargarlas. Un buscador de listas recomendadas sería exactamente lo mismo con otro
 * nombre, y la licencia del contenido no la decide esta app.
 *
 * HILOS
 * -----
 * Descargar una lista y escribir el almacén van en su propio hilo ([enSegundoPlano]):
 * en el principal congelarían la interfaz, que es el fallo que tenía la app al
 * arrancar. La reproducción **no** va fuera: ExoPlayer comprueba desde qué hilo se le
 * llama y lanza si no es el suyo, y puede quedarse en el principal porque preparar y
 * empezar no bloquean —la red la atiende el propio reproductor—.
 */
class IptvUiFragment : Fragment() {

    private lateinit var binding: FragmentIptvBinding
    private val principal = Handler(Looper.getMainLooper())
    private val adaptadorFuentes = FuentesAdapter()
    private val adaptadorCanales = CanalesAdapter()

    private var modulo: IptvModule? = null
    private var reproductor: ReproductorIptv? = null
    private var fuentesActuales: List<Fuente> = emptyList()
    private var fuenteElegida: String? = null
    private var enMarcha: Canal? = null
    private var falloReproduccion: String? = null
    private var ultimoAviso: String? = null
    private var filtro = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentIptvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as CeroXApplication
        modulo = Pantallas.modulo(app, "iptv", IptvModule::class.java) {
            if (isAdded) pintar()
        }

        binding.listaFuentes.layoutManager = LinearLayoutManager(requireContext())
        binding.listaFuentes.adapter = adaptadorFuentes
        adaptadorFuentes.alTocar = { fuente ->
            fuenteElegida = fuente.nombre
            adaptadorFuentes.submit(fuentesActuales, fuenteElegida)
        }
        adaptadorFuentes.alQuitar = { fuente -> quitar(fuente) }

        binding.listaCanales.layoutManager = LinearLayoutManager(requireContext())
        binding.listaCanales.adapter = adaptadorCanales
        adaptadorCanales.alTocar = { canal -> reproducir(canal) }

        binding.botonAnadirFuente.setOnClickListener { anadir() }
        binding.botonCargar.setOnClickListener { cargar() }
        binding.botonReproducir.setOnClickListener { seguir() }
        binding.botonPausa.setOnClickListener { pausar() }
        binding.botonParar.setOnClickListener { pararTodo() }
        binding.busquedaCanales.doAfterTextChanged { texto ->
            filtro = texto?.toString()?.trim().orEmpty()
            pintarCanales()
        }

        pintar()
    }

    override fun onDestroyView() {
        // La superficie se va con la vista. Un reproductor vivo enganchado a una
        // superficie muerta se queda el decodificador de hardware, y el siguiente que
        // se cree no lo va a tener. Se libera sin crearlo: pedirle el reproductor al
        // módulo aquí lo construiría sólo para soltarlo.
        modulo?.liberarReproductor()
        reproductor = null
        enMarcha = null
        falloReproduccion = null
        super.onDestroyView()
    }

    // ── acciones ───────────────────────────────────────────────────────────

    private fun anadir() {
        val m = modulo ?: return
        val r = resources
        val nombre = binding.fuenteNombre.text.toString().trim()
        val url = binding.fuenteUrl.text.toString().trim()
        val huella = binding.fuenteHuella.text.toString().trim()
        // Se lee la opción marcada por defecto y no la otra: si se leen las dos,
        // una de las dos vistas queda sin usar, que es un adorno.
        val tipo = if (binding.tipoLista.isChecked) "m3u" else "xmltv"
        // Nada de estado de la pantalla dentro de este lambda: corre en otro hilo y
        // quien repinta es el principal. La fuente añadida se elige tocándola, que es
        // lo que dice el texto de estado.
        enSegundoPlano {
            val hecho = m.anadirFuente(nombre, url, tipo, huella.ifEmpty { null })
            if (hecho.motivo != null) {
                hecho.motivo
            } else {
                r.getString(R.string.iptv_fuente_anadida, hecho.fuente?.nombre ?: nombre)
            }
        }
    }

    private fun quitar(fuente: Fuente) {
        val m = modulo ?: return
        val r = resources
        val nombre = fuente.nombre
        enSegundoPlano {
            // Si la fuente quitada era la elegida, pintar() la descarta: ya no está en
            // la lista. Tocarlo aquí sería escribir estado desde otro hilo.
            val fallo = m.quitarFuente(nombre)
            fallo ?: r.getString(R.string.iptv_fuente_quitada, nombre)
        }
    }

    private fun cargar() {
        val m = modulo ?: return
        val r = resources
        val nombre = fuenteElegida
        if (nombre == null) {
            ultimoAviso = r.getString(R.string.iptv_elige_fuente)
            pintar()
            return
        }
        enSegundoPlano {
            val carga = m.cargar(nombre)
            if (carga.correcta) {
                r.getString(R.string.iptv_canales_cargados, carga.cuantos, nombre)
            } else {
                carga.motivo
            }
        }
    }

    private fun reproducir(canal: Canal) {
        val m = modulo ?: return
        falloReproduccion = null
        // En el hilo principal a propósito: ver el comentario de la clase.
        val fallo = m.reproducir(canal, binding.superficieVideo)
        if (fallo != null) {
            falloReproduccion = fallo
            enMarcha = null
        } else {
            enMarcha = canal
            // Ya existe: reproducir lo acaba de crear, así que esto no construye nada.
            reproductor = m.reproductor()
        }
        pintarReproduccion()
    }

    private fun seguir() {
        val r = reproductor
        if (r == null) {
            falloReproduccion = resources.getString(R.string.iptv_elige_canal)
            pintarReproduccion()
            return
        }
        falloReproduccion = r.empezar()
        pintarReproduccion()
    }

    private fun pausar() {
        reproductor?.pausar()
    }

    private fun pararTodo() {
        modulo?.liberarReproductor()
        reproductor = null
        enMarcha = null
        falloReproduccion = null
        pintarReproduccion()
    }

    /**
     * Trabajo que toca red o disco fuera del hilo principal, y repintado en él.
     *
     * [trabajo] devuelve el aviso que hay que enseñar. Si lanza, el aviso es la
     * excepción con su mensaje: un fallo que no se enseña es un botón que parece no
     * hacer nada, y esta app ya tuvo bastantes de ésos.
     */
    private fun enSegundoPlano(trabajo: () -> String?) {
        Thread({
            val aviso = try {
                trabajo()
            } catch (e: Exception) {
                val detalle = e.message
                if (detalle == null) e.javaClass.simpleName else e.javaClass.simpleName + ": " + detalle
            }
            principal.post {
                if (!isAdded) return@post
                ultimoAviso = aviso
                pintar()
            }
        }, "cerox-iptv-pantalla").start()
    }

    // ── pintado ────────────────────────────────────────────────────────────

    private fun pintar() {
        val m = modulo
        if (m == null) {
            binding.estado.text = getString(R.string.iptv_modulo_no_disponible)
            pintarReproduccion()
            return
        }
        val listado = m.fuentes()
        fuentesActuales = listado.fuentes
        if (listado.fuentes.none { it.nombre == fuenteElegida }) fuenteElegida = null
        adaptadorFuentes.submit(listado.fuentes, fuenteElegida)
        binding.estado.text = textoDeEstado(listado, m.canales().size)
        pintarCanales()
        pintarReproduccion()
    }

    private fun pintarCanales() {
        val m = modulo ?: return
        val visibles = if (filtro.isEmpty()) m.canales() else m.buscar(filtro)
        adaptadorCanales.submit(visibles)
    }

    private fun pintarReproduccion() {
        val fallo = falloReproduccion
        val canal = enMarcha
        binding.reproduccion.text = when {
            fallo != null -> fallo
            canal != null -> getString(R.string.iptv_reproduciendo, canal.titulo)
            else -> getString(R.string.iptv_sin_reproduccion)
        }
    }

    /**
     * Lo que se lee arriba. Primero el aviso de la última acción, porque es lo que el
     * usuario acaba de preguntar; después lo que diga el almacén; después cuántos
     * canales hay. Nunca un «listo» genérico: si algo falló, se enseña el motivo que
     * devolvió el módulo, que para eso lo devuelve.
     */
    private fun textoDeEstado(listado: Listado, cuantosCanales: Int): String {
        ultimoAviso?.let { return it }
        listado.motivo?.let { return it }
        if (listado.fuentes.isEmpty()) return getString(R.string.iptv_sin_fuentes)
        if (cuantosCanales > 0) {
            val de = fuenteElegida ?: listado.fuentes[0].nombre
            return getString(R.string.iptv_canales_cargados, cuantosCanales, de)
        }
        return getString(R.string.iptv_estado_inicial)
    }
}

/** Las fuentes instaladas. La elegida se marca con una flecha delante. */
private class FuentesAdapter : RecyclerView.Adapter<FuentesAdapter.Vista>() {

    var alTocar: (Fuente) -> Unit = {}
    var alQuitar: (Fuente) -> Unit = {}

    private var elementos: List<Fuente> = emptyList()
    private var elegida: String? = null

    fun submit(nuevos: List<Fuente>, elegida: String?) {
        elementos = nuevos
        this.elegida = elegida
        notifyDataSetChanged()
    }

    class Vista(val b: ItemFuenteIptvBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vista =
        Vista(ItemFuenteIptvBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = elementos.size

    override fun onBindViewHolder(holder: Vista, position: Int) {
        val f = elementos[position]
        val detalle = f.tipo + " · " + f.url
        holder.b.fuenteTitulo.text = f.nombre
        holder.b.fuenteDetalle.text = if (f.nombre == elegida) "▸ $detalle" else detalle
        holder.b.botonQuitarFuente.setOnClickListener { alQuitar(f) }
        holder.itemView.setOnClickListener { alTocar(f) }
    }
}

/** Los canales de la fuente cargada. */
private class CanalesAdapter : RecyclerView.Adapter<CanalesAdapter.Vista>() {

    var alTocar: (Canal) -> Unit = {}

    private var elementos: List<Canal> = emptyList()

    fun submit(nuevos: List<Canal>) {
        elementos = nuevos
        notifyDataSetChanged()
    }

    class Vista(val b: ItemCanalIptvBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vista =
        Vista(ItemCanalIptvBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = elementos.size

    override fun onBindViewHolder(holder: Vista, position: Int) {
        val c = elementos[position]
        val grupo = c.grupo
        holder.b.canalTitulo.text = c.titulo
        holder.b.canalDetalle.text = if (grupo == null) c.url else grupo + " · " + c.url
        holder.itemView.setOnClickListener { alTocar(c) }
    }
}
