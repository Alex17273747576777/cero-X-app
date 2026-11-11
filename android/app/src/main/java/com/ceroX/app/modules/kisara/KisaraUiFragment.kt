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
    private var kisaraModule: KisaraModule? = null
    private var libraryModule: KisaraLibraryModule? = null

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
        kisaraModule = com.ceroX.app.core.Pantallas.modulo(app, "kisara-reader", KisaraModule::class.java) {
            if (isAdded) pintar()
        }

        // toggleBulkSelection vive en KisaraLibraryModule, no en KisaraModule: son
        // dos clases distintas y esta pantalla necesita las dos.
        libraryModule = com.ceroX.app.core.Pantallas.modulo(app, "kisara-library", KisaraLibraryModule::class.java)

        binding.libraryRecycler.layoutManager = LinearLayoutManager(requireContext())
        pintar()

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchInput.text.toString()
                val results = kisaraModule?.searchLibrary(query) ?: emptyList()
                adapter.submitList(results)
                true
            } else false
        }

        // Antes esto era un `if` con un comentario dentro y nada mas: el boton
        // existia, se podia pulsar y no pasaba absolutamente nada. La operacion
        // por lotes llevaba tiempo implementada en KisaraLibraryModule y no habia
        // forma de invocarla desde la pantalla.
        binding.bulkFavoriteButton.setOnClickListener {
            // El cast era sobre kisaraModule, que es un KisaraModule: dos clases
            // distintas, así que daba null siempre y el botón salía en silencio por
            // el return. El comentario de encima decía que la operación por lotes ya
            // se podía invocar desde la pantalla; no se podía. La instancia correcta
            // es la de kisara-library, que es donde vive la operación.
            val biblioteca = libraryModule ?: return@setOnClickListener
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

    /**
     * Dibuja la biblioteca con lo que el lector tenga cargado.
     *
     * Se llama al abrir la pantalla y otra vez cuando el módulo termina de
     * inicializarse, que es cuando la biblioteca puede haber cambiado. Crea siempre
     * el adaptador, aunque la lista esté vacía, para que los listeners de
     * onViewCreated puedan usarlo sin comprobar si existe.
     */
    private fun pintar() {
        if (!this::binding.isInitialized) return
        adapter = KisaraLibraryAdapter(kisaraModule?.listLibraryEntries() ?: emptyList()) { entryId ->
            libraryModule?.toggleBulkSelection(entryId)
            adapter.notifyDataSetChanged()
        }
        binding.libraryRecycler.adapter = adapter
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