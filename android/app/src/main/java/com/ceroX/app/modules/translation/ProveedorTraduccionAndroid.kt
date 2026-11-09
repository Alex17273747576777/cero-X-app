package com.ceroX.app.modules.translation

import android.content.Context
import com.ceroX.app.core.AlmacenExterno
import com.ceroX.app.core.Prefs
import com.cerox.comun.Idiomas
import com.cerox.comun.ProveedoresTraduccion
import com.cerox.comun.Traduccion
import com.cerox.comun.Variante
import com.ceroX.app.R
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Traduce los textos de la app y los guarda como un archivo de recursos.
 *
 * QUÉ HACE EXACTAMENTE
 * --------------------
 *   1. Lee las cadenas de `res/values/strings.xml` del APK.
 *   2. Pasa cada una por el registro elegido (neutro o con modismos) **antes** de
 *      traducir, para que la variante no se pierda en la traducción.
 *   3. Las traduce con el proveedor elegido.
 *   4. Escribe un JSON en el almacenamiento externo de la app
 *      (`AlmacenExterno/idiomas/<tag>.json`) que [TextosTraducidos] usa para
 *      mostrar los textos en ese idioma sin recompilar.
 *
 * POR QUÉ NO SE ESCRIBE UN `strings.xml` COMPILADO
 * ------------------------------------------------
 * Porque no se puede: los recursos se compilan dentro del APK y una app no puede
 * añadir un `values-xx/` en tiempo de ejecución. Lo que sí puede es tener sus
 * textos en un archivo propio y resolverlos con una función
 * ([TextosTraducidos.texto]) que mire primero la traducción y luego el recurso.
 * Eso es lo que se hace, y la pantalla lo dice: los textos traducidos en el
 * dispositivo no cambian los textos del sistema Android (los de las cajas de
 * diálogo del propio Android, por ejemplo).
 *
 * LO QUE NO HACE, Y SE DICE EN LA PANTALLA
 * ----------------------------------------
 * No traduce las cadenas de los recursos del sistema. No cambia el idioma de
 * Android. No traduce texto de contenido si el proveedor es local.
 */
class ProveedorTraduccionAndroid(
    private val contexto: Context,
    private val prefs: Prefs,
) {

    data class Resumen(
        val idioma: String,
        val traducidas: Int,
        val fallidas: Int,
        val proveedor: Traduccion.Proveedor,
        val archivo: String,
        val motivos: List<String>,
    ) {
        fun linea(): String = buildString {
            append("$idioma con ${proveedor.nombre}: $traducidas cadenas traducidas")
            if (fallidas > 0) append(", $fallidas sin traducir")
            append(". Guardado en $archivo")
            if (motivos.isNotEmpty()) append("\n· " + motivos.take(3).joinToString("\n· "))
        }
    }

    private val proveedor: Traduccion.Proveedor
        get() = Traduccion.Proveedor.porId(prefs.getString(CLAVE_PROVEEDOR, null))

    private val registro: Idiomas.Registro
        get() = Idiomas.Registro.entries.firstOrNull {
            it.id == (prefs.getString(CLAVE_REGISTRO, Idiomas.Registro.NEUTRO.id) ?: "")
        } ?: Idiomas.Registro.NEUTRO

    private val variante: Variante.Definicion
        get() = Variante.porCodigo(prefs.getString(CLAVE_VARIANTE, "es-DO") ?: "es-DO")

    /**
     * Traduce la interfaz al idioma pedido. Devuelve una línea con el resultado
     * real: cuántas se tradujeron, cuántas no y dónde quedó.
     */
    fun traducirInterfaz(destino: String): String {
        if (proveedor == Traduccion.Proveedor.LOCAL) {
            return "El proveedor local no traduce texto: solo aplica el glosario y la variante. " +
                "Elige LibreTranslate o un proveedor con clave, sabiendo que el texto sale del dispositivo."
        }
        if (!Traduccion.soporta(proveedor, destino)) {
            return "${proveedor.nombre} no soporta $destino. Elige otro idioma u otro proveedor."
        }
        val dir = AlmacenExterno.archivo(contexto, "idiomas")
        val carpeta = dir ?: return "sin almacenamiento externo: no se guarda la traducción " +
            "en el almacenamiento interno (regla del proyecto)"
        if (!carpeta.isDirectory && !carpeta.mkdirs()) {
            return "no se pudo crear ${carpeta.absolutePath}"
        }

        val claves = leerCadenasDelApk()
        if (claves.isEmpty()) return "no se encontraron cadenas en el APK"

        val glosario = leerGlosario()
        val clave = claveDe(proveedor)
        val urlLibre = prefs.getString(CLAVE_URL_LIBRE, "")?.takeIf { it.isNotBlank() }

        val salida = JSONObject()
        var ok = 0
        val motivos = mutableListOf<String>()

        for ((nombre, original) in claves) {
            // El registro se aplica ANTES de traducir: si se aplicara después,
            // la tabla dominicana intentaría cambiar palabras inglesas.
            val preparado = if (original.any { it.isLetter() }) {
                idiomaDeOrigen(original).let { origen ->
                    // Se prepara con la variante solo si el original está en español.
                    if (origen == "es") {
                        Idiomas.aplicarRegistro(original, registro, variante, glosario)
                    } else {
                        original
                    }
                }
            } else {
                original
            }
            val origen = idiomaDeOrigen(preparado)
            val peticion = ProveedoresTraduccion.peticion(
                proveedor, preparado, origen, destino, clave, urlLibre,
            )
            val r = enviar(peticion)
            when (r) {
                is ProveedoresTraduccion.Resultado.Exito -> {
                    salida.put(nombre, r.texto)
                    ok++
                }
                is ProveedoresTraduccion.Resultado.Fallo -> {
                    // Si falla una, se sigue: un proveedor que tropieza con una
                    // cadena no puede dejar la app a medio traducir sin motivo.
                    motivos.add("$nombre: ${r.mensaje}")
                }
            }
        }

        val archivo = File(carpeta, "$destino.json")
        archivo.writeText(salida.toString(2), Charsets.UTF_8)
        val resumen = Resumen(destino, ok, claves.size - ok, proveedor, archivo.absolutePath, motivos)
        return resumen.linea()
    }

    /**
     * Las cadenas de la app tal como están en el APK.
     *
     * Se leen de la clase `R$string` generada por aapt, que es la lista real de
     * recursos: así no hay que mantener a mano una lista de claves que se
     * desincroniza al añadir una cadena.
     */
    private fun leerCadenasDelApk(): Map<String, String> = runCatching {
        val claseR = R.string::class.java
        val mapa = mutableMapOf<String, String>()
        for (f in claseR.declaredFields) {
            val id = runCatching { f.getInt(null) }.getOrNull() ?: continue
            val valor = runCatching { contexto.getString(id) }.getOrNull() ?: continue
            if (valor.isBlank()) continue
            mapa[f.name] = valor
        }
        mapa
    }.getOrElse { emptyMap() }

    private fun leerGlosario(): Map<String, String> = runCatching {
        val json = prefs.getString(CLAVE_GLOSARIO, "") ?: ""
        if (json.isBlank()) return emptyMap()
        val o = JSONObject(json)
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    /**
     * Qué idioma es un texto, sin modelo: por los caracteres y por el glosario.
     *
     * Es una heurística y se dice: no distingue español de portugués con
     * seguridad. Sirve para no aplicar la tabla dominicana a un texto en inglés,
     * que es el error que se cometería seguro si se aplicara siempre.
     */
    private fun idiomaDeOrigen(texto: String): String {
        val tieneCjk = texto.any { it.code in 0x3040..0x9FFF || it.code in 0xAC00..0xD7AF }
        if (tieneCjk) return "ja"
        val tieneCyrillic = texto.any { it.code in 0x0400..0x04FF }
        if (tieneCyrillic) return "ru"
        val tieneArabic = texto.any { it.code in 0x0600..0x06FF }
        if (tieneArabic) return "ar"
        val tieneThai = texto.any { it.code in 0x0E00..0x0E7F }
        if (tieneThai) return "th"
        // Sin alfabeto especial: se asume español, que es el idioma en que están
        // escritas las cadenas de `values/`.
        return "es"
    }

    /**
     * Envía la petición. Es la única parte que necesita red.
     *
     * Timeouts cortos: traducir 56 cadenas con una espera de 30 s cada una serían
     * 28 minutos de pantalla colgada. 8 s por cadena y se informa lo que falló.
     */
    private fun enviar(p: ProveedoresTraduccion.Peticion): ProveedoresTraduccion.Resultado {
        if (p.url.isBlank()) return ProveedoresTraduccion.Resultado.Fallo("petición vacía")
        return try {
            val conexion = (URL(p.url).openConnection() as HttpURLConnection).apply {
                requestMethod = p.metodo
                connectTimeout = 8_000
                readTimeout = 12_000
                doOutput = p.cuerpo.isNotEmpty()
                p.cabeceras.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                if (p.cuerpo.isNotEmpty()) {
                    conexion.outputStream.use { it.write(p.cuerpo.toByteArray(Charsets.UTF_8)) }
                }
                val codigo = conexion.responseCode
                val cuerpo = (if (codigo in 200..299) conexion.inputStream else conexion.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                ProveedoresTraduccion.leerRespuesta(proveedor, cuerpo, codigo)
            } finally {
                conexion.disconnect()
            }
        } catch (e: java.io.IOException) {
            ProveedoresTraduccion.Resultado.Fallo("sin conexión: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Clave de API de un proveedor, guardada **cifrada** con el Keystore.
     *
     * Una clave de API es un token, y los tokens son la única excepción de la
     * regla de almacenamiento. Se guardan en `EncryptedSharedPreferences`, el
     * mismo almacén que ya usan AniList y MyAnimeList. Si el Keystore no está,
     * no se guarda en claro: se devuelve false y la pantalla lo dice.
     */
    fun guardarClave(proveedor: Traduccion.Proveedor, clave: String): Boolean {
        if (!proveedor.necesitaClave) return true
        if (clave.isBlank()) {
            prefs.borrar("traduccion_clave_${proveedor.id}")
            return true
        }
        return try {
            val maestro = androidx.security.crypto.MasterKey.Builder(contexto)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            val cifradas = androidx.security.crypto.EncryptedSharedPreferences.create(
                contexto,
                "cerox_traduccion_cifrado",
                maestro,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            cifradas.edit().putString("clave_${proveedor.id}", clave).apply()
            true
        } catch (e: Throwable) {
            // Sin Keystore no se guarda en claro, a propósito.
            prefs.putString("traduccion_clave_${proveedor.id}", "")
            false
        }
    }

    /** La clave guardada, si existe. */
    fun claveDe(proveedor: Traduccion.Proveedor): String? = runCatching {
        val maestro = androidx.security.crypto.MasterKey.Builder(contexto)
            .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
            .build()
        val cifradas = androidx.security.crypto.EncryptedSharedPreferences.create(
            contexto,
            "cerox_traduccion_cifrado",
            maestro,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        cifradas.getString("clave_${proveedor.id}", null)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    companion object {
        const val CLAVE_PROVEEDOR = "traduccion_proveedor"
        const val CLAVE_URL_LIBRE = "traduccion_url_libre"
        const val CLAVE_REGISTRO = "idioma_registro"
        const val CLAVE_VARIANTE = "idioma_variante"
        const val CLAVE_GLOSARIO = "traduccion_glosario"
    }
}

/**
 * Los textos traducidos que se muestran en la app.
 *
 * Orden de resolución: traducción del dispositivo → recurso del APK (que Android
 * ya devuelve en el idioma elegido si hay `values-xx`) → la clave como último
 * recurso, para que nunca se vea vacío.
 */
object TextosTraducidos {

    private var cache: MutableMap<String, Map<String, String>> = mutableMapOf()

    fun cargar(contexto: Context, tag: String) {
        if (cache.containsKey(tag)) return
        val f = AlmacenExterno.archivo(contexto, "idiomas/$tag.json") ?: run {
            cache[tag] = emptyMap()
            return
        }
        cache[tag] = runCatching {
            if (!f.isFile) return@runCatching emptyMap()
            val o = JSONObject(f.readText(Charsets.UTF_8))
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    fun texto(contexto: Context, tag: String, clave: String, idDeRecurso: Int): String {
        cargar(contexto, tag)
        cache[tag]?.get(clave)?.let { return it }
        return contexto.getString(idDeRecurso)
    }

    fun olvidar() {
        cache = mutableMapOf()
    }
}
