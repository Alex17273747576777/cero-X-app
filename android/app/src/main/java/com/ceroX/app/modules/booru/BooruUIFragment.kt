package com.ceroX.app.modules.booru

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.ceroX.app.R
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.Pantallas
import com.ceroX.app.databinding.FragmentBooruBinding
import com.ceroX.app.ui.ajustes.FragmentHostActivity
import kotlinx.coroutines.launch

class BooruUIFragment : Fragment() {
    private var binding: FragmentBooruBinding? = null
    private var booruModule: BooruModule? = null
    private var adapter: BooruAdapter? = null
    private var mostrandoFavoritos = false
    private var buscando = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentBooruBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        adapter = BooruAdapter(::alternarFavorito, ::abrir, ::ver)
        b.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerView.adapter = adapter
        mostrandoFavoritos = savedInstanceState?.getBoolean("favoritos") ?: false
        b.searchInput.setText(savedInstanceState?.getString("consulta").orEmpty())
        b.searchButton.setOnClickListener { buscar(0) }
        b.searchInput.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { buscar(0); true } else false
        }
        b.booruPrevious.setOnClickListener { buscar(((booruModule?.ultimoResultado?.pagina ?: 1) - 1).coerceAtLeast(0)) }
        b.booruNext.setOnClickListener { buscar((booruModule?.ultimoResultado?.pagina ?: 0) + 1) }
        b.booruShowFavorites.setOnClickListener {
            mostrandoFavoritos = !mostrandoFavoritos
            pintar()
        }

        val app = requireActivity().application as CeroXApplication
        booruModule = Pantallas.modulo(app, "booru-viewer", BooruModule::class.java) {
            if (isAdded) pintar()
        }
        pintar()
    }

    /** Abre la publicación en el visor propio, con zoom y descarga explícita. */
    private fun ver(post: BooruPost) {
        startActivity(FragmentHostActivity.intentoVisor(requireContext(), post))
    }

    private fun buscar(pagina: Int) {
        val b = binding ?: return
        val modulo = booruModule ?: run { b.booruStatus.text = "El módulo todavía se está preparando"; return }
        if (buscando) return
        mostrandoFavoritos = false
        buscando = true
        b.booruProgress.visibility = View.VISIBLE
        b.searchButton.isEnabled = false
        b.booruStatus.text = "Consultando Safebooru.org…"
        viewLifecycleOwner.lifecycleScope.launch {
            val resultado = modulo.searchBooru(tags = b.searchInput.text.toString(), pagina = pagina)
            if (binding != null) {
                buscando = false
                binding!!.booruProgress.visibility = View.GONE
                binding!!.searchButton.isEnabled = true
                pintar()
            }
        }
    }

    private fun pintar() {
        val b = binding ?: return
        val modulo = booruModule ?: return
        b.booruShowFavorites.text = if (mostrandoFavoritos) "Resultados" else "Favoritos"
        if (mostrandoFavoritos) {
            val favoritos = modulo.postsFavoritos()
            adapter?.submitData(favoritos, modulo.idsFavoritos())
            b.booruStatus.text = if (favoritos.isEmpty()) "No hay favoritos guardados. Marca una publicación con ☆."
            else "${favoritos.size} favoritos disponibles sin volver a consultar"
            b.booruPrevious.isEnabled = false
            b.booruNext.isEnabled = false
            return
        }
        val resultado = modulo.ultimoResultado
        adapter?.submitData(resultado?.results.orEmpty(), modulo.idsFavoritos())
        b.booruStatus.text = when {
            resultado == null -> "Escribe una etiqueta o busca las publicaciones safe recientes."
            !resultado.success -> "Error: ${resultado.motivo ?: "no se pudo consultar"}"
            resultado.results.isEmpty() -> "Búsqueda completada: 0 resultados safe. Prueba otra etiqueta."
            else -> "${resultado.results.size} resultados · página ${resultado.pagina + 1} · ${resultado.tags.ifBlank { "recientes" }}"
        }
        b.booruPrevious.isEnabled = !buscando && (resultado?.pagina ?: 0) > 0
        b.booruNext.isEnabled = !buscando && resultado?.success == true && resultado.results.size == resultado.limit
    }

    private fun alternarFavorito(post: BooruPost) {
        val modulo = booruModule ?: return
        modulo.alternarFavorito(post)
        pintar()
    }

    private fun abrir(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) { binding?.booruStatus?.text = "No hay una aplicación para abrir el enlace" }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("consulta", binding?.searchInput?.text?.toString().orEmpty())
        outState.putBoolean("favoritos", mostrandoFavoritos)
    }

    override fun onDestroyView() {
        binding?.recyclerView?.adapter = null
        adapter = null
        binding = null
        super.onDestroyView()
    }
}

class BooruAdapter(
    private val favorito: (BooruPost) -> Unit,
    private val abrir: (String) -> Unit,
    private val ver: (BooruPost) -> Unit,
) : ListAdapter<BooruPost, BooruAdapter.ViewHolder>(BooruDiff()) {
    private var idsFavoritos: Set<Int> = emptySet()

    fun submitData(posts: List<BooruPost>, favoritos: Set<Int>) {
        idsFavoritos = favoritos
        submitList(posts)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.booru_item_image)
        val site: TextView = view.findViewById(R.id.booru_item_site)
        val tags: TextView = view.findViewById(R.id.booru_item_tags)
        val score: TextView = view.findViewById(R.id.booru_item_score)
        val favorite: Button = view.findViewById(R.id.booru_item_favorite)
        val open: Button = view.findViewById(R.id.booru_item_open)
        val original: Button = view.findViewById(R.id.booru_item_original)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_booru_favorite, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val post = getItem(position)
        holder.site.text = "Safebooru · #${post.id}"
        holder.tags.text = post.tags.joinToString(" ", limit = 8)
        holder.score.text = "Puntuación ${post.score} · ${post.dimensions} · ${post.rating}"
        holder.image.contentDescription = "Abrir la publicación ${post.id} en el visor: " +
            post.tags.take(4).joinToString(" ")
        holder.image.setOnClickListener { ver(post) }
        holder.image.setImageDrawable(ColorDrawable(Color.DKGRAY))
        holder.image.load(post.previewUrl) {
            error(ColorDrawable(Color.LTGRAY))
            crossfade(true)
        }
        holder.favorite.text = if (post.id in idsFavoritos) "★" else "☆"
        holder.favorite.contentDescription = if (post.id in idsFavoritos) "Quitar de favoritos" else "Guardar en favoritos"
        holder.favorite.setOnClickListener { favorito(post) }
        holder.open.setOnClickListener { abrir(post.pagina()) }
        holder.original.setOnClickListener { abrir(post.url) }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.image.setImageDrawable(null)
        super.onViewRecycled(holder)
    }
}

private class BooruDiff : DiffUtil.ItemCallback<BooruPost>() {
    override fun areItemsTheSame(oldItem: BooruPost, newItem: BooruPost): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: BooruPost, newItem: BooruPost): Boolean = oldItem == newItem
}
