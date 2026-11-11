package com.ceroX.app.modules.iptv

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/**
 * Una fuente que añadió el usuario.
 *
 * No hay ninguna fuente por defecto: la lista empieza vacía y se llena con lo que
 * el usuario pegue. cero-X no distribuye listas de canales, y este fichero es donde
 * esa decisión está escrita en código.
 */
data class Fuente(
    val nombre: String,
    val url: String,
    /** "m3u" para canales, "xmltv" para guía. */
    val tipo: String,
    /**
     * Huella SHA-256 que quien publica la fuente dice que tiene.
     *
     * Es opcional porque una lista que pega un usuario no la firma nadie. Cuando sí
     * está, se comprueba y si no cuadra no se usa: es lo único que permite saber que
     * lo que llegó es lo que el publicador dijo que iba a llegar.
     */
    val huellaEsperada: String?,
    val anadida: Long,
    /** Huella de lo que se descargó la última vez, para notar si cambió. */
    val ultimaHuella: String? = null,
)

/**
 * Qué URLs se aceptan como fuente.
 *
 * La regla que importa: **nada que apunte al propio APK**. Los esquemas `file:`,
 * `content:`, `asset:` y `android.resource:` darían exactamente el comportamiento
 * que se rechazó, que es llevar las listas dentro de la aplicación, así que se
 * rechazan aquí y no en la interfaz, donde nadie las mira.
 */
object PoliticaFuentes {

    val TIPOS = listOf("m3u", "xmltv")

    private val ESQUEMAS_INTERNOS = listOf("file:", "content:", "asset:", "android.resource:")

    /** null si la URL vale; si no, la razón, que es lo que se enseña al usuario. */
    fun urlAceptable(url: String): String? {
        val u = url.trim()
        if (u.isEmpty()) return "La URL está vacía"
        if (u.any { it.isWhitespace() }) return "La URL tiene espacios en blanco"
        val minus = u.lowercase()
        for (esquema in ESQUEMAS_INTERNOS) {
            if (minus.startsWith(esquema)) {
                return "Las fuentes tienen que venir de fuera de la app: '$esquema' apunta al " +
                    "propio APK o al almacenamiento local, y cero-X no lleva listas empaquetadas"
            }
        }
        if (!minus.startsWith("https://") && !minus.startsWith("http://")) {
            return "La URL no empieza por https:// ni por http://, y no se acepta ningún otro esquema"
        }
        return null
    }

    /** null si el tipo vale; si no, los que valen. */
    fun tipoAceptable(tipo: String): String? =
        if (tipo in TIPOS) null else "El tipo '$tipo' no se conoce; los que hay son ${TIPOS.joinToString(" y ")}"

    /** null si el nombre sirve; si no, por qué. Se compara sin distinguir mayúsculas. */
    fun nombreAceptable(nombre: String, existentes: List<Fuente>): String? {
        val n = nombre.trim()
        if (n.isEmpty()) return "El nombre está vacío"
        if (n.length > 80) return "El nombre pasa de 80 caracteres"
        val repetido = existentes.any { it.nombre.equals(n, ignoreCase = true) }
        if (repetido) return "Ya hay una fuente llamada '$n'"
        return null
    }
}

/** Huellas, que es como se comprueba de dónde viene algo. */
object Verificador {

    /** SHA-256 en hexadecimal minúsculo. */
    fun sha256(bytes: ByteArray): String = try {
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        ""
    }

    /**
     * Compara la huella esperada con la obtenida, sin importar mayúsculas ni espacios.
     *
     * Devuelve null si cuadran. Si no hay huella esperada devuelve la razón: no es
     * lo mismo "comprobado" que "no se pudo comprobar", y llamar éxito al segundo es
     * justo lo que este proyecto viene quitando.
     */
    fun comprobar(esperada: String?, obtenida: String): String? = when {
        obtenida.isEmpty() -> "No se pudo calcular la huella de lo descargado"
        esperada.isNullOrBlank() ->
            "Quien publica esta fuente no declaró ninguna huella, así que no hay forma " +
                "de comprobar su origen. La huella de lo descargado es $obtenida"
        esperada.trim().equals(obtenida.trim(), ignoreCase = true) -> null
        else ->
            "La huella no cuadra: la fuente declara ${esperada.trim()} y lo descargado " +
                "da $obtenida. No se usa"
    }
}

/** Lo que vino de una descarga, o por qué no vino nada. */
data class Descarga(
    val contenido: String?,
    val huella: String?,
    val contentType: String?,
    val estadoHttp: Int,
    val motivo: String?,
) {
    val correcta: Boolean get() = contenido != null && motivo == null
}

/**
 * Descarga fuentes de fuera de la app.
 *
 * Con `HttpURLConnection`, que es lo que ya usa el proyecto, y tres límites que no
 * son decorativos: sólo http y https (ver [PoliticaFuentes]), un tope de bytes para
 * que una respuesta enorme no se coma la memoria, y tiempos de espera para que no se
 * quede colgado. Todo lo que sale mal vuelve como motivo en [Descarga].
 */
class CargadorExterno(
    private val topeBytes: Int = 8 * 1024 * 1024,
    private val milisEspera: Int = 15000,
    private val agente: String = "cero-X/1.0 (IPTV neutro; sin listas empaquetadas)",
) {

    fun descargar(url: String): Descarga {
        val rechazo = PoliticaFuentes.urlAceptable(url)
        if (rechazo != null) return Descarga(null, null, null, 0, rechazo)

        var conexion: HttpURLConnection? = null
        return try {
            conexion = URI(url).toURL().openConnection() as HttpURLConnection
            conexion.connectTimeout = milisEspera
            conexion.readTimeout = milisEspera
            conexion.instanceFollowRedirects = true
            conexion.setRequestProperty("User-Agent", agente)

            val estado = conexion.responseCode
            val tipo = conexion.contentType
            if (estado !in 200..299) {
                return Descarga(null, null, tipo, estado, "El servidor contestó $estado para $url")
            }

            val bytes = leerConTope(conexion.inputStream)
            if (bytes == null) {
                return Descarga(
                    null, null, tipo, estado,
                    "La respuesta pasa de $topeBytes bytes; se cortó la lectura para no " +
                        "llenar la memoria. Si la fuente es más grande, hace falta un tope mayor",
                )
            }
            Descarga(String(bytes, Charsets.UTF_8), Verificador.sha256(bytes), tipo, estado, null)
        } catch (e: java.net.UnknownHostException) {
            Descarga(null, null, null, 0, "No se pudo resolver el nombre de $url: " + describir(e))
        } catch (e: java.net.SocketTimeoutException) {
            Descarga(null, null, null, 0, "Se agotó la espera de $milisEspera ms al cargar $url")
        } catch (e: javax.net.ssl.SSLException) {
            // Un certificado que no vale no es un fallo de red: es una razón para parar.
            Descarga(null, null, null, 0, "Falló el TLS con $url: " + describir(e))
        } catch (e: Exception) {
            Descarga(null, null, null, 0, "No se pudo descargar $url: " + describir(e))
        } finally {
            try {
                conexion?.disconnect()
            } catch (e: Exception) {
                // Desconectar algo que ya se cayó lanza; no hay nada que hacer.
            }
        }
    }

    /** Lee hasta el tope. Devuelve null si se pasa, en vez de seguir leyendo. */
    private fun leerConTope(entrada: InputStream): ByteArray? {
        val salida = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        entrada.use { flujo ->
            while (true) {
                val leidos = flujo.read(buf)
                if (leidos < 0) break
                total += leidos
                if (total > topeBytes) return null
                salida.write(buf, 0, leidos)
            }
        }
        return salida.toByteArray()
    }

    private fun describir(e: Exception): String = e.message ?: e.javaClass.simpleName
}

/** Lo que hay guardado, y por qué no se pudo leer si no se pudo. */
data class Listado(val fuentes: List<Fuente>, val motivo: String?)

/** Lo que pasó al añadir una fuente. */
data class Anadido(val fuente: Fuente?, val motivo: String?)

/**
 * Las fuentes del usuario, guardadas en JSON en el directorio privado de la app.
 *
 * Se escribe en un fichero temporal y se renombra, porque un proceso que se mata a
 * mitad de la escritura dejaría si no un JSON a medias, y la vez siguiente no se
 * podría leer nada.
 */
class AlmacenFuentes(private val fichero: File) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val tipoLista = object : TypeToken<List<Fuente>>() {}.type

    fun listar(): Listado {
        if (!fichero.isFile) return Listado(emptyList(), null)
        return try {
            val texto = fichero.readText()
            if (texto.isBlank()) return Listado(emptyList(), null)
            val leidas: List<Fuente>? = gson.fromJson(texto, tipoLista)
            if (leidas == null) {
                Listado(emptyList(), "El fichero de fuentes no trae una lista legible")
            } else {
                Listado(leidas, null)
            }
        } catch (e: Exception) {
            // Se devuelve la lista vacía con la razón: borrar el fichero a escondidas
            // haría desaparecer las fuentes del usuario sin decirlo.
            Listado(
                emptyList(),
                "No se pudo leer ${fichero.name}: " + (e.message ?: e.javaClass.simpleName),
            )
        }
    }

    fun anadir(nombre: String, url: String, tipo: String, huellaEsperada: String?): Anadido {
        val actuales = listar().fuentes

        val malNombre = PoliticaFuentes.nombreAceptable(nombre, actuales)
        if (malNombre != null) return Anadido(null, malNombre)

        val malTipo = PoliticaFuentes.tipoAceptable(tipo)
        if (malTipo != null) return Anadido(null, malTipo)

        val malUrl = PoliticaFuentes.urlAceptable(url)
        if (malUrl != null) return Anadido(null, malUrl)

        val repetida = actuales.any { it.url.equals(url.trim(), ignoreCase = true) }
        if (repetida) return Anadido(null, "Esa URL ya está dada de alta en otra fuente")

        val fuente = Fuente(
            nombre = nombre.trim(),
            url = url.trim(),
            tipo = tipo,
            huellaEsperada = huellaEsperada?.trim()?.takeIf { it.isNotEmpty() },
            anadida = System.currentTimeMillis(),
        )
        val error = guardar(actuales + fuente)
        return if (error != null) Anadido(null, error) else Anadido(fuente, null)
    }

    /** null si se quitó; si no, la razón. */
    fun quitar(nombre: String): String? {
        val actuales = listar().fuentes
        val quedan = actuales.filterNot { it.nombre.equals(nombre.trim(), ignoreCase = true) }
        if (quedan.size == actuales.size) return "No hay ninguna fuente llamada '$nombre'"
        return guardar(quedan)
    }

    /** Anota la huella de lo descargado, para notar cambios entre cargas. */
    fun registrarHuella(nombre: String, huella: String): String? {
        val actuales = listar().fuentes
        var encontrada = false
        val nuevas = actuales.map {
            if (it.nombre.equals(nombre.trim(), ignoreCase = true)) {
                encontrada = true
                it.copy(ultimaHuella = huella)
            } else {
                it
            }
        }
        if (!encontrada) return "No hay ninguna fuente llamada '$nombre'"
        return guardar(nuevas)
    }

    /** null si se guardó; si no, por qué. */
    private fun guardar(fuentes: List<Fuente>): String? {
        // Cuerpo de bloque a propósito: Kotlin prohíbe los `return` dentro de una
        // función cuyo cuerpo es una expresión, y aquí hay tres salidas anticipadas.
        return try {
            val padre = fichero.parentFile
            if (padre != null && !padre.isDirectory && !padre.mkdirs()) {
                return "No se pudo crear el directorio ${padre.path}"
            }
            val temporal = File(fichero.path + ".tmp")
            temporal.writeText(gson.toJson(fuentes), Charsets.UTF_8)
            if (fichero.isFile && !fichero.delete()) {
                return "No se pudo sustituir ${fichero.name}"
            }
            if (!temporal.renameTo(fichero)) {
                return "No se pudo renombrar el fichero temporal a ${fichero.name}"
            }
            null
        } catch (e: Exception) {
            "No se pudieron guardar las fuentes: " + (e.message ?: e.javaClass.simpleName)
        }
    }
}
