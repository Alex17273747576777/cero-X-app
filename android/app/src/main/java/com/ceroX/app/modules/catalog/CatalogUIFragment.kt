package com.ceroX.app.modules.catalog

import com.ceroX.app.R

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ceroX.app.databinding.FragmentCatalogBinding

class CatalogUIFragment : Fragment() {

    private lateinit var binding: FragmentCatalogBinding
    private lateinit var adapter: CatalogAdapter
    private lateinit var catalogModule: CatalogModule

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCatalogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Obtener instancia del módulo de catálogo desde la aplicación
        val app = requireActivity().application as com.ceroX.app.core.CeroXApplication
        catalogModule = app.coreEngine.registry.getInstance("app-catalog") as CatalogModule

        adapter = CatalogAdapter(catalogModule.listApps())
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // Filtro por categoría
        binding.categoryFilter.setOnClickListener {
            // Placeholder: mostrar filtro de categorías
        }

        // Búsqueda
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                val results = catalogModule.searchApps(query)
                adapter.submitList(results)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                if (newText.isEmpty()) {
                    adapter.submitList(catalogModule.listApps())
                } else {
                    val results = catalogModule.searchApps(newText)
                    adapter.submitList(results)
                }
                return true
            }
        })
    }
}

class CatalogAdapter(private var items: List<CatalogApp>) :
    androidx.recyclerview.widget.ListAdapter<CatalogApp, CatalogAdapter.ViewHolder>(
        CatalogAppDiffCallback()
    ) {

    class ViewHolder(itemView: android.view.View) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val name: android.widget.TextView = itemView.findViewById(R.id.catalog_app_name)
        val category: android.widget.TextView = itemView.findViewById(R.id.catalog_app_category)
        val status: android.widget.TextView = itemView.findViewById(R.id.catalog_app_status)
        val license: android.widget.TextView = itemView.findViewById(R.id.catalog_app_license)
        val features: android.widget.TextView = itemView.findViewById(R.id.catalog_app_features)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_catalog_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.name.text = item.name
        holder.category.text = item.category
        holder.status.text = item.status.name.lowercase()
        holder.license.text = item.license
        holder.features.text = item.bestFeatures.joinToString(" • ", limit = 3)

        holder.status.setTextColor(
            when (item.status) {
                CatalogStatus.ACTIVE -> android.graphics.Color.GREEN
                CatalogStatus.DISCONTINUED -> android.graphics.Color.RED
                CatalogStatus.BETA -> android.graphics.Color.YELLOW
                CatalogStatus.ARCHIVED -> android.graphics.Color.GRAY
            }
        )
    }

    fun submitList(newList: List<CatalogApp>) {
        items = newList
        submitList(newList)
    }
}

class CatalogAppDiffCallback :
    androidx.recyclerview.widget.DiffUtil.ItemCallback<CatalogApp>() {
    override fun areItemsTheSame(oldItem: CatalogApp, newItem: CatalogApp): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: CatalogApp, newItem: CatalogApp): Boolean =
        oldItem == newItem
}