package com.ceroX.app.modules.kisara

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ceroX.app.R
import com.ceroX.app.core.Pantallas
import com.ceroX.app.databinding.FragmentKisaraBinding

/** Biblioteca local: todas las operaciones cambian la misma lista del lector. */
class KisaraUiFragment : Fragment() {
    private lateinit var binding: FragmentKisaraBinding
    private lateinit var adapter: KisaraLibraryAdapter
    private var lector: KisaraModule? = null
    private var biblioteca: KisaraLibraryModule? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentKisaraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as com.ceroX.app.core.CeroXApplication
        adapter = KisaraLibraryAdapter(emptyList(), emptySet(), ::seleccionar, ::favorito)
        binding.libraryRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.libraryRecycler.adapter = adapter
        binding.searchInput.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_SEARCH) { pintar(); true } else false
        }
        binding.bulkFavoriteButton.setOnClickListener { favoritosSeleccionados() }
        binding.mergeEntriesButton.setOnClickListener { fusionarSeleccionadas() }
        lector = Pantallas.modulo(app, "kisara-reader", KisaraModule::class.java) { copiarDesdeLector() }
        biblioteca = Pantallas.modulo(app, "kisara-library", KisaraLibraryModule::class.java) { copiarDesdeLector() }
        copiarDesdeLector()
    }

    private fun copiarDesdeLector() {
        val origen = lector ?: return
        val destino = biblioteca ?: return
        if (!this::binding.isInitialized) return
        destino.replaceEntries(origen.listLibraryEntries())
        pintar()
    }

    private fun guardarEnLector() {
        val destino = lector ?: return
        val origen = biblioteca ?: return
        destino.replaceLibraryEntries(origen.listEntries())
    }

    private fun seleccionar(id: String) {
        biblioteca?.toggleBulkSelection(id)
        pintar()
    }

    private fun favorito(id: String, valor: Boolean) {
        biblioteca?.setFavorite(id, valor)
        guardarEnLector()
        pintar()
    }

    private fun favoritosSeleccionados() {
        val modulo = biblioteca ?: return
        if (modulo.selectedCount() == 0) {
            modulo.selectAllEntries()
            binding.libraryStatus.text = "${modulo.selectedCount()} seleccionadas; pulsa de nuevo para marcar favoritas"
        } else {
            modulo.bulkFavorite(modulo.selectedIds(), true)
            guardarEnLector()
            binding.libraryStatus.text = "Favoritas actualizadas en la biblioteca local"
        }
        pintar()
    }

    private fun fusionarSeleccionadas() {
        val modulo = biblioteca ?: return
        val ids = modulo.selectedIds()
        if (ids.size < 2) {
            binding.libraryStatus.text = "Selecciona al menos dos copias para fusionar"
            return
        }
        try {
            modulo.mergeEntries(ids.first(), ids.drop(1))
            guardarEnLector()
            binding.libraryStatus.text = "Copias fusionadas: progreso máximo, fuentes y etiquetas conservadas"
        } catch (e: IllegalArgumentException) {
            binding.libraryStatus.text = e.message
        }
        pintar()
    }

    private fun pintar() {
        if (!this::binding.isInitialized || !this::adapter.isInitialized) return
        val modulo = biblioteca ?: return
        val resultados = modulo.searchEntries(binding.searchInput.text?.toString().orEmpty())
        adapter.submitList(resultados, modulo.selectedIds().toSet())
        binding.libraryStatus.text = binding.libraryStatus.text?.toString()?.takeIf {
            it.contains("seleccionadas") || it.contains("fusionadas") || it.contains("actualizadas") || it.contains("Selecciona")
        } ?: "${resultados.size} de ${modulo.listEntries().size} obras · búsqueda: \"frase\" -excluir tag:acción"
    }
}

class KisaraLibraryAdapter(
    private var items: List<KisaraLibraryEntry>,
    private var seleccionadas: Set<String>,
    private val onEntryClick: (String) -> Unit,
    private val onFavorite: (String, Boolean) -> Unit,
) : RecyclerView.Adapter<KisaraLibraryAdapter.ViewHolder>() {
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.kisara_item_title)
        val author: TextView = itemView.findViewById(R.id.kisara_item_author)
        val cover: ImageView = itemView.findViewById(R.id.kisara_item_cover)
        val favorite: CheckBox = itemView.findViewById(R.id.kisara_item_favorite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_kisara_library, parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.author.text = "${item.author} · ${item.chaptersRead}/${item.totalChapters} capítulos"
        holder.favorite.setOnCheckedChangeListener(null)
        holder.favorite.isChecked = item.isFavorite
        holder.favorite.setOnCheckedChangeListener { _, valor -> onFavorite(item.id, valor) }
        holder.itemView.isActivated = item.id in seleccionadas
        holder.itemView.setOnClickListener { onEntryClick(item.id) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newList: List<KisaraLibraryEntry>, nuevasSeleccionadas: Set<String>) {
        items = newList
        seleccionadas = nuevasSeleccionadas
        notifyDataSetChanged()
    }
}
