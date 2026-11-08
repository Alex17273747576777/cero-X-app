package com.ceroX.app.modules.theme

import com.ceroX.app.R

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ceroX.app.databinding.FragmentThemeSwitcherBinding

class ThemeSwitcherFragment : Fragment() {

    private lateinit var binding: FragmentThemeSwitcherBinding
    private lateinit var adapter: ThemeAdapter
    private lateinit var themeEngine: ThemeEngineModule

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentThemeSwitcherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as com.ceroX.app.core.CeroXApplication
        themeEngine = app.coreEngine.registry.getInstance("theme-engine") as ThemeEngineModule

        adapter = ThemeAdapter(themeEngine.listThemes(), themeEngine.getCurrentTheme().id) { themeId ->
            themeEngine.setTheme(themeId)
            adapter.notifyDataSetChanged()
        }

        binding.themesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.themesRecycler.adapter = adapter

        binding.liquidGlassSwitch.isChecked = themeEngine.getLiquidGlassEnabled()
        binding.blurSlider.progress = themeEngine.getBlurRadius().toInt()
        binding.blurValue.text = "${themeEngine.getBlurRadius().toInt()}dp"

        binding.liquidGlassSwitch.setOnCheckedChangeListener { _, isChecked ->
            themeEngine.setLiquidGlass(isChecked)
        }

        binding.blurSlider.setOnSliderListener(object : androidx.slider.Slider.OnSliderListener {
            override fun onSliderFocusChange(slider: androidx.slider.Slider, b: Boolean) {}
            override fun onSliderStartTrackingTouch(slider: androidx.slider.Slider) {}
            override fun onSliderStopTrackingTouch(slider: androidx.slider.Slider) {
                themeEngine.setBlurRadius(slider.progress.toFloat())
                binding.blurValue.text = "${slider.progress}dp"
            }
        })
    }
}

class ThemeAdapter(
    private var themes: List<AppTheme>,
    private var currentThemeId: String,
    private val onThemeSelected: (String) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<ThemeAdapter.ViewHolder>() {

    class ViewHolder(itemView: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val name: android.widget.TextView = itemView.findViewById(R.id.theme_item_name)
        val source: android.widget.TextView = itemView.findViewById(R.id.theme_item_source)
        val desc: android.widget.TextView = itemView.findViewById(R.id.theme_item_desc)
        val selectBtn: android.widget.Button = itemView.findViewById(R.id.theme_item_select)
        val indicator: View = itemView.findViewById(R.id.theme_item_indicator)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_theme, parent, false)
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
        holder.indicator.setBackgroundColor(
            android.graphics.Color.parseColor(theme.primaryColor)
        )

        holder.selectBtn.setOnClickListener {
            onThemeSelected(theme.id)
        }
    }

    override fun getItemCount(): Int = themes.size

    fun updateCurrentTheme(newId: String) {
        currentThemeId = newId
        notifyDataSetChanged()
    }
}