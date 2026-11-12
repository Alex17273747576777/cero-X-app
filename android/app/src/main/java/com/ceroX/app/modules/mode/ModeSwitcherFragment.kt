package com.ceroX.app.modules.mode

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.Pantallas
import com.ceroX.app.databinding.FragmentModeSwitcherBinding
import com.ceroX.app.ui.ajustes.FragmentHostActivity
import com.ceroX.app.ui.catalogo.CatalogoActivity
import com.ceroX.app.ui.extensiones.IntegracionesActivity
import com.ceroX.app.ui.ia.PantallaIaActivity
import com.ceroX.app.ui.main.MainActivity

/** Modos que abren un recorrido real; no cambian sólo el nombre de una preferencia. */
class ModeSwitcherFragment : Fragment() {
    private var binding: FragmentModeSwitcherBinding? = null
    private var modeManager: ModeManagerModule? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        binding = FragmentModeSwitcherBinding.inflate(i, c, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as CeroXApplication
        modeManager = Pantallas.modulo(app, "mode-manager", ModeManagerModule::class.java) {
            if (isAdded) pintar()
        }
        binding!!.modesRecycler.layoutManager = LinearLayoutManager(requireContext())
        pintar()
    }

    private fun pintar() {
        val b = binding ?: return
        val gestor = modeManager ?: return
        val actual = gestor.getCurrentMode()
        b.currentModeText.text = "Modo actual: ${actual?.name ?: "ninguno"}"
        b.modesRecycler.adapter = ModeAdapter(gestor.listModes(), actual?.id, ::abrirModo)
    }

    private fun abrirModo(modeId: String) {
        val gestor = modeManager ?: return
        if (!gestor.setMode(modeId)) {
            Toast.makeText(requireContext(), "Este modo todavía no tiene un recorrido disponible", Toast.LENGTH_LONG).show()
            return
        }
        pintar()
        val context = requireContext()
        val intent = when (modeId) {
            "dashboard" -> Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            "manga_comics", "novel" -> Intent(context, CatalogoActivity::class.java)
            "catalog" -> FragmentHostActivity.intento(context, FragmentHostActivity.APPS)
            "sanbox_extensions" -> Intent(context, IntegracionesActivity::class.java)
            "ai_assistant" -> Intent(context, PantallaIaActivity::class.java)
            else -> null
        }
        if (intent != null) startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        pintar()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}

class ModeAdapter(
    private val modes: List<AppMode>,
    private val currentModeId: String?,
    private val onModeSelected: (String) -> Unit,
) : androidx.recyclerview.widget.RecyclerView.Adapter<ModeAdapter.ViewHolder>() {
    class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val name: android.widget.TextView = itemView.findViewById(com.ceroX.app.R.id.mode_item_name)
        val desc: android.widget.TextView = itemView.findViewById(com.ceroX.app.R.id.mode_item_desc)
        val category: android.widget.TextView = itemView.findViewById(com.ceroX.app.R.id.mode_item_category)
        val selectBtn: android.widget.Button = itemView.findViewById(com.ceroX.app.R.id.mode_item_select)
        val indicator: View = itemView.findViewById(com.ceroX.app.R.id.mode_item_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(com.ceroX.app.R.layout.item_mode, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val mode = modes[position]
        val isCurrent = mode.id == currentModeId
        holder.name.text = mode.name
        holder.desc.text = mode.description
        holder.category.text = mode.category.name
        holder.selectBtn.text = if (isCurrent) "Activo" else "Abrir"
        holder.selectBtn.isEnabled = !isCurrent
        holder.indicator.setBackgroundColor(
            if (isCurrent) android.graphics.Color.parseColor("#22D3EE")
            else android.graphics.Color.parseColor("#666666"),
        )
        holder.selectBtn.setOnClickListener { onModeSelected(mode.id) }
    }

    override fun getItemCount(): Int = modes.size
}
