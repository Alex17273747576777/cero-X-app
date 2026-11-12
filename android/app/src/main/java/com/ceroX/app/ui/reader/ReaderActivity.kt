package com.ceroX.app.ui.reader

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ceroX.app.modules.houri.tracker.TrackerAutoSync
import com.ceroX.app.modules.reader.PageCache
import com.ceroX.app.ui.theme.CeroXTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * La Activity del lector. La que nunca existió.
 *
 * QUÉ HABÍA ANTES Y QUÉ HAY AHORA
 * -------------------------------
 * Había un `ReaderPipeline` que aplicaba colorización y reescalado a un bitmap,
 * un `PageCache` con tope en bytes, un `ReaderPrefetcher` con ventana de páginas
 * y hasta un contrato de fuentes `eu.kanade.tachiyomi.source` completo. Todo
 * eso, y **ninguna pantalla**: la app no podía abrir un capítulo. Ese era el
 * hueco número uno de la lista, y el que bloqueaba once issues de terceros.
 *
 * Esta Activity es el pegamento visible: pide páginas a `ContentRepository`,
 * las baja con `PageLoader`, las cachea, y las pinta en un `LazyColumn` que
 * sirve tanto para webtoon (desplazamiento continuo) como para manga página a
 * página.
 *
 * DECISIONES QUE NO SON DE GUSTO
 * ------------------------------
 *  1. **Webtoon primero, página a página después.** El modo continuo es el que
 *     no puede fallar: no hay gesto que interpretar, no hay sentido de lectura
 *     que equivocar. Los otros dos modos se activan por menú y se apoyan en
 *     `ReaderState`, que está probado sin Android.
 *  2. **La caché se recorta en `onTrimMemory`.** Sin eso, el sistema mata la app
 *     en mitad de un capítulo largo y el usuario culpa al manga.
 *  3. **El marcapáginas se guarda al salir y al cambiar de página**, con
 *     `commit()`. Perder dónde ibas es el peor fallo posible en un lector.
 *  4. **Los errores se pintan.** Una página que no baja muestra su motivo y un
 *     botón de reintentar. Una pantalla en negro no distingue «cargando» de
 *     «roto», y el usuario no puede hacer nada.
 *
 * LO QUE ESTA ACTIVITY **NO** HACE
 * --------------------------------
 * No lanza el pipeline de IA por su cuenta. Colorizar cuesta 1 490 ms por
 * página medidos; encenderlo sin que el usuario lo pida sería convertir un
 * lector en una estufa. Queda detrás de un ajuste.
 *
 * Y no se ha ejecutado en un teléfono: en este entorno no hay `/dev/kvm`, así
 * que no hay emulador ni dispositivo. Lo que está probado es lo que se puede
 * probar sin pantalla —estado, orden, rutas, caché, marcapáginas— y está dicho
 * así en el README.
 */
class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FUENTE_ID = "fuente_id"
        const val EXTRA_FUENTE_NOMBRE = "fuente_nombre"
        const val EXTRA_OBRA = "obra"
        const val EXTRA_CAPITULO = "capitulo"
        const val EXTRA_URL_CAPITULO = "url_capitulo"
        const val EXTRA_PAGINA = "pagina"
        const val EXTRA_MODO = "modo"
    }

    private lateinit var cache: PageCache
    private lateinit var ia: IaDePagina
    private lateinit var cargador: PageLoader
    private lateinit var progreso: ReadingProgress

    /**
     * El cable al tracker. Es opcional: sin token guardado no hace nada y no
     * molesta. Antes de esto, `TrackerClient` y el lector no se hablaban y la
     * sincronización era un formulario en otra pantalla.
     */
    private lateinit var autoSync: TrackerAutoSync
    private val alcanceSync = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    /** Último capítulo que se intentó enviar, para no repetirlo en cada página. */
    private var ultimoCapituloEnviado = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cache = PageCache(96L * 1024 * 1024)
        cargador = PageLoader(cache)
        // El pipeline de modelos: si está apagado, procesar() devuelve el mismo
        // bitmap sin copiarlo. Si está encendido, cuesta lo que cuesta y se dice.
        ia = IaDePagina(this)
        progreso = ReadingProgress(this)
        autoSync = TrackerAutoSync(this)

        val fuenteId = intent.getLongExtra(EXTRA_FUENTE_ID, -1L)
        val fuenteNombre = intent.getStringExtra(EXTRA_FUENTE_NOMBRE) ?: "?"
        val obra = intent.getStringExtra(EXTRA_OBRA) ?: ""
        val capitulo = intent.getStringExtra(EXTRA_CAPITULO) ?: ""
        val urlCapitulo = intent.getStringExtra(EXTRA_URL_CAPITULO) ?: ""
        val modo = runCatching {
            ReaderState.Modo.valueOf(intent.getStringExtra(EXTRA_MODO) ?: "LTR")
        }.getOrDefault(ReaderState.Modo.LTR)
        val guardada = progreso.marca(fuenteNombre, obra, capitulo)?.pagina
        val inicial = intent.getIntExtra(EXTRA_PAGINA, guardada ?: 0)

        setContent {
            CeroXTheme(darkTheme = true) {
                Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
                    LectorScreen(
                        titulo = if (capitulo.isBlank()) obra else "$obra — $capitulo",
                        cargarPaginas = {
                            val resultado = com.ceroX.app.sources.RepositorioGlobal.obtener()
                                .paginasDetallado(fuenteId, urlCapitulo)
                            resultado.error?.let { throw IllegalStateException(it) }
                            resultado.paginas
                        },
                        cargador = cargador,
                        ia = ia,
                        clave = { i -> cache.clave(fuenteNombre, obra, capitulo, i) },
                        modo = modo,
                        indiceInicial = inicial,
                        alCambiarPagina = { pagina, total ->
                            progreso.guardar(
                                ReadingProgress.Marca(
                                    fuenteNombre, obra, capitulo, pagina, total,
                                    System.currentTimeMillis()
                                )
                            )
                            // Solo cuando se llega a la última página, y solo
                            // una vez por capítulo: sincronizar en cada página
                            // sería una petición por deslizamiento de dedo.
                            val numero = ReaderState.numeroDeCapitulo(capitulo)
                            if (total > 0 && pagina == total - 1 && numero > 0 &&
                                numero != ultimoCapituloEnviado
                            ) {
                                ultimoCapituloEnviado = numero
                                autoSync.enSegundoPlano(
                                    alcance = alcanceSync,
                                    obra = obra,
                                    capitulo = numero,
                                ) { r ->
                                    if (r.intentado) {
                                        android.util.Log.i(
                                            "ceroX.tracker",
                                            "capítulo $numero de «$obra»: ${r.motivo}",
                                        )
                                    }
                                }
                            }
                        },
                        alSalir = { finish() },
                        alAbrirModelos = {
                            startActivity(
                                android.content.Intent(
                                    this,
                                    com.ceroX.app.ui.ia.PantallaIaActivity::class.java,
                                )
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        alcanceSync.cancel()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // El sistema avisa antes de matar. Soltar la caché es mejor que morir.
        if (level >= TRIM_MEMORY_RUNNING_LOW) cache.recortarHasta(0)
    }
}

/**
 * La pantalla. Separada de la Activity para que el estado de Compose no dependa
 * de la configuración y para que se pueda leer de un vistazo.
 */
@Composable
fun LectorScreen(
    titulo: String,
    cargarPaginas: suspend () -> List<com.ceroX.app.sources.ContentRepository.PaginaLista>,
    cargador: PageLoader,
    clave: (Int) -> String,
    ia: IaDePagina,
    modo: ReaderState.Modo,
    indiceInicial: Int,
    alCambiarPagina: (Int, Int) -> Unit,
    alSalir: () -> Unit,
    alAbrirModelos: () -> Unit,
) {
    var urls by remember {
        mutableStateOf<List<com.ceroX.app.sources.ContentRepository.PaginaLista>>(emptyList())
    }
    var errorGlobal by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var mostrarBarra by remember { mutableStateOf(true) }
    // Bitmap por índice. Un mapa de estado observable: si se usara un mapa
    // normal, la lista no se repintaría al llegar una página.
    val paginas = remember { mutableStateMapOf<Int, PageLoader.Cargada>() }
    val alcance = rememberCoroutineScope()
    val listaState = rememberLazyListState()
    var estadoLector by remember { mutableStateOf(ReaderState(0, 0, modo)) }

    // Carga de la lista de páginas del capítulo.
    LaunchedEffect(Unit) {
        cargando = true
        errorGlobal = null
        try {
            val lista = cargarPaginas()
            urls = lista
            estadoLector = ReaderState(lista.size, indiceInicial.coerceAtLeast(0), modo)
            if (lista.isEmpty()) errorGlobal = "el capítulo no tiene páginas"
            else listaState.scrollToItem(indiceInicial.coerceIn(0, lista.size - 1))
        } catch (e: Throwable) {
            errorGlobal = e.message ?: e.javaClass.simpleName
        } finally {
            cargando = false
        }
    }

    // La página visible se guarda como marcapáginas. `firstVisibleItemIndex` es
    // el índice real de lo que el usuario está viendo en un LazyColumn.
    LaunchedEffect(listaState.firstVisibleItemIndex, urls.size) {
        if (urls.isNotEmpty()) {
            estadoLector.irA(listaState.firstVisibleItemIndex)
            alCambiarPagina(listaState.firstVisibleItemIndex, urls.size)
        }
    }

    // Prefetch: cuando cambia la página visible se piden las siguientes.
    LaunchedEffect(listaState.firstVisibleItemIndex, urls.size) {
        if (urls.isEmpty()) return@LaunchedEffect
        val desde = listaState.firstVisibleItemIndex
        val hasta = minOf(urls.size - 1, desde + 2)
        if (desde + 1 > hasta) return@LaunchedEffect
        for (i in (desde + 1)..hasta) {
            if (paginas[i]?.exito == true || paginas[i] != null) continue
            val pagina = urls[i]
            val r = cargador.cargar(clave(i), pagina.imagenUrl ?: pagina.url) {
                com.ceroX.app.sources.RepositorioGlobal.obtener()
                    .bytesDeImagen(pagina, cargador.limiteBytes)
            }
            paginas[i] = r
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            cargando -> Centrado { CircularProgressIndicator(color = Color.White) }

            errorGlobal != null -> Centrado {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("No se pudo abrir el capítulo", color = Color.White)
                    Text(errorGlobal ?: "", color = Color(0xFFFF8888))
                    BotonSimple("Volver") { alSalir() }
                }
            }

            else -> Unit
        }

        // Columna para que la capa de gestos y la barra no se pisen: si se
        // apilaran en el mismo Box, la capa de gestos se comería los toques de
        // los botones y la barra no respondería nunca.
        Column(Modifier.fillMaxSize()) {
            if (mostrarBarra) {
                BarraSuperior(
                    titulo = titulo,
                    posicion = estadoLector.etiqueta(),
                    alSalir = alSalir,
                    alIrA = { delta ->
                        val destino = (listaState.firstVisibleItemIndex + delta)
                            .coerceIn(0, maxOf(0, urls.size - 1))
                        alcance.launch { listaState.animateScrollToItem(destino) }
                    },
                    alAbrirModelos = alAbrirModelos,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Deslizar para pasar página. Se acumula el recorrido y se
                    // decide al soltar: mirar solo el último delta hace que un
                    // roce de 2 px pase página.
                    .pointerInput(modo, urls.size) {
                        if (modo == ReaderState.Modo.WEBTOON) return@pointerInput
                        var acumulado = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { acumulado = 0f },
                            onHorizontalDrag = { _, delta -> acumulado += delta },
                            onDragEnd = {
                                if (kotlin.math.abs(acumulado) > 80f) {
                                    // Cero-X cuenta como lector occidental: dedo
                                    // a la izquierda = avanzar. En modo RTL se
                                    // invierte, y eso lo decide ReaderState.
                                    val avanza = acumulado < 0f
                                    val estado = ReaderState(
                                        total = urls.size,
                                        inicial = listaState.firstVisibleItemIndex,
                                        modo = modo,
                                    )
                                    estado.porGesto(dxPositivo = avanza)
                                    val destino = listaState.firstVisibleItemIndex +
                                        if (avanza) 1 else -1
                                    alcance.launch {
                                        listaState.animateScrollToItem(
                                            destino.coerceIn(0, maxOf(0, urls.size - 1))
                                        )
                                    }
                                }
                            },
                        )
                    },
            ) {
                if (!cargando && errorGlobal == null) {
                    LazyColumn(state = listaState, modifier = Modifier.fillMaxSize()) {
                        items(urls.size) { indice ->
                            PaginaDeCapitulo(
                                indice = indice,
                                total = urls.size,
                                estado = paginas[indice],
                                cargar = {
                                    alcance.launch {
                                        val pagina = urls[indice]
                                        val cruda = cargador.cargar(
                                            clave(indice), pagina.imagenUrl ?: pagina.url,
                                        ) {
                                            com.ceroX.app.sources.RepositorioGlobal.obtener()
                                                .bytesDeImagen(pagina, cargador.limiteBytes)
                                        }
                                        // Si hay modelos encendidos y la página se
                                        // decodificó, se procesa antes de pintarla.
                                        // Si falla, vuelve la misma: nunca null.
                                        val bmp = cruda.bitmap
                                        paginas[indice] = if (bmp != null && ia.activa) {
                                            cruda.copy(bitmap = ia.procesar(bmp))
                                        } else {
                                            cruda
                                        }
                                    }
                                },
                                alTocar = { mostrarBarra = !mostrarBarra },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Una página. Pide su bitmap la primera vez que se compone y muestra el estado.
 *
 * El `Box` con altura mínima es importante: si la página aún no está, un
 * elemento de altura cero se desmonta y se vuelve a montar al hacer scroll, y
 * eso pide la imagen otra vez en un bucle.
 */
@Composable
private fun PaginaDeCapitulo(
    indice: Int,
    total: Int,
    estado: PageLoader.Cargada?,
    cargar: () -> Unit,
    alTocar: () -> Unit,
) {
    LaunchedEffect(indice, estado) {
        if (estado == null) cargar()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { alTocar() }) },
        contentAlignment = Alignment.Center,
    ) {
        val cargada = estado
        when {
            cargada == null -> Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Color.White)
                Text("${indice + 1} / $total", color = Color(0xFF888888))
            }

            cargada.bitmap != null -> Image(
                bitmap = cargada.bitmap.asImageBitmap(),
                contentDescription = "Página ${indice + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )

            else -> Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Esta página no cargó", color = Color.White)
                Text(cargada.motivo, color = Color(0xFFFF8888))
                BotonSimple("Reintentar", cargar)
            }
        }
    }
}

@Composable
private fun BarraSuperior(
    titulo: String,
    posicion: String,
    alSalir: () -> Unit,
    alIrA: (Int) -> Unit,
    alAbrirModelos: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = alSalir) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
        }
        Column(Modifier.padding(horizontal = 8.dp)) {
            Text(titulo, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text(posicion, color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { alIrA(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Anterior", tint = Color.White)
                }
                IconButton(onClick = { alIrA(1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Siguiente", tint = Color.White)
                }
                // Los modelos: encender, apagar y ver qué pasó de verdad. Está aquí
                // y no en los ajustes globales porque se decide leyendo, al ver una
                // página que se lee mal.
                IconButton(onClick = alAbrirModelos) {
                    Icon(Icons.Filled.Build, contentDescription = "Modelos", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun BotonSimple(texto: String, accion: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = accion) {
        Text(texto, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Centrado(contenido: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { contenido() }
}
