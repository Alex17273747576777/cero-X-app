package com.cerox.comun

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La forma de las peticiones y la lectura de las respuestas de cada proveedor.
 *
 * Lo que se fija aquí, con capturas de la documentación pública de cada API
 * (consultada el 2026-11-08):
 *
 *   · LibreTranslate  → `{"translatedText":"Hola"}`
 *   · Google v2       → `{"data":{"translations":[{"translatedText":"Hola"}]}}`
 *   · DeepL           → `{"translations":[{"detected_source_language":"EN","text":"Hola"}]}`
 *   · Microsoft v3    → `[{"translations":[{"text":"Hola","to":"es"}]}]`
 *   · Youdao          → `{"errorCode":"0","translation":["Hola"]}`
 *
 * Y lo que **no** se hace: no se llama a ningún proveedor. Mandar texto a Google
 * o a un LibreTranslate público desde una prueba es usar infraestructura de otros.
 */
class ProveedoresTraduccionTest {

    private val texto = "Hola, ¿cómo estás?"

    // ── Construcción de la petición ───────────────────────────────────────

    @Test
    fun libretranslate_manda_json_a_translate_y_no_lleva_clave() {
        val p = ProveedoresTraduccion.peticion(
            Traduccion.Proveedor.LIBRE, texto, "es", "en",
        )
        assertEquals("https://translate.disroot.org/translate", p.url)
        assertEquals("POST", p.metodo)
        assertEquals("application/json", p.cabeceras["Content-Type"])
        assertTrue(p.cuerpo.contains("\"target\":\"en\""))
        assertTrue(p.cuerpo.contains("\"source\":\"es\""))
        assertFalse(
            p.cabeceras.keys.any { it.contains("Authorization", true) },
            "LibreTranslate no usa clave; mandar una cabecera de autorización vacía es ruido",
        )
    }

    @Test
    fun libretranslate_respeta_el_servidor_del_usuario() {
        val p = ProveedoresTraduccion.peticion(
            Traduccion.Proveedor.LIBRE, texto, "es", "en",
            urlDelUsuario = "https://mi-traductor.local/",
        )
        // Sin doble barra al final: `${url}/translate` ya pone una.
        assertEquals("https://mi-traductor.local/translate", p.url)
    }

    @Test
    fun deepl_usa_la_cabecera_y_los_idiomas_en_mayusculas() {
        val p = ProveedoresTraduccion.peticion(
            Traduccion.Proveedor.DEEPL, texto, "es", "en", clave = "abc",
        )
        assertEquals("DeepL-Auth-Key abc", p.cabeceras["Authorization"])
        assertTrue(p.cuerpo.contains("\"target_lang\":\"EN\""), p.cuerpo)
        assertTrue(p.cuerpo.contains("\"source_lang\":\"ES\""), p.cuerpo)
    }

    @Test
    fun deepl_con_clave_gratuita_va_al_servidor_gratuito() {
        val gratis = ProveedoresTraduccion.peticion(
            Traduccion.Proveedor.DEEPL, texto, "es", "en", clave = "abc:fx",
        )
        val pago = ProveedoresTraduccion.peticion(
            Traduccion.Proveedor.DEEPL, texto, "es", "en", clave = "abc",
        )
        assertEquals("https://api-free.deepl.com/v2/translate", gratis.url)
        assertEquals("https://api.deepl.com/v2/translate", pago.url)
    }

    @Test
    fun google_lleva_la_clave_en_cabecera_y_el_texto_en_arreglo() {
        val p = ProveedoresTraduccion.peticion(
            Traduccion.Proveedor.GOOGLE, texto, "es", "en", clave = "clave-google",
        )
        assertEquals("clave-google", p.cabeceras["X-Goog-Api-Key"])
        assertTrue(p.cuerpo.contains("\"q\":["), "Google espera un arreglo en q: ${p.cuerpo}")
    }

    @Test
    fun microsoft_pone_la_version_los_idiomas_en_la_url() {
        val p = ProveedoresTraduccion.peticion(
            Traduccion.Proveedor.MICROSOFT, texto, "es", "en", clave = "k",
        )
        assertTrue(p.url.contains("api-version=3.0"), p.url)
        assertTrue(p.url.contains("from=es"), p.url)
        assertTrue(p.url.contains("to=en"), p.url)
        assertEquals("k", p.cabeceras["Ocp-Apim-Subscription-Key"])
    }

    @Test
    fun youdao_va_por_formulario_y_codifica_el_texto() {
        val p = ProveedoresTraduccion.peticion(
            Traduccion.Proveedor.YOUDAO, "hola mundo & más", "es", "zh", clave = "k",
        )
        assertEquals("application/x-www-form-urlencoded", p.cabeceras["Content-Type"])
        // El & del texto no puede romper el formulario: tiene que ir escapado.
        assertTrue(p.cuerpo.contains("q=hola%20mundo%20%26%20m%C3%A1s"), p.cuerpo)
        assertTrue(p.cuerpo.contains("signType=v3"))
    }

    @Test
    fun la_descripcion_de_una_peticion_no_lleva_el_texto_ni_la_clave() {
        // Esto es lo que se escribe en el registro cuando algo falla. Si llevara
        // el texto, el registro del usuario tendría sus traducciones dentro.
        val p = ProveedoresTraduccion.peticion(
            Traduccion.Proveedor.DEEPL, "contenido privado del usuario", "es", "en", clave = "secreto:fx",
        )
        val d = p.descripcion()
        assertFalse(d.contains("contenido privado"), "la descripción filtra el texto: $d")
        assertFalse(d.contains("secreto"), "la descripción filtra la clave: $d")
        assertTrue(d.contains("POST") && d.contains("api-free"))
    }

    @Test
    fun el_proveedor_local_no_construye_ninguna_peticion() {
        val p = ProveedoresTraduccion.peticion(
            Traduccion.Proveedor.LOCAL, texto, "es", "en",
        )
        assertEquals("", p.url)
        assertEquals(ProveedoresTraduccion.TipoDeCuerpo.SIN_CUERPO, p.tipoDeCuerpo)
    }

    // ── Lectura de respuestas reales ──────────────────────────────────────

    @Test
    fun lee_la_respuesta_de_libretranslate() {
        val cuerpo = """{"translatedText":"Hello, how are you?"}"""
        val r = ProveedoresTraduccion.leerRespuesta(Traduccion.Proveedor.LIBRE, cuerpo, 200)
        assertEquals("Hello, how are you?", r.comoTexto())
    }

    @Test
    fun lee_la_respuesta_de_google() {
        val cuerpo = """{"data":{"translations":[{"translatedText":"Hello, how are you?",""" +
            """"detectedSourceLanguage":"es"}]}}"""
        val r = ProveedoresTraduccion.leerRespuesta(Traduccion.Proveedor.GOOGLE, cuerpo, 200)
        assertEquals("Hello, how are you?", r.comoTexto())
    }

    @Test
    fun lee_la_respuesta_de_deepl() {
        val cuerpo = """{"translations":[{"detected_source_language":"ES","text":"Hello, how are you?"}]}"""
        val r = ProveedoresTraduccion.leerRespuesta(Traduccion.Proveedor.DEEPL, cuerpo, 200)
        assertEquals("Hello, how are you?", r.comoTexto())
    }

    @Test
    fun lee_la_respuesta_de_microsoft() {
        val cuerpo = """[{"detectedLanguage":{"language":"es","score":1.0},""" +
            """"translations":[{"text":"Hello, how are you?","to":"en"}]}]"""
        val r = ProveedoresTraduccion.leerRespuesta(Traduccion.Proveedor.MICROSOFT, cuerpo, 200)
        assertEquals("Hello, how are you?", r.comoTexto())
    }

    @Test
    fun lee_la_respuesta_de_youdao_y_su_codigo_de_error() {
        val bien = """{"errorCode":"0","translation":["Hello, how are you?"]}"""
        assertEquals(
            "Hello, how are you?",
            ProveedoresTraduccion.leerRespuesta(Traduccion.Proveedor.YOUDAO, bien, 200).comoTexto(),
        )
        // Youdao contesta SIEMPRE HTTP 200 y pone el error en errorCode.
        val mal = """{"errorCode":"108","translation":null}"""
        val r = ProveedoresTraduccion.leerRespuesta(Traduccion.Proveedor.YOUDAO, mal, 200)
        assertFalse(r.esExito(), "errorCode 108 es un fallo aunque el HTTP sea 200")
        assertTrue((r as ProveedoresTraduccion.Resultado.Fallo).mensaje.contains("108"))
    }

    @Test
    fun un_fallo_trae_el_motivo_y_no_un_texto_vacio() {
        // Un traductor que devuelve "" cuando falla deja al usuario creyendo que
        // la traducción salió vacía en vez de saber que no se tradujo.
        val r = ProveedoresTraduccion.leerRespuesta(
            Traduccion.Proveedor.LIBRE,
            """{"error":"Language not supported"}""",
            400,
        )
        assertFalse(r.esExito())
        val msg = (r as ProveedoresTraduccion.Resultado.Fallo).mensaje
        assertTrue(msg.contains("400"), msg)
        assertTrue(msg.contains("no soportado") || msg.contains("Language"), msg)
    }

    @Test
    fun el_429_de_libretranslate_se_explica_como_lo_que_es() {
        val r = ProveedoresTraduccion.leerRespuesta(
            Traduccion.Proveedor.LIBRE, """{"error":"Too many requests"}""", 429,
        )
        val msg = (r as ProveedoresTraduccion.Resultado.Fallo).mensaje
        assertTrue(msg.contains("demasiadas peticiones"), msg)
        assertTrue(msg.contains("cambia de servidor"), "tiene que decir qué puede hacer el usuario: $msg")
    }

    @Test
    fun lee_caracteres_no_latinos_y_escapes() {
        val cuerpo = """{"translatedText":"こんにちは、\"amigo\"\n¿qué tal?"}"""
        val r = ProveedoresTraduccion.leerRespuesta(Traduccion.Proveedor.LIBRE, cuerpo, 200)
        assertEquals("こんにちは、\"amigo\"\n¿qué tal?", r.comoTexto())
    }

    @Test
    fun lee_escapes_unicode() {
        val cuerpo = """{"translatedText":"\u4f60\u597d"}"""
        val r = ProveedoresTraduccion.leerRespuesta(Traduccion.Proveedor.LIBRE, cuerpo, 200)
        assertEquals("你好", r.comoTexto())
    }

    @Test
    fun una_respuesta_sin_el_campo_no_se_interpreta_como_exito() {
        val r = ProveedoresTraduccion.leerRespuesta(
            Traduccion.Proveedor.GOOGLE, """{"data":{}}""", 200,
        )
        assertFalse(r.esExito(), "sin translatedText no hay traducción, aunque el HTTP sea 200")
    }
}
