package com.ceroX.app.modules.mode

import com.ceroX.app.R

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ceroX.app.databinding.FragmentModeSwitcherBinding

class ModeSwitcherFragment : Fragment() {

    private lateinit var binding: FragmentModeSwitcherBinding
    private lateinit var adapter: ModeAdapter
    private var modeManager: ModeManagerModule? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentModeSwitcherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as com.ceroX.app.core.CeroXApplication
        modeManager = com.ceroX.app.core.Pantallas.modulo(app, "mode-manager", ModeManagerModule::class.java) {
            if (isAdded) pintar()
        }

        binding.modesRecycler.layoutManager = LinearLayoutManager(requireContext())
        pintar()
    }

    /** Dibuja la lista y el modo actual. Se repite cuando el módulo termina de inicializarse. */
    private fun pintar() {
        if (!this::binding.isInitialized) return
        val gestor = modeManager ?: return
        val actual = gestor.getCurrentMode()
        adapter = ModeAdapter(gestor.listModes(), actual?.id) { modeId ->
            gestor.setMode(modeId)
            adapter.notifyDataSetChanged()
        }
        binding.modesRecycler.adapter = adapter
        binding.currentModeText.text = "Modo actual: ${actual?.name ?: "ninguno"}"
    }

    override fun onResume() {
        super.onResume()
        pintar()
    }
}

class ModeAdapter(
    private var modes: List<AppMode>,
    private var currentModeId: String?,
    private val onModeSelected: (String) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<ModeAdapter.ViewHolder>() {

    class ViewHolder(itemView: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val name: android.widget.TextView = itemView.findViewById(R.id.mode_item_name)
        val desc: android.widget.TextView = itemView.findViewById(R.id.mode_item_desc)
        val category: android.widget.TextView = itemView.findViewById(R.id.mode_item_category)
        val selectBtn: android.widget.Button = itemView.findViewById(R.id.mode_item_select)
        val indicator: View = itemView.findViewById(R.id.mode_item_indicator)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mode, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val mode = modes[position]
        val isCurrent = mode.id == currentModeId

        holder.name.text = mode.name
        holder.desc.text = mode.description
        holder.category.text = mode.category.name
        holder.selectBtn.text = if (isCurrent) "Activo" else "Cambiar"
        holder.selectBtn.isEnabled = !isCurrent
        holder.indicator.setBackgroundColor(
            if (isCurrent) android.graphics.Color.parseColor("#22D3EE")
            else android.graphics.Color.parseColor("#666666")
        )

        holder.selectBtn.setOnClickListener {
            onModeSelected(mode.id)
        }
    }

    override fun getItemCount(): Int = modes.size

    fun updateCurrentMode(newId: String?) {
        currentModeId = newId
        notifyDataSetChanged()
    }
}