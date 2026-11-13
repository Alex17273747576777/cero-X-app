package com.ceroX.app.modules.catalog

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ceroX.app.R
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.Pantallas
import com.ceroX.app.databinding.FragmentCatalogBinding

/**
 * Catálogo informativo con filtro combinado.
 *
 * La apertura es explícita y honesta: cada ficha dice qué es su enlace
 * (repositorio, sitio del producto, documentación, tienda) y la acción se llama
 * en consecuencia. Desde aquí sólo se abre el navegador; cero-X no descarga ni
 * instala nada del catálogo. Los directorios curados de los que salió el
 * catálogo van en su propio bloque, y las tiendas se filtran aparte de las apps.
 */
class CatalogUIFragment : Fragment() {
    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CatalogAdapter
    private var catalogModule: CatalogModule? = null
    private var categoria: String? = null
    private var consulta = ""

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCatalogBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as CeroXApplication
        catalogModule = Pantallas.modulo(app, "app-catalog", CatalogModule::class.java) {
            if (isAdded && _binding != null) aplicarFiltro()
        }
        adapter = CatalogAdapter(catalogModule?.aplicaciones().orEmpty(), ::abrirEnlace)
        poblarDirectorios()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        adapter.registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onChanged() = actualizarEstado()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = actualizarEstado()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = actualizarEstado()
        })

        binding.categoryFilter.setOnClickListener { elegirCategoria() }
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                consulta = query
                aplicarFiltro()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                consulta = newText
                aplicarFiltro()
                return true
            }
        })
        aplicarFiltro()
    }

    private fun elegirCategoria() {
        // Las categorías salen de las fichas. La lista fija que declaraba
        // healthCheck antes incluía «audio» y «servers», que no existen en los
        // datos, y omitía «server», «tracking» y «ereader», que sí existen.
        val categorias = catalogModule?.aplicaciones().orEmpty().map { it.category }.distinct().sorted()
        val opciones = listOf(ETIQUETA_TODAS) + categorias + ETIQUETA_TIENDAS
        AlertDialog.Builder(requireContext())
            .setTitle("Filtrar por categoría")
            .setItems(opciones.toTypedArray()) { _, indice ->
                categoria = when (indice) {
                    0 -> null
                    opciones.lastIndex -> FILTRO_TIENDAS
                    else -> categorias.getOrNull(indice - 1)
                }
                binding.categoryFilter.text = when (val c = categoria) {
                    null -> ETIQUETA_TODAS
                    FILTRO_TIENDAS -> ETIQUETA_TIENDAS
                    else -> c
                }
                aplicarFiltro()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Qué conjunto se está listando: las apps, o las tiendas aparte. */
    private fun baseActual(): List<CatalogApp> = when (categoria) {
        FILTRO_TIENDAS -> catalogModule?.tiendas().orEmpty()
        else -> catalogModule?.aplicaciones().orEmpty()
    }

    private fun aplicarFiltro() {
        if (_binding == null || !this::adapter.isInitialized) return
        val base = baseActual()
        val filtro = if (categoria == FILTRO_TIENDAS) null else categoria
        val filtradas = filtrarCatalogo(base, consulta, filtro)
        adapter.submitList(filtradas)
        actualizarEstado(filtradas.size, base.size)
    }

    private fun actualizarEstado(cantidad: Int = adapter.itemCount, total: Int = baseActual().size) {
        if (_binding == null) return
        val etiqueta = if (categoria == FILTRO_TIENDAS) "tiendas" else "apps"
        binding.categoryStatus.text = "$cantidad de $total $etiqueta" +
            (categoria?.takeIf { it != FILTRO_TIENDAS }?.let { " · $it" } ?: " · todas las categorías")
        binding.emptyView.visibility = if (cantidad == 0) View.VISIBLE else View.GONE
    }

    /**
     * Los directorios curados van en su propio bloque, encima de la lista: son
     * las fuentes del catálogo, no apps, y mezclarlos con ellas era justo lo que
     * hacía la lista plana anterior.
     */
    private fun poblarDirectorios() {
        if (_binding == null) return
        val densidad = resources.displayMetrics.density
        val contenedor = binding.directoriosLista
        contenedor.removeAllViews()
        for (d in catalogModule?.directoriosCurados().orEmpty()) {
            contenedor.addView(TextView(requireContext()).apply {
                text = "${d.nombre} · ${d.entradasMedidas} entradas medidas el ${d.fechaMedicion}\n" +
                    "${d.quePublica}\nLimitación: ${d.limitacion}"
                textSize = 12f
                minimumHeight = (48 * densidad).toInt()
                setPadding(0, (10 * densidad).toInt(), 0, (10 * densidad).toInt())
                contentDescription = "${accionPara(TipoEnlace.DIRECTORIO_CURADO)}: ${d.nombre}"
                setOnClickListener { abrirUrl(d.nombre, d.url) }
            })
        }
    }

    private fun abrirEnlace(app: CatalogApp) = abrirUrl(app.name, app.repository, app.estadoEnlace)

    /**
     * Abre un enlace en el navegador, y no hace nada más.
     *
     * Lo decide [decisionDeApertura], que es pura y está probada: se rechaza lo
     * que no sea HTTPS y se rechaza todo enlace que apunte a un binario
     * instalable, porque abrirlo provocaría una descarga implícita de un APK.
     * El catálogo no descarga ni instala: sólo enseña y abre.
     */
    private fun abrirUrl(
        nombre: String,
        url: String?,
        estado: EstadoEnlace = EstadoEnlace.SIN_VERIFICAR,
    ) {
        when (decisionDeApertura(url)) {
            DecisionApertura.SIN_ENLACE -> avisar("$nombre: la ficha no trae enlace")
            DecisionApertura.RECHAZADO_NO_HTTPS -> avisar("$nombre: el enlace no es HTTPS y no se abre")
            DecisionApertura.RECHAZADO_BINARIO_INSTALABLE ->
                avisar("$nombre: el enlace apunta a un binario instalable y cero-X no descarga APK")
            DecisionApertura.ABRIR_NAVEGADOR -> {
                if (estado == EstadoEnlace.MUERTO_404) {
                    avisar("$nombre: este enlace devolvió HTTP 404 en la auditoría del $FECHA_AUDITORIA")
                }
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(requireNotNull(url))))
                } catch (_: ActivityNotFoundException) {
                    avisar("No hay ninguna aplicación capaz de abrir el enlace")
                }
            }
        }
    }

    private fun avisar(mensaje: String) {
        if (_binding == null) return
        Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val ETIQUETA_TODAS = "Todas las categorías"
        const val ETIQUETA_TIENDAS = "Tiendas e instaladores (aparte de las apps)"
        const val FILTRO_TIENDAS = "__tiendas__"
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

class CatalogAdapter(
    items: List<CatalogApp>,
    private val abrir: (CatalogApp) -> Unit,
) : androidx.recyclerview.widget.ListAdapter<CatalogApp, CatalogAdapter.ViewHolder>(CatalogAppDiffCallback()) {
    init { submitList(items) }

    class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val name: android.widget.TextView = itemView.findViewById(R.id.catalog_app_name)
        val category: android.widget.TextView = itemView.findViewById(R.id.catalog_app_category)
        val status: android.widget.TextView = itemView.findViewById(R.id.catalog_app_status)
        val license: android.widget.TextView = itemView.findViewById(R.id.catalog_app_license)
        val features: android.widget.TextView = itemView.findViewById(R.id.catalog_app_features)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_catalog_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.name.text = item.name
        // Antes todas las filas decían «Abrir repositorio de …» y 27 de las 52
        // fichas no llevan a ningún repositorio.
        holder.category.text = "${item.category} · ${item.platform} · ${etiquetaDe(item.tipoEnlace)}"
        holder.status.text = buildString {
            append(item.status.name.lowercase())
            if (item.estadoEnlace == EstadoEnlace.MUERTO_404) append(" · enlace muerto (404)")
            if (item.enlaceArchivado) append(" · repo archivado")
        }
        holder.license.text = item.license
        holder.license.contentDescription = "Licencia ${item.license}" +
            (item.licenciaNota?.let { ". $it" } ?: "") +
            (item.licenciaFuente?.let { " Fuente: $it" } ?: "")
        holder.features.text = item.bestFeatures.joinToString(" • ", limit = 3)
        holder.itemView.contentDescription = "${accionPara(item.tipoEnlace)}: ${item.name}"
        holder.itemView.setOnClickListener { abrir(item) }
        holder.status.setTextColor(
            when (item.status) {
                CatalogStatus.ACTIVE -> android.graphics.Color.GREEN
                CatalogStatus.DISCONTINUED -> android.graphics.Color.RED
                CatalogStatus.BETA -> android.graphics.Color.YELLOW
                CatalogStatus.ARCHIVED -> android.graphics.Color.GRAY
            },
        )
    }
}

class CatalogAppDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<CatalogApp>() {
    override fun areItemsTheSame(oldItem: CatalogApp, newItem: CatalogApp): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: CatalogApp, newItem: CatalogApp): Boolean = oldItem == newItem
}
