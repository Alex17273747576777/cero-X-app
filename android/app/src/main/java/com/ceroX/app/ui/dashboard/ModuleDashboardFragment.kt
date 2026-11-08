package com.ceroX.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.ceroX.app.R
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.engine.ModuleStatus
import com.ceroX.app.databinding.FragmentModuleDashboardBinding
import com.ceroX.app.ui.main.MainActivity

class ModuleDashboardFragment : Fragment() {

    private lateinit var binding: FragmentModuleDashboardBinding
    private lateinit var viewModel: DashboardViewModel
    private lateinit var adapter: ModuleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentModuleDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[DashboardViewModel::class.java]

        val app = requireActivity().application as CeroXApplication
        val engine = app.coreEngine

        // Setup RecyclerView
        adapter = ModuleAdapter(engine) { moduleName ->
            // Handle module click
            val status = engine.registry.getMetadata(moduleName)?.status
            if (status == ModuleStatus.STARTED) {
                // Module is running, could show details
            }
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // Observe module health
        viewModel.moduleHealth.observe(viewLifecycleOwner) { healthMap ->
            adapter.mostrarSalud(healthMap.entries.toList())
        }

        // Refresh button
        binding.refreshButton.setOnClickListener {
            val healthMap = engine.healthCheckAll()
            viewModel.moduleHealth.value = healthMap
        }

        // Initial load
        val healthMap = engine.healthCheckAll()
        viewModel.moduleHealth.value = healthMap
    }
}

/** Simple adapter for module list */
class ModuleAdapter(
    private val engine: com.ceroX.app.core.engine.CoreEngine,
    private val onModuleClick: (String) -> Unit
) : androidx.recyclerview.widget.ListAdapter<ModuleItem, ModuleAdapter.ModuleViewHolder>(
    ModuleItemDiffCallback()
) {

    class ModuleViewHolder(itemView: android.view.View) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val name: android.widget.TextView = itemView.findViewById(R.id.module_name)
        val status: android.widget.TextView = itemView.findViewById(R.id.module_status)
        val version: android.widget.TextView = itemView.findViewById(R.id.module_version)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModuleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_module, parent, false)
        return ModuleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModuleViewHolder, position: Int) {
        val item = getItem(position)
        holder.name.text = item.name
        holder.version.text = "v${item.version}"
        holder.status.text = item.status
        holder.status.setTextColor(
            when (item.status) {
                "healthy" -> android.graphics.Color.GREEN
                "stopped" -> android.graphics.Color.RED
                else -> android.graphics.Color.YELLOW
            }
        )
        holder.itemView.setOnClickListener { onModuleClick(item.name) }
    }

    /**
     * Convierte el mapa de salud en la lista que pinta el adaptador.
     *
     * Se llamaba submitList y chocaba con la de ListAdapter (misma firma JVM),
     * además de llamarse a sí misma. Ahora es un método con nombre propio que
     * al final delega en la submitList real de la clase base.
     */
    fun mostrarSalud(items: List<Map.Entry<String, com.ceroX.app.core.engine.ModuleHealth>>) {
        submitList(items.map { (name, health) -> ModuleItem(name, health.version, health.status) })
    }
}

data class ModuleItem(val name: String, val version: String, val status: String)

class ModuleItemDiffCallback :
    androidx.recyclerview.widget.DiffUtil.ItemCallback<ModuleItem>() {
    override fun areItemsTheSame(oldItem: ModuleItem, newItem: ModuleItem): Boolean =
        oldItem.name == newItem.name

    override fun areContentsTheSame(oldItem: ModuleItem, newItem: ModuleItem): Boolean =
        oldItem == newItem
}

// DashboardViewModel vive en su propio archivo (ui/dashboard/DashboardViewModel.kt)
// y ya tiene un metodo refresh(). Esta segunda definicion, sin refresh, era una
// redeclaracion en el mismo paquete.