package com.ceroX.app.modules.theme

import com.ceroX.app.R

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

/**
 * La pantalla que elige la paleta.
 *
 * Pedía el motor de temas al motor legacy, que no registraba nada: el cast no nulo
 * sobre null cerraba la app al tocar «Diseño» en la barra inferior. Ahora lo pide
 * por [Pantallas], que devuelve null en vez de reventar, y avisa a la actividad para
 * que la paleta se aplique en el momento —antes se guardaba y no cambiaba nada—.
 */
class ThemeSwitcherFragment : Fragment() {

    companion object {
        private const val TAG = "ceroX.diseno"

        /** Cada paso del SeekBar equivale a 4 dp de radio de desenfoque. */
        private const val PASO_DP = 4
    }

    private lateinit var binding: FragmentThemeSwitcherBinding
    private var adapter: ThemeAdapter? = null
    private var themeEngine: ThemeEngineModule? = null

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

        val app = requireActivity().application as CeroXApplication
        themeEngine = Pantallas.modulo(app, "theme-engine", ThemeEngineModule::class.java) {
            if (isAdded) pintar()
        }
        if (themeEngine == null) {
            Log.w(TAG, "theme-engine no está registrado: la pantalla queda vacía")
        }

        binding.themesRecycler.layoutManager = LinearLayoutManager(requireContext())
        pintar()
    }

    /**
     * Dibuja la lista, el interruptor y la barra.
     *
     * Se llama al abrir y otra vez cuando el módulo termina de inicializarse, que es
     * cuando puede haber cambiado algo. Todo va sobre el binding existente: si el
     * fragment ya no está añadido, no se toca nada.
     */
    private fun pintar() {
        if (!this::binding.isInitialized) return
        val motor = themeEngine ?: return

        val actual = motor.getCurrentTheme()?.id
        val nuevo = ThemeAdapter(motor.listThemes(), actual) { themeId ->
            motor.setTheme(themeId)
            adapter?.notifyDataSetChanged()
            // La paleta se aplica al vuelo: la actividad repinta Compose con los
            // colores elegidos. Sin esto, «Seleccionar» guardaba y no pasaba nada.
            (activity as? AplicaDiseno)?.aplicarTema(themeId)
        }
        adapter = nuevo
        binding.themesRecycler.adapter = nuevo

        binding.liquidGlassSwitch.isChecked = motor.getLiquidGlassEnabled()
        // SeekBar va de 0 a 16 en pasos de 1, y cada paso son 4 dp de desenfoque.
        // Los dos valores se guardan y ningún renderer los usa: no hay desenfoque
        // en la app. La pantalla lo dice en su subtítulo.
        val radioActual = motor.getBlurRadius().toInt()
        binding.blurSeekbar.progress = (radioActual / PASO_DP).coerceIn(0, 16)
        binding.blurValue.text = "${radioActual}dp"

        binding.liquidGlassSwitch.setOnCheckedChangeListener { _, isChecked ->
            motor.setLiquidGlass(isChecked)
        }

        binding.blurSeekbar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(barra: android.widget.SeekBar, progreso: Int, delUsuario: Boolean) {
                if (!delUsuario) return
                val dp = progreso * PASO_DP
                motor.setBlurRadius(dp.toFloat())
                binding.blurValue.text = "${dp}dp"
            }

            override fun onStartTrackingTouch(barra: android.widget.SeekBar) {}

            override fun onStopTrackingTouch(barra: android.widget.SeekBar) {}
        })
    }
}

class ThemeAdapter(
    private var themes: List<AppTheme>,
    private var currentThemeId: String?,
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

    fun updateCurrentTheme(newId: String?) {
        currentThemeId = newId
        notifyDataSetChanged()
    }
}
