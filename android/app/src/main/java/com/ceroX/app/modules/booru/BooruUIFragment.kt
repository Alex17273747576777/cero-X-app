package com.ceroX.app.modules.booru

import com.ceroX.app.R

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ceroX.app.databinding.FragmentBooruBinding

class BooruUIFragment : Fragment() {

    private lateinit var binding: FragmentBooruBinding
    private lateinit var adapter: BooruAdapter
    private var booruModule: BooruModule? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBooruBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as com.ceroX.app.core.CeroXApplication
        booruModule = com.ceroX.app.core.Pantallas.modulo(app, "booru-viewer", BooruModule::class.java) {
            if (isAdded && this::adapter.isInitialized) adapter.submitList(booruModule?.listFavorites() ?: emptyList())
        }

        adapter = BooruAdapter(booruModule?.listFavorites() ?: emptyList())
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.searchButton.setOnClickListener {
            // La búsqueda no tiene dónde enseñarse y además no busca: searchBooru
            // devuelve una lista vacía (no hay cliente de ningún booru en el
            // proyecto) y devolvería BooruPost, mientras esta lista enseña
            // BooruFavorite. Antes el listener llamaba a la búsqueda y tiraba el
            // resultado con un "Relleno: mostrar resultados" al lado, o sea que
            // el botón parecía hacer algo. Ahora dice lo que hay.
            android.widget.Toast.makeText(
                requireContext(),
                "La búsqueda en boorus no está implementada: esta lista sólo enseña favoritos guardados",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }
}

class BooruAdapter(private var items: List<BooruFavorite>) :
    androidx.recyclerview.widget.ListAdapter<BooruFavorite, BooruAdapter.ViewHolder>(
        BooruFavoriteDiffCallback()
    ) {

    class ViewHolder(itemView: android.view.View) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val site: android.widget.TextView = itemView.findViewById(R.id.booru_item_site)
        val tags: android.widget.TextView = itemView.findViewById(R.id.booru_item_tags)
        val score: android.widget.TextView = itemView.findViewById(R.id.booru_item_score)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_booru_favorite, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.site.text = item.site
        holder.tags.text = item.tags.joinToString(", ", limit = 5)
        holder.score.text = item.score.toString()
    }

    // Aquí había un `submitList` que se llamaba a sí mismo: recursión infinita
    // y StackOverflow en cuanto llegara la primera lista. ListAdapter ya trae
    // submitList; la correcta es la de la clase base.
}

class BooruFavoriteDiffCallback :
    androidx.recyclerview.widget.DiffUtil.ItemCallback<BooruFavorite>() {
    override fun areItemsTheSame(oldItem: BooruFavorite, newItem: BooruFavorite): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: BooruFavorite, newItem: BooruFavorite): Boolean =
        oldItem == newItem
}