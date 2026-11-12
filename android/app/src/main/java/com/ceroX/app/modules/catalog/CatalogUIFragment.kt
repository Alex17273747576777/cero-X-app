package com.ceroX.app.modules.catalog

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ceroX.app.R
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.Pantallas
import com.ceroX.app.databinding.FragmentCatalogBinding

/** Catálogo informativo con filtro combinado y apertura explícita del repositorio. */
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
        adapter = CatalogAdapter(catalogModule?.listApps().orEmpty(), ::abrirRepositorio)
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
        val categorias = catalogModule?.listApps().orEmpty().map { it.category }.distinct().sorted()
        val opciones = listOf("Todas las categorías") + categorias
        AlertDialog.Builder(requireContext())
            .setTitle("Filtrar por categoría")
            .setItems(opciones.toTypedArray()) { _, indice ->
                categoria = categorias.getOrNull(indice - 1)
                binding.categoryFilter.text = categoria ?: "Todas las categorías"
                aplicarFiltro()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun aplicarFiltro() {
        if (_binding == null || !this::adapter.isInitialized) return
        val filtradas = filtrarCatalogo(catalogModule?.listApps().orEmpty(), consulta, categoria)
        adapter.submitList(filtradas)
        actualizarEstado(filtradas.size)
    }

    private fun actualizarEstado(cantidad: Int = adapter.itemCount) {
        if (_binding == null) return
        val total = catalogModule?.listApps()?.size ?: 0
        binding.categoryStatus.text = "$cantidad de $total apps" +
            (categoria?.let { " · $it" } ?: " · todas las categorías")
        binding.emptyView.visibility = if (cantidad == 0) View.VISIBLE else View.GONE
    }

    private fun abrirRepositorio(app: CatalogApp) {
        val url = app.repository
        if (url.isNullOrBlank() || !url.startsWith("https://")) {
            Toast.makeText(requireContext(), "${app.name}: no hay enlace HTTPS verificado", Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "No hay ninguna aplicación capaz de abrir el enlace", Toast.LENGTH_LONG).show()
        }
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
        holder.category.text = "${item.category} · ${item.platform}"
        holder.status.text = item.status.name.lowercase()
        holder.license.text = item.license
        holder.features.text = item.bestFeatures.joinToString(" • ", limit = 3)
        holder.itemView.contentDescription = "Abrir repositorio de ${item.name}"
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
