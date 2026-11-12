package com.ceroX.app.modules.theme

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ceroX.app.core.AplicaDiseno
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.Pantallas
import com.ceroX.app.databinding.FragmentThemeSwitcherBinding

/** Selector de paletas. No muestra controles de efectos que el renderer no tiene. */
class ThemeSwitcherFragment : Fragment() {
    private var binding: FragmentThemeSwitcherBinding? = null
    private var adapter: ThemeAdapter? = null
    private var themeEngine: ThemeEngineModule? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        binding = FragmentThemeSwitcherBinding.inflate(i, c, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as CeroXApplication
        themeEngine = Pantallas.modulo(app, "theme-engine", ThemeEngineModule::class.java) {
            if (isAdded) pintar()
        }
        if (themeEngine == null) Log.w("ceroX.diseno", "theme-engine no está registrado")
        binding!!.themesRecycler.layoutManager = LinearLayoutManager(requireContext())
        pintar()
    }

    private fun pintar() {
        val b = binding ?: return
        val motor = themeEngine ?: return
        val actual = motor.getCurrentTheme()?.id
        adapter = ThemeAdapter(motor.listThemes(), actual) { themeId ->
            motor.setTheme(themeId)
            adapter?.updateCurrentTheme(themeId)
            // En MainActivity se aplica al instante; desde Ajustes se aplica al volver.
            (activity as? AplicaDiseno)?.aplicarTema(themeId)
        }
        b.themesRecycler.adapter = adapter
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}

class ThemeAdapter(
    private val themes: List<AppTheme>,
    private var currentThemeId: String?,
    private val onThemeSelected: (String) -> Unit,
) : androidx.recyclerview.widget.RecyclerView.Adapter<ThemeAdapter.ViewHolder>() {
    class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val name: android.widget.TextView = itemView.findViewById(com.ceroX.app.R.id.theme_item_name)
        val source: android.widget.TextView = itemView.findViewById(com.ceroX.app.R.id.theme_item_source)
        val desc: android.widget.TextView = itemView.findViewById(com.ceroX.app.R.id.theme_item_desc)
        val selectBtn: android.widget.Button = itemView.findViewById(com.ceroX.app.R.id.theme_item_select)
        val indicator: View = itemView.findViewById(com.ceroX.app.R.id.theme_item_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(com.ceroX.app.R.layout.item_theme, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val theme = themes[position]
        val isCurrent = theme.id == currentThemeId
        holder.name.text = theme.name
        holder.source.text = "Diseño: ${theme.designSource}"
        holder.desc.text = theme.description
        holder.selectBtn.text = if (isCurrent) "Activo" else "Seleccionar"
        holder.selectBtn.isEnabled = !isCurrent
        holder.indicator.setBackgroundColor(android.graphics.Color.parseColor(theme.primaryColor))
        holder.selectBtn.setOnClickListener { onThemeSelected(theme.id) }
    }

    override fun getItemCount(): Int = themes.size

    fun updateCurrentTheme(newId: String?) {
        currentThemeId = newId
        notifyDataSetChanged()
    }
}
