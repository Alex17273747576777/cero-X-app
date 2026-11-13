package com.ceroX.app.modules.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La auditoría del catálogo, comprobada sobre los datos que de verdad se envían.
 *
 * Las cifras están fijadas a propósito: son el resultado medido el
 * [FECHA_AUDITORIA] contra api.github.com, codeberg.org y los archivos LICENSE.
 * Si cambian es porque alguien tocó el catálogo, así que el cambio tiene que ser
 * deliberado y volver a auditarse, no colarse en silencio.
 *
 * Lo que estas pruebas no hacen: no consultan la red. Que la fuente siga
 * diciendo hoy lo mismo lo comprueba [CatalogoLicenciasRedRealTest], que es
 * opt-in.
 */
class CatalogoAuditoriaTest {

    private val modulo = CatalogModule()
    private val apps = modulo.listApps()

    private fun porId(id: String): CatalogApp = apps.first { it.id == id }

    private val main: File by lazy {
        val f = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "android/app/src/main") }
            .firstOrNull { it.isDirectory }
        assertTrue("no encuentro android/app/src/main desde " + File("").absolutePath, f != null)
        f!!
    }

    private fun leer(rel: String): String {
        val f = File(File(main, "java/com/ceroX/app"), rel)
        assertTrue("falta $rel", f.exists())
        return f.readText()
    }

    @Test
    fun la_auditoria_esta_aplicada_desde_el_constructor() {
        // registerAllApps() corre en el bloque init. Si la tabla de auditoría
        // estuviera declarada después, al construirse estaría vacía y las 52
        // fichas saldrían sin auditar sin que fallara nada más: el fallo
        // silencioso que esta prueba corta.
        assertEquals(52, apps.size)
        assertEquals(25, modulo.fichasAuditadas().size)
        assertEquals(18, modulo.licenciasVerificadas().size)
        assertEquals(34, modulo.licenciasSinVerificar().size)
        assertEquals(21, apps.count { it.estadoEnlace == EstadoEnlace.VERIFICADO_200 })
    }

    @Test
    fun ninguna_licencia_es_un_marcador_que_parece_un_dato() {
        val vagas = apps.filter {
            it.license.isBlank() ||
                it.license.equals("Open Source", ignoreCase = true) ||
                it.license.equals("Unknown", ignoreCase = true) ||
                it.license.contains(" / ")
        }
        assertEquals(
            "quedan licencias que son un marcador, no un identificador: " + vagas.map { it.id },
            emptyList<CatalogApp>(),
            vagas,
        )
        for (a in apps) {
            if (a.licenciaVerificada) {
                assertFalse(a.id + " dice verificada y trae SIN_VERIFICAR", a.license == SIN_VERIFICAR)
                assertNotNull(a.id + " verificada sin decir dónde", a.licenciaFuente)
            } else {
                assertEquals(a.id + " no está verificada y no lo dice", SIN_VERIFICAR, a.license)
                assertNotNull(a.id + " sin verificar y sin motivo", a.licenciaNota)
                assertTrue(a.id + " con motivo vacío", a.licenciaNota!!.isNotBlank())
            }
        }
    }

    @Test
    fun lo_que_la_auditoria_desmiente_se_corrige_y_deja_constancia() {
        // Tres fichas declaraban una licencia que la fuente desmiente.
        assertEquals("GPL-3.0", porId("cloudstream").license) // declaraba Apache-2.0
        assertEquals("AGPL-3.0", porId("miru").license) // declaraba Apache-2.0
        assertEquals("GPL-2.0", porId("gallery_dl").license) // declaraba Public Domain / Unlicense
        for (id in listOf("cloudstream", "miru", "gallery_dl")) {
            val nota = porId(id).licenciaNota
            assertNotNull(id + " se corrigió en silencio", nota)
            assertTrue(id + " no dice qué declaraba antes", nota!!.contains("declaraba"))
        }
        // Las que ya estaban bien ni cambian ni se anotan.
        assertEquals("Apache-2.0", porId("mihon").license)
        assertTrue(porId("mihon").licenciaVerificada)
        assertNull(porId("mihon").licenciaNota)
        // Y las que sólo estaban en otro formato se normalizan al de la fuente.
        assertEquals("GPL-3.0", porId("kotatsu").license) // declaraba GPLv3
        assertTrue(porId("kotatsu").licenciaNota!!.contains("declaraba"))
    }

    @Test
    fun lo_que_no_se_pudo_comprobar_no_se_rellena() {
        // Sin archivo LICENSE, o con uno que SPDX no reconoce.
        for (id in listOf("dantotsu", "tachiyomi_az", "dartotsu")) {
            val a = porId(id)
            assertEquals(id, SIN_VERIFICAR, a.license)
            assertFalse(a.licenciaVerificada)
            assertTrue(id + " sin motivo", a.licenciaNota!!.isNotBlank())
        }
        // 12 enlaces son sitio de producto o documentación: no hay código fuente
        // que auditar, y eso se dice en vez de dar por buena la licencia.
        assertEquals(12, apps.count { it.estadoEnlace == EstadoEnlace.SIN_VERIFICAR })
        assertEquals(15, apps.count { it.estadoEnlace == EstadoEnlace.SIN_ENLACE })
        for (a in apps.filter { it.estadoEnlace == EstadoEnlace.SIN_ENLACE }) {
            assertEquals(a.id, TipoEnlace.SIN_ENLACE, a.tipoEnlace)
            assertEquals(a.id, SIN_VERIFICAR, a.license)
        }
    }

    @Test
    fun los_enlaces_muertos_estan_marcados() {
        assertEquals(
            setOf("aniyomi", "openlib", "tachiyomi_sy", "tachiyomi"),
            modulo.enlacesMuertos().map { it.id }.toSet(),
        )
        for (a in modulo.enlacesMuertos()) {
            assertEquals(EstadoEnlace.MUERTO_404, a.estadoEnlace)
            assertEquals(SIN_VERIFICAR, a.license)
            assertTrue(a.id + " no explica el 404", a.licenciaNota!!.contains("404"))
            assertEquals(404, modulo.fichaAuditada(a.id)?.http ?: -1)
        }
        // Un enlace muerto no demuestra que la app esté descontinuada: puede
        // haberse mudado. El estado que declaró la fuente se respeta.
        assertEquals(CatalogStatus.ACTIVE, porId("aniyomi").status)
        assertEquals(CatalogStatus.DISCONTINUED, porId("tachiyomi").status)
        // Kotatsu sí está archivado según la API, y eso se ve.
        assertTrue(porId("kotatsu").enlaceArchivado)
    }

    @Test
    fun las_tiendas_van_separadas_de_las_aplicaciones() {
        assertEquals(listOf("github_store"), modulo.tiendas().map { it.id })
        assertEquals(51, modulo.aplicaciones().size)
        assertFalse(modulo.aplicaciones().any { it.id == "github_store" })
        assertEquals(apps.size, modulo.aplicaciones().size + modulo.tiendas().size)
        assertEquals(TipoFicha.TIENDA, porId("github_store").tipoFicha)
        val motivo = modulo.motivoDeTienda("github_store")
        assertNotNull(motivo)
        assertTrue(motivo!!.isNotBlank())
        // Las 51 restantes son apps, no tiendas.
        assertTrue(modulo.aplicaciones().all { it.tipoFicha == TipoFicha.APP })
    }

    @Test
    fun los_directorios_curados_no_son_apps() {
        val dirs = modulo.directoriosCurados()
        assertEquals(listOf("everythingmoe", "miyomi", "wotaku"), dirs.map { it.id })
        for (d in dirs) {
            assertTrue(d.nombre + " sin HTTPS", d.url.startsWith("https://"))
            assertTrue(d.nombre + " sin cifras medidas", d.entradasMedidas > 0)
            assertTrue(d.nombre + " sin describir qué publica", d.quePublica.isNotBlank())
            assertTrue(d.nombre + " sin limitación declarada", d.limitacion.isNotBlank())
            assertFalse(d.id + " mezclado con las apps", apps.any { it.id == d.id })
            assertEquals(TipoEnlace.DIRECTORIO_CURADO, clasificarEnlace(d.url))
        }
        assertFalse("ninguna ficha del catálogo es un directorio", apps.any { it.tipoFicha == TipoFicha.DIRECTORIO_CURADO })
    }

    @Test
    fun healthcheck_declara_las_categorias_que_existen() {
        val detalles = modulo.healthCheck().details
        @Suppress("UNCHECKED_CAST")
        val categorias = detalles["categories"] as List<String>
        assertEquals(modulo.categoriasReales(), categorias)
        for (c in categorias) {
            assertTrue("categoría declarada sin ninguna ficha: $c", apps.any { it.category == c })
        }
        // La lista fija anterior declaraba categorías inventadas y omitía reales.
        assertFalse("sigue declarando audio, que no existe", categorias.contains("audio"))
        assertFalse("sigue declarando servers, que no existe", categorias.contains("servers"))
        assertTrue("falta server, que sí existe", categorias.contains("server"))
        assertTrue(categorias.contains("tracking"))
        assertTrue(categorias.contains("ereader"))
        assertEquals(52, detalles["apps_catalogued"])
        assertEquals(51, detalles["apps_excluding_stores"])
        assertEquals(1, detalles["stores_separated"])
        assertEquals(3, detalles["curated_directories"])
        assertEquals(25, detalles["links_audited"])
        assertEquals(18, detalles["licenses_verified"])
        assertEquals(34, detalles["licenses_unverified"])
        assertEquals(4, detalles["dead_links"])
        assertEquals(FECHA_AUDITORIA, detalles["audit_date"])
    }

    @Test
    fun las_plataformas_se_normalizan_sin_afirmar_soporte_que_no_hay() {
        assertEquals(setOf(Plataforma.ANDROID), plataformasDe("Android 8.0+, iOS coming soon"))
        assertEquals(setOf(Plataforma.SIN_DETERMINAR), plataformasDe("Cross-platform (Flutter)"))
        assertEquals(setOf(Plataforma.ANDROID, Plataforma.ESCRITORIO), plataformasDe("Android, PC"))
        assertEquals(setOf(Plataforma.ANDROID, Plataforma.ESCRITORIO), plataformasDe("Android, Desktop"))
        assertEquals(
            setOf(Plataforma.ANDROID, Plataforma.LECTOR_EBOOK),
            plataformasDe("Android, Kindle, Kobo, Cervantes, PocketBook"),
        )
        assertEquals(
            setOf(Plataforma.ANDROID, Plataforma.IOS, Plataforma.MACOS, Plataforma.LINUX, Plataforma.WINDOWS),
            plataformasDe("Android, iOS, macOS, Linux, Windows"),
        )
        assertEquals(setOf(Plataforma.SERVIDOR), plataformasDe("Server"))
        assertEquals(setOf(Plataforma.CLI), plataformasDe("CLI"))
        assertEquals(setOf(Plataforma.ANDROID, Plataforma.ANDROID_TV), plataformasDe("Android, Android TV"))
        // La ficha real que anunciaba iOS «próximamente» no puede decir que lo tiene.
        assertEquals(setOf(Plataforma.ANDROID), porId("ehviewer").plataformas)
        assertFalse(porId("ehviewer").plataformas.contains(Plataforma.IOS))
        // Un framework no es una plataforma: se marca, no se adivina.
        assertEquals(setOf(Plataforma.SIN_DETERMINAR), porId("jhentai").plataformas)
        for (a in apps) assertTrue(a.id + " se queda sin plataformas", a.plataformas.isNotEmpty())
        assertEquals(1, apps.count { Plataforma.SIN_DETERMINAR in it.plataformas })
    }

    @Test
    fun la_clasificacion_separa_fuente_tienda_directorio_documento_y_sitio() {
        assertEquals(TipoEnlace.REPOSITORIO_FUENTE, clasificarEnlace("https://github.com/KotatsuApp/Kotatsu"))
        assertEquals(TipoEnlace.REPOSITORIO_FUENTE, clasificarEnlace("https://codeberg.org/mikf/gallery-dl/"))
        assertEquals(TipoEnlace.TIENDA, clasificarEnlace("https://f-droid.org/packages/app.example/"))
        assertEquals(TipoEnlace.TIENDA, clasificarEnlace("https://apt.izzyondroid.org/es/app/"))
        // Una página de releases reparte binarios aunque viva en un forge.
        assertEquals(TipoEnlace.TIENDA, clasificarEnlace("https://github.com/x/y/releases"))
        assertEquals(TipoEnlace.TIENDA, clasificarEnlace("https://example.com/app.apk"))
        assertEquals(TipoEnlace.DIRECTORIO_CURADO, clasificarEnlace("https://wotaku.wiki/software"))
        assertEquals(TipoEnlace.DOCUMENTACION, clasificarEnlace("https://sugoi.gitbook.io/lanraragi/dev"))
        assertEquals(TipoEnlace.SITIO_PRODUCTO, clasificarEnlace("https://jellyfin.org/"))
        assertEquals(TipoEnlace.SITIO_PRODUCTO, clasificarEnlace("https://anikku-app.github.io/"))
        assertEquals(TipoEnlace.SIN_ENLACE, clasificarEnlace(null))
        assertEquals(TipoEnlace.SIN_ENLACE, clasificarEnlace("  "))
        assertEquals(TipoEnlace.SIN_ENLACE, clasificarEnlace("http://jellyfin.org/"))
        // Cada ficha del catálogo lleva su tipo puesto.
        assertEquals(25, apps.count { it.tipoEnlace == TipoEnlace.REPOSITORIO_FUENTE })
        assertEquals(1, apps.count { it.tipoEnlace == TipoEnlace.DOCUMENTACION })
        assertEquals(11, apps.count { it.tipoEnlace == TipoEnlace.SITIO_PRODUCTO })
        assertEquals(15, apps.count { it.tipoEnlace == TipoEnlace.SIN_ENLACE })
        assertEquals(0, apps.count { it.tipoEnlace == TipoEnlace.TIENDA })
    }

    @Test
    fun el_catalogo_no_descarga_ni_instala_nada() {
        assertEquals(DecisionApertura.ABRIR_NAVEGADOR, decisionDeApertura("https://github.com/x/y"))
        assertEquals(DecisionApertura.RECHAZADO_BINARIO_INSTALABLE, decisionDeApertura("https://example.com/app.apk"))
        assertEquals(DecisionApertura.RECHAZADO_BINARIO_INSTALABLE, decisionDeApertura("https://example.com/a.APK?raw=1"))
        assertEquals(DecisionApertura.RECHAZADO_BINARIO_INSTALABLE, decisionDeApertura("https://example.com/x.aab"))
        assertEquals(DecisionApertura.RECHAZADO_NO_HTTPS, decisionDeApertura("http://example.com/"))
        assertEquals(DecisionApertura.SIN_ENLACE, decisionDeApertura(null))
        assertEquals(DecisionApertura.SIN_ENLACE, decisionDeApertura("   "))
        // Ninguna ficha actual provocaría esa descarga.
        for (a in apps) {
            assertFalse(
                a.id + " apunta a un binario instalable",
                decisionDeApertura(a.repository) == DecisionApertura.RECHAZADO_BINARIO_INSTALABLE,
            )
        }
        // Y en el fuente no hay ningún camino de descarga o instalación.
        for (rel in listOf("modules/catalog/CatalogModule.kt", "modules/catalog/CatalogUIFragment.kt")) {
            val t = leer(rel)
            for (prohibido in listOf(
                "DownloadManager", "ACTION_INSTALL_PACKAGE", "PackageInstaller",
                "application/vnd.android.package-archive", "openFileOutput", ".enqueue(",
            )) {
                assertFalse("$rel contiene $prohibido", t.contains(prohibido))
            }
        }
        val ui = leer("modules/catalog/CatalogUIFragment.kt")
        assertTrue("la UI no abre con ACTION_VIEW", ui.contains("Intent.ACTION_VIEW"))
        assertTrue("la UI no pasa por decisionDeApertura", ui.contains("decisionDeApertura"))
    }

    @Test
    fun la_accion_se_llama_segun_lo_que_el_enlace_es() {
        assertEquals("Abrir repositorio de código", accionPara(TipoEnlace.REPOSITORIO_FUENTE))
        assertEquals("Sin enlace que abrir", accionPara(TipoEnlace.SIN_ENLACE))
        assertFalse(
            "un sitio del producto no es un repositorio",
            accionPara(TipoEnlace.SITIO_PRODUCTO).contains("repositorio"),
        )
        assertTrue(accionPara(TipoEnlace.SITIO_PRODUCTO).contains("sitio"))
        assertTrue(accionPara(TipoEnlace.TIENDA).contains("tienda"))
        for (t in TipoEnlace.values()) {
            assertTrue(t.name + " sin etiqueta", etiquetaDe(t).isNotBlank())
            assertTrue(t.name + " sin acción", accionPara(t).isNotBlank())
        }
        // 27 de las 52 fichas no llevan a un repositorio; antes todas decían
        // «Abrir repositorio de …».
        assertEquals(27, apps.count { it.tipoEnlace != TipoEnlace.REPOSITORIO_FUENTE })
    }

    @Test
    fun el_filtro_sigue_funcionando_sobre_las_apps_sin_las_tiendas() {
        val manga = filtrarCatalogo(modulo.aplicaciones(), "", "manga")
        assertTrue(manga.isNotEmpty())
        assertTrue(manga.all { it.category == "manga" })
        assertFalse(manga.any { it.tipoFicha == TipoFicha.TIENDA })
        assertTrue(filtrarCatalogo(modulo.tiendas(), "store", null).isNotEmpty())
        assertTrue(filtrarCatalogo(modulo.aplicaciones(), "noexisteestotexto", null).isEmpty())
    }
}
