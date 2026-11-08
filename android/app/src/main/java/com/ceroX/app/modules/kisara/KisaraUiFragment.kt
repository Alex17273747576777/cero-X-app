package com.ceroX.app.modules.kisara

import com.ceroX.app.R

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ceroX.app.databinding.FragmentKisaraBinding

class KisaraUiFragment : Fragment() {

    private lateinit var binding: FragmentKisaraBinding
    private lateinit var adapter: KisaraLibraryAdapter
    private lateinit var kisaraModule: KisaraModule

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentKisaraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as com.ceroX.app.core.CeroXApplication
        kisaraModule = app.coreEngine.registry.getInstance("kisara-reader") as KisaraModule

        // toggleBulkSelection vive en KisaraLibraryModule, no en KisaraModule.
        // Se resuelve la instancia correcta del registro antes de usarla.
        val libraryModule = app.coreEngine.registry.getInstance("kisara-library") as? KisaraLibraryModule

        adapter = KisaraLibraryAdapter(kisaraModule.listLibraryEntries()) { entryId ->
            libraryModule?.toggleBulkSelection(entryId)
            adapter.notifyDataSetChanged()
        }

        binding.libraryRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.libraryRecycler.adapter = adapter

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchInput.text.toString()
                val results = kisaraModule.searchLibrary(query)
                adapter.submitList(results)
                true
            } else false
        }

        // Antes esto era un `if` con un comentario dentro y nada mas: el boton
        // existia, se podia pulsar y no pasaba absolutamente nada. La operacion
        // por lotes llevaba tiempo implementada en KisaraLibraryModule y no habia
        // forma de invocarla desde la pantalla.
        binding.bulkFavoriteButton.setOnClickListener {
            val biblioteca = kisaraModule as? KisaraLibraryModule ?: return@setOnClickListener
            if (biblioteca.selectedCount() == 0) {
                // Sin seleccion: el boton entra en modo seleccion en vez de no
                // hacer nada, que es lo que confundia.
                biblioteca.selectAllEntries()
                adapter.notifyDataSetChanged()
            } else {
                biblioteca.bulkFavorite(biblioteca.selectedIds(), true)
                adapter.notifyDataSetChanged()
            }
        }
    }
}

class KisaraLibraryAdapter(
    private var items: List<KisaraLibraryEntry>,
    private val onEntryClick: (String) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<KisaraLibraryAdapter.ViewHolder>() {

    class ViewHolder(itemView: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val title: android.widget.TextView = itemView.findViewById(R.id.kisara_item_title)
        val author: android.widget.TextView = itemView.findViewById(R.id.kisara_item_author)
        val cover: android.widget.ImageView = itemView.findViewById(R.id.kisara_item_cover)
        val favorite: android.widget.CheckBox = itemView.findViewById(R.id.kisara_item_favorite)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_kisara_library, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.author.text = item.author
        holder.favorite.isChecked = item.isFavorite
        holder.favorite.setOnCheckedChangeListener { _, isChecked ->
            // Toggle favorite
        }
        holder.itemView.setOnClickListener { onEntryClick(item.id) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newList: List<KisaraLibraryEntry>) {
        items = newList
        notifyDataSetChanged()
    }
}