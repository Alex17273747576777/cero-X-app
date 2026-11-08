package com.ceroX.app.sources

import com.ceroX.app.modules.reader.DescargadorDeBytes
import com.ceroX.app.ui.reader.ReaderState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files

/**
 * El lector de punta a punta: de una carpeta en el disco a páginas descargadas,
 * pasando por las mismas piezas que usa la pantalla de lectura.
 *
 * POR QUÉ ESTA PRUEBA ES DISTINTA DE LAS QUE YA HABÍA
 * --------------------------------------------------
 * `LocalFolderSourceTest` prueba la fuente; `ContentRepositoryTest` prueba el
 * orquestador con fuentes falsas. Ninguna de las dos demuestra **la cadena
 * completa**, y el README tenía razón al decir que faltaba una prueba de eso:
 * entre las dos piezas hay cuatro saltos (registro de fuente → carpeta →
 * capítulos → páginas → bytes) y un fallo en cualquiera de ellos deja la
 * pantalla de lectura en negro.
 *
 * QUÉ SE EJECUTA AQUÍ, Y QUÉ NO
 * -----------------------------
 * Se ejecuta de verdad:
 *
 *   · `LocalFolderSource` leyendo una carpeta temporal real con imágenes reales.
 *   · `RepositorioGlobal` + `ContentRepository` (las que consulta
 *     `ReaderActivity` en `onCreate`).
 *   · `DescargadorDeBytes` bajando los bytes por **HTTP** desde un
 *     `HttpServer` que sirve esa misma carpeta, que es lo que ocurre con las
 *     fuentes de extensiones.
 *
 * No se ejecuta `BitmapFactory` (devuelve `null` en JVM) ni el `LazyColumn`. Eso
 * está dicho en el README y no se disimula: la prueba llega hasta los bytes y el
 * orden y el estado de página, que es donde estaban los fallos que importan.
 */
class LectorExtremoAExtremoTest {

    private lateinit var raiz: File
    private var servidor: MiniServidor? = null
    private var base = ""

    /** Un PNG mínimo válido de 1×1, escrito a mano para no depender de assets. */
    private val png1x1 = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(),
        0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
        0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
        0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
        0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
        0x42, 0x60, 0x82.toByte(),
    )

    @Before
    fun preparar() {
        raiz = Files.createTempDirectory("cerox-lector").toFile()
        RepositorioGlobal.limpiar()
    }

    @After
    fun limpiar() {
        servidor?.cerrar()
        raiz.deleteRecursively()
        RepositorioGlobal.limpiar()
    }

    private fun pagina(carpeta: File, nombre: String, bytes: ByteArray = png1x1) {
        carpeta.mkdirs()
        File(carpeta, nombre).writeBytes(bytes)
    }

    // ── La cadena completa, con los mismos objetos que usa la pantalla ──────

    @Test
    fun `de una carpeta en disco a paginas con bytes`() = runBlocking {
        // La biblioteca del usuario: una serie con dos capítulos, y el 10 va
        // antes que el 2 en el gestor de archivos del sistema.
        val obra = File(raiz, "One Piece")
        pagina(File(obra, "Capítulo 10"), "1.jpg")
        pagina(File(obra, "Capítulo 10"), "2.jpg")
        pagina(File(obra, "Capítulo 10"), "3.jpg")
        pagina(File(obra, "Capítulo 2"), "1.jpg")
        pagina(File(obra, "Capítulo 2"), "2.jpg")

        val fuente = LocalFolderSource(raiz = raiz)
        assertTrue("la fuente local tiene que registrarse", RepositorioGlobal.registrar(fuente))

        // Lo que hace CatalogoActivity al tocar una serie.
        val repo = RepositorioGlobal.obtener()
        val detalle = repo.detalle(fuente.id, obra.absolutePath)
        assertNotNull("la ficha de la serie no puede ser nula", detalle)
        assertEquals("One Piece", detalle!!.titulo)
        assertEquals(2, detalle.capitulos.size)
        assertEquals("Capítulo 2", detalle.capitulos[0].nombre)
        assertEquals("Capítulo 10", detalle.capitulos[1].nombre)

        // Lo que hace ReaderActivity al abrir un capítulo.
        val capitulo = detalle.capitulos[1]
        val paginas = repo.paginas(fuente.id, capitulo.url)
        assertEquals(3, paginas.size)
        assertEquals(listOf(0, 1, 2), paginas.map { it.indice })
        assertTrue("la imagen resuelta no puede ser nula", paginas.all { it.imagenUrl != null })

        // Y de cada página, sus bytes. Aquí es donde antes no se probaba nada.
        val descargador = DescargadorDeBytes()
        paginas.forEach { p ->
            val bytes = descargador.leer(p.imagenUrl!!)
            assertEquals("bytes de la página ${p.indice + 1}", png1x1.size, bytes.size)
            assertTrue("tiene que ser un PNG", bytes[0] == png1x1[0] && bytes[1] == png1x1[1])
        }
    }

    @Test
    fun `el capitulo 10 no abre con las paginas del capitulo 2`() = runBlocking {
        val obra = File(raiz, "Berserk")
        pagina(File(obra, "01"), "001.jpg", byteArrayOf(1, 1, 1))
        pagina(File(obra, "10"), "001.jpg", byteArrayOf(9, 9, 9))
        pagina(File(obra, "10"), "002.jpg", byteArrayOf(8, 8, 8))

        val fuente = LocalFolderSource(raiz = raiz)
        RepositorioGlobal.registrar(fuente)
        val repo = RepositorioGlobal.obtener()
        val detalle = repo.detalle(fuente.id, obra.absolutePath)!!

        val cap10 = detalle.capitulos.first { it.nombre == "10" }
        val paginas = repo.paginas(fuente.id, cap10.url)
        assertEquals(2, paginas.size)
        val primero = File(paginas[0].url).readBytes()
        assertEquals("la primera página del capítulo 10 es la 001 suya", 9.toByte(), primero[0])
    }

    // ── La parte de red, contra un servidor HTTP de verdad ─────────────────

    /**
     * Un servidor HTTP mínimo sobre un `ServerSocket` de verdad.
     *
     * POR QUÉ A MANO Y NO CON `com.sun.net.httpserver`
     * -----------------------------------------------
     * Porque `android.jar` no trae `com.sun.net.httpserver` y la prueba no
     * compila. Un socket no es un simulacro: por ahí salen bytes HTTP/1.1 de
     * verdad y el cliente de la app los tiene que interpretar. Además permite
     * el caso incómodo que ninguna librería de pruebas reproduce bien: un
     * servidor que **no** manda `Content-Length` y cierra la conexión.
     */
    private inner class MiniServidor {

        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val puerto: Int get() = socket.localPort

        init {
            Thread {
                while (!socket.isClosed) {
                    try {
                        val cliente = socket.accept()
                        Thread { atender(cliente) }.apply { isDaemon = true }.start()
                    } catch (e: IOException) {
                        break
                    }
                }
            }.apply { isDaemon = true }.start()
        }

        fun cerrar() {
            runCatching { socket.close() }
        }

        private fun atender(cliente: Socket) {
            cliente.use { s ->
                val entrada = s.getInputStream()
                val texto = StringBuilder()
                // Se lee sólo la cabecera: la petición del descargador es un GET
                // sin cuerpo.
                val crudo = java.io.BufferedReader(java.io.InputStreamReader(entrada, Charsets.ISO_8859_1))
                val primera = crudo.readLine() ?: return
                val ruta = primera.split(" ").getOrNull(1) ?: "/"
                while (true) {
                    val h = crudo.readLine() ?: break
                    if (h.isEmpty()) break
                    texto.append(h)
                }
                val salida = s.getOutputStream()

                fun responder(estado: String, cabeceras: List<String>, cuerpo: ByteArray) {
                    val cabecera = buildString {
                        append("HTTP/1.1 ").append(estado).append("\r\n")
                        cabeceras.forEach { append(it).append("\r\n") }
                        append("\r\n")
                    }
                    salida.write(cabecera.toByteArray(Charsets.ISO_8859_1))
                    salida.write(cuerpo)
                    salida.flush()
                }

                when (ruta) {
                    "/redirige" -> responder("302 Found", listOf("Location: /ok.png", "Content-Length: 0"), ByteArray(0))
                    "/bucle" -> responder("302 Found", listOf("Location: /bucle", "Content-Length: 0"), ByteArray(0))
                    "/404" -> responder(
                        "404 Not Found",
                        listOf("Content-Length: 8"),
                        "no está".toByteArray(),
                    )
                    "/gordo" -> responder(
                        "200 OK",
                        listOf("Content-Length: 33554432"),
                        ByteArray(0),
                    )
                    "/sinlargo" -> {
                        // Sin Content-Length y cerrando al terminar: el cliente
                        // tiene que leer hasta el final y no colgarse.
                        val cabecera = "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n"
                        salida.write(cabecera.toByteArray(Charsets.ISO_8859_1))
                        salida.write(png1x1)
                        salida.flush()
                    }
                    else -> responder(
                        "200 OK",
                        listOf("Content-Type: image/png", "Content-Length: ${png1x1.size}"),
                        png1x1,
                    )
                }
            }
        }
    }

    private fun arrancarServidor() {
        val s = MiniServidor()
        servidor = s
        base = "http://127.0.0.1:${s.puerto}"
    }

    @Test
    fun `una pagina por http trae los mismos bytes que el archivo`() {
        arrancarServidor()
        val bytes = DescargadorDeBytes().leer("$base/ok.png")
        assertEquals(png1x1.size, bytes.size)
        assertTrue(bytes.contentEquals(png1x1))
    }

    @Test
    fun `una redireccion se sigue y no se devuelve el 302`() {
        arrancarServidor()
        val bytes = DescargadorDeBytes().leer("$base/redirige")
        assertTrue("tras la redirección llegan los bytes de la imagen", bytes.contentEquals(png1x1))
    }

    @Test
    fun `un 404 se convierte en un motivo y no en una pagina vacia`() {
        arrancarServidor()
        val e = runCatching { DescargadorDeBytes().leer("$base/404") }.exceptionOrNull()
        assertNotNull("un 404 no puede devolver bytes", e)
        assertTrue(e!!.message!!.contains("404"))
    }

    @Test
    fun `un bucle de redirecciones termina con motivo`() {
        arrancarServidor()
        val e = runCatching { DescargadorDeBytes().leer("$base/bucle") }.exceptionOrNull()
        assertNotNull(e)
        assertTrue("el motivo habla de redirecciones: ${e!!.message}", e.message!!.contains("redireccion"))
    }

    @Test
    fun `un servidor que miente en el tamano se corta`() {
        arrancarServidor()
        val e = runCatching {
            DescargadorDeBytes(maxBytes = 1024).leer("$base/gordo")
        }.exceptionOrNull()
        assertNotNull("si el Content-Length pasa del tope, no se empieza a bajar", e)
        assertTrue("el motivo es el tope: ${e!!.message}", e.message!!.contains("MiB"))
    }

    @Test
    fun `sin Content-Length se lee hasta el final y no se cuelga`() {
        arrancarServidor()
        val bytes = DescargadorDeBytes().leer("$base/sinlargo")
        assertTrue("los bytes llegan igual", bytes.contentEquals(png1x1))
    }

    @Test
    fun `un archivo que pasa del tope no se carga en memoria`() {
        val gordo = File(raiz, "gorda.jpg")
        gordo.writeBytes(ByteArray(2048))
        val e = runCatching {
            DescargadorDeBytes(maxBytes = 1024).leer(gordo.absolutePath)
        }.exceptionOrNull()
        assertNotNull("un archivo por encima del tope tiene que fallar", e)
        assertTrue(e!!.message!!.contains("demasiado grande"))
    }

    @Test
    fun `una ruta que no existe dice que no existe`() {
        val e = runCatching {
            DescargadorDeBytes().leer(File(raiz, "fantasma.jpg").absolutePath)
        }.exceptionOrNull()
        assertNotNull(e)
        assertTrue(e!!.message!!.contains("no existe"))
    }

    // ── El estado de la pantalla, alimentado por la cadena real ────────────

    @Test
    fun `el estado del lector sale de las paginas que devolvio la fuente`() = runBlocking {
        val obra = File(raiz, "Solo Leveling")
        pagina(File(obra, "Capítulo 1"), "1.png")
        pagina(File(obra, "Capítulo 1"), "2.png")
        pagina(File(obra, "Capítulo 1"), "10.png")

        val fuente = LocalFolderSource(raiz = raiz)
        RepositorioGlobal.registrar(fuente)
        val repo = RepositorioGlobal.obtener()
        val detalle = repo.detalle(fuente.id, obra.absolutePath)!!
        val paginas = repo.paginas(fuente.id, detalle.capitulos[0].url).map { it.url }

        // Lo que hace ReaderActivity al recibir la lista.
        val estado = ReaderState.desdePaginas(paginas, inicial = 0, modo = ReaderState.Modo.WEBTOON)
        assertEquals(3, estado.total)
        assertEquals("1 / 3", estado.etiqueta())

        // Y que avance hasta el final sin salirse.
        assertTrue(estado.siguiente())
        assertEquals("2 / 3", estado.etiqueta())
        assertTrue(estado.siguiente())
        assertTrue(estado.esUltima)
        assertFalse("la última página no puede avanzar", estado.siguiente())

        // La página 10 va la tercera, no la segunda: el estado respeta el orden
        // natural que puso la fuente.
        assertEquals("10.png", File(paginas[2]).name)
    }

    @Test
    fun `un marcapaginas mayor que el capitulo abre en la ultima pagina sin romper`() = runBlocking {
        val obra = File(raiz, "Obra corta")
        pagina(File(obra, "Capítulo 1"), "1.png")
        pagina(File(obra, "Capítulo 1"), "2.png")

        val fuente = LocalFolderSource(raiz = raiz)
        RepositorioGlobal.registrar(fuente)
        val repo = RepositorioGlobal.obtener()
        val detalle = repo.detalle(fuente.id, obra.absolutePath)!!
        val paginas = repo.paginas(fuente.id, detalle.capitulos[0].url).map { it.url }

        // El usuario leyó 40 páginas de un capítulo que ahora tiene 2.
        val estado = ReaderState.desdePaginas(paginas, inicial = 40, modo = ReaderState.Modo.RTL)
        assertEquals(2, estado.total)
        assertEquals(1, estado.indice)
        assertEquals("2 / 2", estado.etiqueta())
        assertEquals(1f, estado.progreso)
    }

    @Test
    fun `un capitulo sin paginas deja el lector en estado vacio y no lanza`() {
        val estado = ReaderState.desdePaginas(emptyList(), inicial = 3)
        assertTrue(estado.vacio)
        assertEquals(0, estado.total)
        assertEquals("sin páginas", estado.etiqueta())
        assertEquals(0f, estado.progreso)
        assertFalse(estado.siguiente())
        assertFalse(estado.anterior())
    }

    @Test
    fun `sin fuentes registradas el lector no finge paginas`() = runBlocking {
        val repo = RepositorioGlobal.obtener()
        assertEquals(emptyList<Any>(), repo.paginas(7L, "cualquier-cosa"))
        assertNull(repo.detalle(7L, "cualquier-cosa"))
    }
}
