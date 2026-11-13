package com.ceroX.app.modules.booru

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.ceroX.app.core.CeroXApplication
import com.ceroX.app.core.Pantallas
import com.ceroX.app.databinding.FragmentVisorImagenBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Visor de una publicación de Safebooru: zoom, arrastre y descarga explícita.
 *
 * POR QUÉ EXISTE
 * --------------
 * Antes, tocar una publicación abría el navegador. Eso no es un visor: no hay
 * zoom, no hay estado y la imagen original depende de otra aplicación. Aquí la
 * imagen se carga en la propia app y se puede ampliar con dos dedos, con doble
 * toque o con los botones, que hacen exactamente la misma operación.
 *
 * LO QUE NO PROMETE
 * -----------------
 * No reproduce vídeo: Safebooru sirve imágenes y el filtro de
 * [urlMediaSafebooru] no deja pasar otra cosa. Si la imagen no se puede cargar
 * (sin red, HTTP 403), se dice con el motivo y el zoom queda disponible sobre
 * las dimensiones que declaró la API; no se finge una imagen en blanco como si
 * fuera la real.
 */
class VisorBooruFragment : Fragment() {

    private var binding: FragmentVisorImagenBinding? = null
    private var modulo: BooruModule? = null
    private var post: BooruPost? = null
    private var guardando = false

    /** Escala que se recupera tras rotar; vive hasta que haya imagen que ajustar. */
    private var escalaPendiente = 0f

    /**
     * Costura para probar la pantalla sin red.
     *
     * En producción vale [cargadorCoil]: carga con Coil y avisa a
     * [imagenCargada] o [falloDeCarga]. Una prueba puede sustituirlo y decidir el
     * resultado; lo que se comprueba entonces es exactamente lo que hace la
     * pantalla con ese resultado. Coil no se puede ejercitar en Robolectric sin
     * lanzar una petición real, y una prueba no debe tocar la red.
     */
    internal var cargadorImagen: CargadorImagen = cargadorCoil

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentVisorImagenBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        val datos = postDe(arguments)
        if (datos == null) {
            b.visorEstado.text = "El visor se abrió sin los datos de la publicación"
            b.visorMetadata.text = "Sin publicación"
            desactivarTodo()
            return
        }
        post = datos

        val app = requireActivity().application as CeroXApplication
        modulo = Pantallas.modulo(app, "booru-viewer", BooruModule::class.java) {
            if (isAdded) pintarFavorito()
        }

        b.visorMetadata.text = "Safebooru #${datos.id} · ${datos.dimensions} · rating ${datos.rating}" +
            " · puntuación ${datos.score}"
        b.visorTags.text = if (datos.tags.isEmpty()) "Sin etiquetas declaradas"
        else datos.tags.joinToString(" ")
        b.visorImagen.alCambiar = { estado -> pintarZoom(estado) }

        val declaradas = VisorTransformacion.dimensiones(datos.dimensions)
        if (declaradas != null) {
            val (anchoVista, altoVista) = huecoDisponible()
            b.visorImagen.establecer(declaradas.first, declaradas.second, anchoVista, altoVista)
            b.visorEstado.text = "Imagen declarada de ${declaradas.first}×${declaradas.second};" +
                " cargando el archivo original…"
        } else {
            b.visorEstado.text = "La API no declaró dimensiones (${datos.dimensions});" +
                " el zoom se activará cuando llegue la imagen"
        }

        b.visorAjustar.setOnClickListener { b.visorImagen.ajustar() }
        b.visorUnoAUno.setOnClickListener { b.visorImagen.unoAUno() }
        b.visorAcercar.setOnClickListener { b.visorImagen.ampliar() }
        b.visorAlejar.setOnClickListener { b.visorImagen.reducir() }
        b.visorFicha.setOnClickListener { abrirExterna(datos.pagina()) }
        b.visorFavorito.setOnClickListener { alternarFavorito(datos) }
        b.visorGuardar.setOnClickListener { guardar(datos) }

        pintarFavorito()

        // La escala se recupera en cuanto haya imagen: al rotar, Coil vuelve a
        // cargar y `establecer` reajusta, así que el valor tiene que sobrevivir.
        escalaPendiente = savedInstanceState?.getFloat("escala", 0f) ?: 0f
        cargar(datos.url)
        aplicarEscalaPendiente()
    }

    private fun aplicarEscalaPendiente() {
        if (escalaPendiente <= 0f) return
        val visor = binding?.visorImagen ?: return
        if (!visor.estado.util) return
        visor.restaurarEscala(escalaPendiente)
        escalaPendiente = 0f
    }

    /** Tamaño real de la vista si ya se midió; si no, el de la pantalla. */
    private fun huecoDisponible(): Pair<Int, Int> {
        val v = binding?.visorImagen
        if (v != null && v.width > 0 && v.height > 0) return v.width to v.height
        val dm = resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    private fun cargar(url: String) {
        val b = binding ?: return
        b.visorProgress.visibility = View.VISIBLE
        cargadorImagen(b.visorImagen, url, ::imagenCargada, ::falloDeCarga)
    }

    /**
     * Qué hace la pantalla cuando la imagen llega: reajusta con las dimensiones
     * reales del drawable, que pueden no ser las que declaró la API.
     */
    internal fun imagenCargada(ancho: Int, alto: Int) {
        val b = binding ?: return
        val (anchoVista, altoVista) = huecoDisponible()
        b.visorImagen.establecer(ancho, alto, anchoVista, altoVista)
        aplicarEscalaPendiente()
        b.visorProgress.visibility = View.GONE
        b.visorEstado.text = "Imagen cargada de $ancho×$alto." +
            " Dos dedos o doble toque para ampliar; arrastra para recorrerla."
    }

    /**
     * Qué hace la pantalla cuando la imagen no llega. Dice el motivo y no finge
     * una imagen en blanco: si la API declaró dimensiones, el zoom sigue
     * funcionando sobre ellas; si no, no hay nada que ampliar y se dice.
     */
    internal fun falloDeCarga(motivo: String) {
        val b = binding ?: return
        b.visorProgress.visibility = View.GONE
        b.visorEstado.text = "No se pudo cargar la imagen: $motivo." +
            if (b.visorImagen.estado.lista) {
                " El zoom sigue activo sobre las dimensiones declaradas."
            } else {
                " Sin imagen no hay nada que ampliar."
            }
    }

    private fun pintarZoom(estado: VisorTransformacion) {
        val b = binding ?: return
        b.visorZoom.text = estado.resumen()
        b.visorAcercar.isEnabled = estado.puedeAmpliar
        b.visorAlejar.isEnabled = estado.puedeReducir
        b.visorAjustar.isEnabled = estado.lista && !estado.ajustada
        b.visorUnoAUno.isEnabled = estado.lista
    }

    private fun pintarFavorito() {
        val b = binding ?: return
        val datos = post ?: return
        val favorito = datos.id in (modulo?.idsFavoritos() ?: emptySet())
        b.visorFavorito.text = if (favorito) "★" else "☆"
        b.visorFavorito.contentDescription =
            if (favorito) "Quitar la publicación ${datos.id} de favoritos"
            else "Guardar la publicación ${datos.id} en favoritos"
        b.visorFavorito.isEnabled = modulo != null
    }

    private fun alternarFavorito(datos: BooruPost) {
        val b = binding ?: return
        val m = modulo
        if (m == null) {
            b.visorEstado.text = "El módulo Booru todavía se está preparando: no se puede guardar el favorito"
            return
        }
        val añadido = m.alternarFavorito(datos)
        pintarFavorito()
        b.visorEstado.text = if (añadido) {
            "Publicación ${datos.id} guardada en favoritos (metadatos en el almacenamiento externo)"
        } else {
            "Publicación ${datos.id} quitada de favoritos"
        }
    }

    private fun guardar(datos: BooruPost) {
        val b = binding ?: return
        if (guardando) return
        val m = modulo
        if (m == null) {
            b.visorEstado.text = "El módulo Booru todavía se está preparando: no se puede descargar"
            return
        }
        guardando = true
        b.visorGuardar.isEnabled = false
        b.visorProgress.visibility = View.VISIBLE
        b.visorEstado.text = "Descargando la imagen original al almacenamiento externo…"
        viewLifecycleOwner.lifecycleScope.launch {
            val resultado = withContext(Dispatchers.IO) { m.descargar(datos, datos.url) }
            guardando = false
            val actual = binding ?: return@launch
            actual.visorProgress.visibility = View.GONE
            actual.visorGuardar.isEnabled = true
            actual.visorEstado.text = when (resultado) {
                is ResultadoDescarga.Exito ->
                    if (resultado.yaExistia) {
                        "Ya estaba guardada: ${resultado.ruta} (${resultado.bytes} bytes)"
                    } else {
                        "Guardada: ${resultado.ruta} (${resultado.bytes} bytes)"
                    }
                is ResultadoDescarga.Fallo -> "No se guardó: ${resultado.motivo}"
            }
        }
    }

    private fun abrirExterna(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            binding?.visorEstado?.text = "No hay ninguna aplicación para abrir $url"
        }
    }

    private fun desactivarTodo() {
        val b = binding ?: return
        listOf(
            b.visorAjustar, b.visorUnoAUno, b.visorAcercar, b.visorAlejar,
            b.visorGuardar, b.visorFavorito, b.visorFicha,
        ).forEach { it.isEnabled = false }
        b.visorZoom.text = "Sin imagen"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val estado = binding?.visorImagen?.estado ?: return
        if (!estado.lista) return
        outState.putFloat("escala", estado.escala)
        outState.putFloat("focoX", estado.anchoVista / 2f)
        outState.putFloat("focoY", estado.altoVista / 2f)
        outState.putInt("anchoVista", estado.anchoVista)
        outState.putInt("altoVista", estado.altoVista)
    }

    override fun onDestroyView() {
        binding?.visorImagen?.alCambiar = null
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_ID = "id"
        private const val ARG_URL = "url"
        private const val ARG_PREVIEW = "preview"
        private const val ARG_TAGS = "tags"
        private const val ARG_SCORE = "score"
        private const val ARG_RATING = "rating"
        private const val ARG_DIMS = "dimensions"

        fun argumentos(post: BooruPost): Bundle = Bundle().apply {
            putInt(ARG_ID, post.id)
            putString(ARG_URL, post.url)
            putString(ARG_PREVIEW, post.previewUrl)
            putString(ARG_TAGS, post.tags.joinToString(" "))
            putInt(ARG_SCORE, post.score)
            putString(ARG_RATING, post.rating)
            putString(ARG_DIMS, post.dimensions)
        }

        /** null si faltan los datos esenciales; el visor lo dice en vez de inventarlos. */
        fun postDe(bundle: Bundle?): BooruPost? {
            if (bundle == null) return null
            val id = bundle.getInt(ARG_ID, 0)
            val url = urlMediaSafebooru(bundle.getString(ARG_URL))
            if (id <= 0 || url == null) return null
            return BooruPost(
                id = id,
                url = url,
                previewUrl = urlMediaSafebooru(bundle.getString(ARG_PREVIEW)),
                tags = bundle.getString(ARG_TAGS).orEmpty().split(Regex("\\s+")).filter { it.isNotBlank() },
                score = bundle.getInt(ARG_SCORE, 0),
                rating = bundle.getString(ARG_RATING) ?: "safe",
                dimensions = bundle.getString(ARG_DIMS).orEmpty(),
                source = null,
                createdAt = 0L,
            )
        }
    }
}

/** Cómo se carga una imagen en el visor. */
internal typealias CargadorImagen = (
    vista: VisorImagenView,
    url: String,
    exito: (ancho: Int, alto: Int) -> Unit,
    fallo: (motivo: String) -> Unit,
) -> Unit

/** El cargador real: Coil, con su éxito y su error traducidos a los dos callbacks. */
private val cargadorCoil: CargadorImagen = { vista, url, exito, fallo ->
    vista.load(url) {
        listener(
            onSuccess = { _, resultado ->
                exito(resultado.drawable.intrinsicWidth, resultado.drawable.intrinsicHeight)
            },
            onError = { _, resultado ->
                fallo(resultado.throwable.message ?: resultado.throwable.javaClass.simpleName)
            },
        )
    }
}
