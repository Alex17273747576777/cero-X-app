package com.ceroX.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ceroX.app.R
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ReglasArranque
import com.ceroX.app.databinding.FragmentModuleDashboardBinding
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModuleDashboardFragment : Fragment() {
    private var binding: FragmentModuleDashboardBinding? = null
    private lateinit var viewModel: DashboardViewModel
    private var adapter: ModuleAdapter? = null
    private var consulta = ""
    private var trabajando = false
    private var mensajeResultado: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentModuleDashboardBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[DashboardViewModel::class.java]
        val app = requireActivity().application as CeroXApplication
        adapter = ModuleAdapter(::mostrarDetalle)
        binding!!.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding!!.recyclerView.adapter = adapter
        viewModel.moduleHealth.observe(viewLifecycleOwner) { salud ->
            val entradas = salud.entries.toList()
            adapter?.mostrarSalud(entradas, consulta)
            actualizarEstado(salud.size)
        }
        binding!!.refreshButton.setOnClickListener { viewModel.refresh(app) }
        binding!!.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                consulta = query.orEmpty()
                filtrar()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                consulta = newText.orEmpty()
                filtrar()
                return true
            }
        })
        viewModel.refresh(app)
    }

    private fun filtrar() {
        val salud = viewModel.moduleHealth.value ?: return
        adapter?.mostrarSalud(salud.entries.toList(), consulta)
        actualizarEstado(salud.size)
    }

    private fun actualizarEstado(total: Int) {
        val visibles = adapter?.itemCount ?: 0
        binding?.dashboardStatus?.text = "$total módulos · $visibles visibles" +
            (if (visibles == 0) " · sin coincidencias" else "") +
            (mensajeResultado?.let { "\n$it" } ?: "")
    }

    private fun mostrarDetalle(nombre: String) {
        val app = requireActivity().application as CeroXApplication
        val salud = viewModel.moduleHealth.value?.get(nombre) ?: return
        val meta = app.lifecycleManager.getRegistry().getMetadata(nombre) ?: return
        val activo = app.lifecycleManager.isModuleStarted(nombre)
        val advertencia = if (ReglasArranque.esPesado(nombre) && !activo)
            "\n\nEs un módulo pesado: iniciarlo puede reservar memoria para runtime o modelos." else ""
        val detalles = salud.details.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        AlertDialog.Builder(requireContext())
            .setTitle(nombre)
            .setMessage("Versión ${meta.version}\nEstado: ${salud.status}\nCiclo: ${if (activo) "activo" else "detenido"}\nDependencias: ${meta.dependencies.ifEmpty { listOf("ninguna") }.joinToString()}" +
                "\n\n$detalles$advertencia")
            .setPositiveButton(if (activo) "Detener" else "Iniciar") { _, _ -> cambiarEstado(nombre, activo) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun cambiarEstado(nombre: String, detener: Boolean) {
        if (trabajando) return
        val app = requireActivity().application as CeroXApplication
        trabajando = true
        binding?.refreshButton?.isEnabled = false
        binding?.dashboardStatus?.text = if (detener) "Deteniendo $nombre…" else "Inicializando y activando $nombre…"
        viewLifecycleOwner.lifecycleScope.launch {
            val resultado = try {
                withContext(Dispatchers.IO) {
                    val gestor = app.lifecycleManager
                    if (detener) gestor.detenerEnSegundoPlano(nombre).get()
                    else gestor.iniciarYActivarEnSegundoPlano(nombre).get()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is InterruptedException) Thread.currentThread().interrupt()
                com.ceroX.app.core.engine.ResultadoAccionModulo(
                    false,
                    "La acción no terminó: ${e.cause?.message ?: e.message ?: e.javaClass.simpleName}",
                )
            }
            mensajeResultado = resultado.mensaje
            trabajando = false
            binding?.refreshButton?.isEnabled = true
            viewModel.refresh(app)
        }
    }

    override fun onDestroyView() {
        // La corrutina pertenece a esta vista; una nueva vista debe poder actuar.
        trabajando = false
        binding?.recyclerView?.adapter = null
        adapter = null
        binding = null
        super.onDestroyView()
    }
}

fun filtrarModulos(items: List<ModuleItem>, consulta: String): List<ModuleItem> {
    val q = consulta.trim().lowercase()
    if (q.isBlank()) return items
    return items.filter { listOf(it.name, it.version, it.status, it.detalles).joinToString(" ").lowercase().contains(q) }
}

class ModuleAdapter(private val onModuleClick: (String) -> Unit) :
    ListAdapter<ModuleItem, ModuleAdapter.ModuleViewHolder>(ModuleItemDiffCallback()) {
    private var todos: List<ModuleItem> = emptyList()

    class ModuleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: android.widget.TextView = itemView.findViewById(R.id.module_name)
        val status: android.widget.TextView = itemView.findViewById(R.id.module_status)
        val version: android.widget.TextView = itemView.findViewById(R.id.module_version)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModuleViewHolder =
        ModuleViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_module, parent, false))

    override fun onBindViewHolder(holder: ModuleViewHolder, position: Int) {
        val item = getItem(position)
        holder.name.text = item.name
        holder.version.text = "v${item.version} · tocar para detalles y acciones"
        holder.status.text = when (item.status) {
            "healthy" -> "en marcha"
            "stopped" -> "detenido"
            else -> item.status
        }
        val attr = when {
            item.status.contains("error", true) -> com.ceroX.app.R.attr.colorError
            item.status == "healthy" -> com.ceroX.app.R.attr.colorPrimary
            else -> com.ceroX.app.R.attr.colorOnSurfaceVariant
        }
        holder.status.setTextColor(MaterialColors.getColor(holder.itemView, attr))
        holder.itemView.contentDescription = "${item.name}, ${holder.status.text}. Abrir detalles"
        holder.itemView.setOnClickListener { onModuleClick(item.name) }
    }

    fun mostrarSalud(items: List<Map.Entry<String, ModuleHealth>>, consulta: String) {
        todos = items.map { (name, health) ->
            ModuleItem(name, health.version, health.status,
                health.details.entries.joinToString(" ") { "${it.key} ${it.value}" })
        }.sortedBy { it.name }
        submitList(filtrarModulos(todos, consulta))
    }
}

data class ModuleItem(val name: String, val version: String, val status: String, val detalles: String = "")

class ModuleItemDiffCallback : DiffUtil.ItemCallback<ModuleItem>() {
    override fun areItemsTheSame(oldItem: ModuleItem, newItem: ModuleItem): Boolean = oldItem.name == newItem.name
    override fun areContentsTheSame(oldItem: ModuleItem, newItem: ModuleItem): Boolean = oldItem == newItem
}
