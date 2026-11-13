package com.ceroX.app.modules.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Opt-in: repite la auditoría del catálogo contra la API real del alojamiento.
 *
 * Se activa con `CEROX_LIVE_CATALOGO=1`; sin esa variable se omite, igual que
 * las demás pruebas de red real del proyecto.
 *
 * Comprueba dos cosas que ninguna prueba sin red puede comprobar:
 *
 *  1. que las licencias grabadas en el código siguen siendo las que responde la
 *     fuente de verdad, y
 *  2. que los enlaces marcados como muertos siguen muertos.
 *
 * El segundo punto falla a propósito si un repositorio revive: no es un fallo
 * del código, es el aviso de que hay que volver a auditar en vez de dejar el
 * catálogo mintiendo por omisión. Lo mismo si una licencia cambia de SPDX.
 *
 * Sólo imprime id, HTTP, SPDX y archivado. Nunca vuelca cuerpos de respuesta ni
 * tokens, y no hace falta ninguna credencial: son endpoints públicos con un
 * límite de peticiones por IP, por eso la prueba va de uno en uno y con pausa.
 */
class CatalogoLicenciasRedRealTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun la_fuente_sigue_diciendo_lo_que_grabo_la_auditoria() {
        assumeTrue("Definir CEROX_LIVE_CATALOGO=1", System.getenv("CEROX_LIVE_CATALOGO") == "1")
        val http = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
        val modulo = CatalogModule()
        val fichas = modulo.fichasAuditadas()
        assertTrue("el catálogo no trae auditoría", fichas.isNotEmpty())

        // Sin credencial el límite es de 60 peticiones por IP y hora, y repasar
        // la auditoría son 25. Se mira antes de gastar ninguna: /rate_limit no
        // cuenta contra el límite, y así la prueba se omite entera con un
        // mensaje que dice cuánto falta, en vez de morir a mitad con un 403.
        http.newCall(
            Request.Builder().url("https://api.github.com/rate_limit")
                .header("User-Agent", "cerox-catalog-audit").build(),
        ).execute().use { respuesta ->
            if (respuesta.code == 200) {
                val raiz = json.parseToJsonElement(respuesta.body.string()) as? JsonObject
                val core = (raiz?.get("resources") as? JsonObject)?.get("core") as? JsonObject
                val quedan = (core?.get("remaining") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
                val reinicio = (core?.get("reset") as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
                assumeTrue(
                    "quedan $quedan peticiones en api.github.com y repasar la auditoría necesita 25; " +
                        "el límite se reinicia en el instante unix $reinicio",
                    quedan >= fichas.size,
                )
                println("CATALOGO_AUDITORIA peticiones_disponibles=$quedan necesarias=${fichas.size}")
            }
        }

        var vivos = 0
        var muertos = 0
        var comprobados = 0
        for ((id, ficha) in fichas) {
            val endpoint = ficha.endpoint ?: continue
            val peticion = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "cerox-catalog-audit")
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(peticion).execute().use { respuesta ->
                val codigo = respuesta.code
                assumeTrue(
                    "la API respondió HTTP $codigo para $id (¿límite de peticiones por IP alcanzado?)",
                    codigo == 200 || codigo == 404,
                )
                if (codigo == 404) {
                    assertEquals(
                        "$id está marcado vivo en el código pero la fuente responde 404: hay que re-auditar",
                        404, ficha.http ?: -1,
                    )
                    muertos++
                    println("CATALOGO_AUDITORIA id=$id http=404 sigue_muerto=true")
                    return@use
                }
                val raiz = json.parseToJsonElement(respuesta.body.string()) as? JsonObject
                assertTrue("$id no devolvió un objeto JSON", raiz != null)
                val licencia = raiz?.get("license") as? JsonObject
                val spdx = (licencia?.get("spdx_id") as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.isNotBlank() && it != "null" }
                val archivado = (raiz?.get("archived") as? JsonPrimitive)?.booleanOrNull == true
                assertEquals(
                    "$id cambió de licencia en la fuente (antes ${ficha.spdxApi}, ahora $spdx): hay que re-auditar",
                    ficha.spdxApi, spdx,
                )
                assertEquals("$id cambió de estado de archivado", ficha.archivado, archivado)
                assertEquals(
                    "$id cambió de estado HTTP en la fuente",
                    ficha.http ?: -1, codigo,
                )
                vivos++
                println("CATALOGO_AUDITORIA id=$id http=200 spdx=${spdx ?: "ninguna"} archivado=$archivado")
            }
            comprobados++
            Thread.sleep(400)
        }

        println("CATALOGO_AUDITORIA resumen comprobados=$comprobados vivos=$vivos muertos=$muertos")
        assertEquals("no se repasaron los 25 repositorios auditados", 25, comprobados)
        assertEquals("cambió el número de repositorios vivos", 21, vivos)
        assertEquals("cambió el número de enlaces muertos", 4, muertos)
        // La licencia que se enseña en la pantalla es la que dice la fuente.
        assertEquals(18, modulo.licenciasVerificadas().size)
    }
}
