package com.ceroX.app.modules.catalog

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * CatalogModule — Catálogo integrado de cero-X con las apps descubiertas en
 * Miyomi, Wotaku.wiki y EverythingMoe.
 *
 * Referencia las apps de código abierto analizadas con sus mejores funciones,
 * para que cero-X pueda integrarlas gradualmente.
 *
 * Desde 1.1.0 la licencia y el enlace de cada ficha no son «lo que alguien
 * escribió a mano»: están auditados contra la API del propio alojamiento (tabla
 * [auditoria], evidencia en docs/CATALOGO-AUDITORIA.md). Lo que no se pudo
 * comprobar queda como [SIN_VERIFICAR] en vez de rellenarse con una suposición,
 * y las tiendas y los directorios curados van separados de las aplicaciones
 * porque no son lo mismo.
 */
class CatalogModule : ModuleInterface() {

    override val name = "app-catalog"
    override val version = "1.1.0-catalogo-auditado"
    override val dependencies: List<String> = emptyList()

    private val apps = mutableListOf<CatalogApp>()

    /**
     * Auditoría real de enlaces y licencias, hecha el [FECHA_AUDITORIA].
     *
     * Cada repositorio se consultó en la API de su alojamiento; `endpoint` es la
     * URL exacta para repetirlo, y CatalogoLicenciasRedRealTest lo hace cuando
     * se activa con CEROX_LIVE_CATALOGO=1.
     *
     * DECLARADA ANTES DEL BLOQUE init A PROPÓSITO: registerAllApps() corre en el
     * constructor y aplica esta tabla, así que si estuviera más abajo estaría
     * vacía en ese momento y las 52 fichas saldrían sin auditar sin que fallara
     * nada. Es la trampa del orden de inicialización en Kotlin.
     */
    private val auditoria: Map<String, FichaAuditada> = mapOf(
        "ani_cli" to FichaAuditada(
            url = "https://github.com/pystardust/ani-cli",
            http = 200,
            spdxApi = "GPL-3.0",
            archivado = false,
            identificadorRetirado = true,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/pystardust/ani-cli",
            endpoint = "https://api.github.com/repos/pystardust/ani-cli",
        ),
        "animetail" to FichaAuditada(
            url = "https://github.com/Animetailapp/Animetail",
            http = 200,
            spdxApi = "Apache-2.0",
            archivado = false,
            identificadorRetirado = false,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/Animetailapp/Animetail",
            endpoint = "https://api.github.com/repos/Animetailapp/Animetail",
        ),
        "animiru" to FichaAuditada(
            url = "https://github.com/quickdesh/Animiru",
            http = 200,
            spdxApi = "GPL-3.0",
            archivado = false,
            identificadorRetirado = true,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/quickdesh/Animiru",
            endpoint = "https://api.github.com/repos/quickdesh/Animiru",
        ),
        "aniyomi" to FichaAuditada(
            url = "https://github.com/AniyomiDev/Aniyomi",
            http = 404,
            spdxApi = null,
            archivado = false,
            identificadorRetirado = false,
            motivo = "El repositorio devuelve HTTP 404 en la API y en la web: el enlace de la ficha está muerto y la licencia no se puede comprobar",
            fuente = "api.github.com/repos/AniyomiDev/Aniyomi",
            endpoint = "https://api.github.com/repos/AniyomiDev/Aniyomi",
        ),
        "cloudstream" to FichaAuditada(
            url = "https://github.com/recloudstream/cloudstream",
            http = 200,
            spdxApi = "GPL-3.0",
            archivado = false,
            identificadorRetirado = true,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/recloudstream/cloudstream",
            endpoint = "https://api.github.com/repos/recloudstream/cloudstream",
        ),
        "curd" to FichaAuditada(
            url = "https://github.com/Wraient/curd",
            http = 200,
            spdxApi = "GPL-3.0",
            archivado = false,
            identificadorRetirado = true,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/Wraient/curd",
            endpoint = "https://api.github.com/repos/Wraient/curd",
        ),
        "dantotsu" to FichaAuditada(
            url = "https://github.com/itsmechinmoy/dantotsu-updater",
            http = 200,
            spdxApi = null,
            archivado = false,
            identificadorRetirado = false,
            motivo = "El repositorio existe pero no tiene archivo LICENSE; además es «dantotsu-updater», un actualizador, no la app en sí",
            fuente = "api.github.com/repos/itsmechinmoy/dantotsu-updater",
            endpoint = "https://api.github.com/repos/itsmechinmoy/dantotsu-updater",
        ),
        "dartotsu" to FichaAuditada(
            url = "https://github.com/aayush2622/Dartotsu",
            http = 200,
            spdxApi = "NOASSERTION",
            archivado = false,
            identificadorRetirado = false,
            motivo = "El repositorio existe pero su archivo LICENSE no es reconocible por SPDX (la API responde NOASSERTION)",
            fuente = "api.github.com/repos/aayush2622/Dartotsu",
            endpoint = "https://api.github.com/repos/aayush2622/Dartotsu",
        ),
        "gallery_dl" to FichaAuditada(
            url = "https://codeberg.org/mikf/gallery-dl/",
            http = 200,
            spdxApi = "GPL-2.0",
            archivado = false,
            identificadorRetirado = true,
            motivo = "La API de Codeberg no detecta la licencia del espejo; se leyó allí el archivo LICENSE (18.092 bytes, GNU GPL versión 2, sin cláusula «or any later version») y se contrastó con el mismo proyecto en GitHub, que responde GPL-2.0",
            fuente = "codeberg.org/mikf/gallery-dl/raw/branch/master/LICENSE + api.github.com/repos/mikf/gallery-dl",
            endpoint = "https://api.github.com/repos/mikf/gallery-dl",
        ),
        "houbi" to FichaAuditada(
            url = "https://github.com/PineappleTwilight/houri",
            http = 200,
            spdxApi = "Apache-2.0",
            archivado = false,
            identificadorRetirado = false,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/PineappleTwilight/houri",
            endpoint = "https://api.github.com/repos/PineappleTwilight/houri",
        ),
        "kisara" to FichaAuditada(
            url = "https://github.com/ifritraen/kisara",
            http = 200,
            spdxApi = "Apache-2.0",
            archivado = false,
            identificadorRetirado = false,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/ifritraen/kisara",
            endpoint = "https://api.github.com/repos/ifritraen/kisara",
        ),
        "komikku" to FichaAuditada(
            url = "https://github.com/komikku-app/komikku",
            http = 200,
            spdxApi = "Apache-2.0",
            archivado = false,
            identificadorRetirado = false,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/komikku-app/komikku",
            endpoint = "https://api.github.com/repos/komikku-app/komikku",
        ),
        "kotatsu" to FichaAuditada(
            url = "https://github.com/KotatsuApp/Kotatsu",
            http = 200,
            spdxApi = "GPL-3.0",
            archivado = true,
            identificadorRetirado = true,
            motivo = "Licencia devuelta por la API del alojamiento; el repositorio figura ARCHIVADO (solo lectura)",
            fuente = "api.github.com/repos/KotatsuApp/Kotatsu",
            endpoint = "https://api.github.com/repos/KotatsuApp/Kotatsu",
        ),
        "mangayomi" to FichaAuditada(
            url = "https://github.com/kodjodevf/mangayomi",
            http = 200,
            spdxApi = "Apache-2.0",
            archivado = false,
            identificadorRetirado = false,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/kodjodevf/mangayomi",
            endpoint = "https://api.github.com/repos/kodjodevf/mangayomi",
        ),
        "mihon" to FichaAuditada(
            url = "https://github.com/mihonapp/mihon",
            http = 200,
            spdxApi = "Apache-2.0",
            archivado = false,
            identificadorRetirado = false,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/mihonapp/mihon",
            endpoint = "https://api.github.com/repos/mihonapp/mihon",
        ),
        "miru" to FichaAuditada(
            url = "https://github.com/miru-project/miru-app",
            http = 200,
            spdxApi = "AGPL-3.0",
            archivado = false,
            identificadorRetirado = true,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/miru-project/miru-app",
            endpoint = "https://api.github.com/repos/miru-project/miru-app",
        ),
        "openlib" to FichaAuditada(
            url = "https://github.com/Openlib-app/Openlib",
            http = 404,
            spdxApi = null,
            archivado = false,
            identificadorRetirado = false,
            motivo = "El repositorio devuelve HTTP 404 en la API y en la web: el enlace de la ficha está muerto y la licencia no se puede comprobar",
            fuente = "api.github.com/repos/Openlib-app/Openlib",
            endpoint = "https://api.github.com/repos/Openlib-app/Openlib",
        ),
        "reikai" to FichaAuditada(
            url = "https://github.com/unseensnick/Reikai",
            http = 200,
            spdxApi = "Apache-2.0",
            archivado = false,
            identificadorRetirado = false,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/unseensnick/Reikai",
            endpoint = "https://api.github.com/repos/unseensnick/Reikai",
        ),
        "suwayomi" to FichaAuditada(
            url = "https://github.com/Suwayomi/Suwayomi-Server",
            http = 200,
            spdxApi = "MPL-2.0",
            archivado = false,
            identificadorRetirado = false,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/Suwayomi/Suwayomi-Server",
            endpoint = "https://api.github.com/repos/Suwayomi/Suwayomi-Server",
        ),
        "tachiyomi" to FichaAuditada(
            url = "https://github.com/tachiyomiorg/tachiyomi",
            http = 404,
            spdxApi = null,
            archivado = false,
            identificadorRetirado = false,
            motivo = "El repositorio devuelve HTTP 404 en la API y en la web: el enlace de la ficha está muerto y la licencia no se puede comprobar",
            fuente = "api.github.com/repos/tachiyomiorg/tachiyomi",
            endpoint = "https://api.github.com/repos/tachiyomiorg/tachiyomi",
        ),
        "tachiyomi_az" to FichaAuditada(
            url = "https://github.com/az4521/TachiyomiAZ",
            http = 200,
            spdxApi = "NOASSERTION",
            archivado = false,
            identificadorRetirado = false,
            motivo = "El repositorio existe pero su archivo LICENSE no es reconocible por SPDX (la API responde NOASSERTION)",
            fuente = "api.github.com/repos/az4521/TachiyomiAZ",
            endpoint = "https://api.github.com/repos/az4521/TachiyomiAZ",
        ),
        "tachiyomi_sy" to FichaAuditada(
            url = "https://github.com/riderkick/TachiyomiSY",
            http = 404,
            spdxApi = null,
            archivado = false,
            identificadorRetirado = false,
            motivo = "El repositorio devuelve HTTP 404 en la API y en la web: el enlace de la ficha está muerto y la licencia no se puede comprobar",
            fuente = "api.github.com/repos/riderkick/TachiyomiSY",
            endpoint = "https://api.github.com/repos/riderkick/TachiyomiSY",
        ),
        "toru" to FichaAuditada(
            url = "https://github.com/sweetbbak/toru",
            http = 200,
            spdxApi = "MIT",
            archivado = false,
            identificadorRetirado = false,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/sweetbbak/toru",
            endpoint = "https://api.github.com/repos/sweetbbak/toru",
        ),
        "unyo" to FichaAuditada(
            url = "https://github.com/K3vinb5/unyo-app",
            http = 200,
            spdxApi = "BSD-3-Clause",
            archivado = false,
            identificadorRetirado = false,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/K3vinb5/unyo-app",
            endpoint = "https://api.github.com/repos/K3vinb5/unyo-app",
        ),
        "yokai" to FichaAuditada(
            url = "https://github.com/null2264/yokai",
            http = 200,
            spdxApi = "Apache-2.0",
            archivado = false,
            identificadorRetirado = false,
            motivo = "Licencia devuelta por la API del alojamiento",
            fuente = "api.github.com/repos/null2264/yokai",
            endpoint = "https://api.github.com/repos/null2264/yokai",
        ),
    )

    /**
     * Fichas que son una tienda o un instalador, no una app del ecosistema. Se
     * declaran aquí con su motivo porque ninguna URL lo revela: «Github Store»
     * ni siquiera trae enlace, y deducirlo del nombre sería justo el falso
     * positivo que este catálogo no se permite.
     */
    private val tiendasDeclaradas: Map<String, String> = mapOf(
        "github_store" to "Se presenta como «app store for GitHub releases»: instala otras apps, no es una app del ecosistema",
    )

    /**
     * Los tres directorios curados de los que salió el catálogo. No son apps y
     * no se mezclan con ellas: publican listas de recursos, no software. Las
     * cifras son las medidas el 2026-11-08 (docs/CATALOGO-DE-RECURSOS.md) y las
     * tres URLs se comprobaron vivas (HTTP 200) el [FECHA_AUDITORIA].
     */
    private val directorios: List<DirectorioCurado> = listOf(
        DirectorioCurado(
            id = "everythingmoe",
            nombre = "EverythingMoe",
            url = "https://everythingmoe.com/",
            quePublica = "25 secciones del ecosistema con ficha propia, icono y etiquetas",
            entradasMedidas = 798,
            limitacion = "69 entradas que el sitio oculta con JavaScript y no manda en el HTML; están registradas con sus números exactos en vez de rellenadas",
        ),
        DirectorioCurado(
            id = "miyomi",
            nombre = "Miyomi",
            url = "https://miyomi.app/",
            quePublica = "API JSON con 36 campos por app, más 47 extensiones y 24 guías",
            entradasMedidas = 149,
            limitacion = "89 fichas con la categoría vacía o nula en la propia API; no se rellena",
        ),
        DirectorioCurado(
            id = "wotaku",
            nombre = "Wotaku",
            url = "https://wotaku.wiki/",
            quePublica = "Tablas de software, sitios web y miscelánea",
            entradasMedidas = 156,
            limitacion = "Mezcla apps, servidores y enlaces sueltos en la misma tabla: sirve para documentos, no para datos",
        ),
    )

    /**
     * Añade una app al catálogo.
     *
     * Esta función faltaba: se llamaba 52 veces en registerAllApps() y no estaba
     * definida en ninguna parte, así que el módulo entero de catálogo no compilaba.
     * Deduplica por id para que registrar dos veces la misma app no la duplique,
     * que es el fallo silencioso clásico de un catálogo que se rellena a mano.
     */
    private fun addApp(app: CatalogApp) {
        if (apps.none { it.id == app.id }) {
            apps.add(auditar(app))
        }
    }

    /**
     * Convierte lo que la ficha declaraba en lo que se pudo comprobar.
     *
     * La licencia escrita a mano se trata como una *declaración de la fuente*,
     * no como un hecho: sólo se conserva si la auditoría la confirmó. Si la
     * contradice, gana la auditoría y la nota deja constancia de lo que se decía
     * antes, porque reescribir en silencio borraría la pista del error. Si no
     * hay forma de comprobarla, queda [SIN_VERIFICAR].
     */
    private fun auditar(app: CatalogApp): CatalogApp {
        val declarada = app.license.trim()
        val vaga = declarada.isBlank() ||
            declarada.equals("Open Source", ignoreCase = true) ||
            declarada.equals("Unknown", ignoreCase = true)
        val ficha = auditoria[app.id]
        val tipo = clasificarEnlace(app.repository)
        val tipoFicha = if (tiendasDeclaradas.containsKey(app.id)) TipoFicha.TIENDA else TipoFicha.APP
        val estado = when {
            app.repository.isNullOrBlank() -> EstadoEnlace.SIN_ENLACE
            ficha == null -> EstadoEnlace.SIN_VERIFICAR
            ficha.enlaceVivo -> EstadoEnlace.VERIFICADO_200
            else -> EstadoEnlace.MUERTO_404
        }
        val comprobada = ficha?.licencia
        return if (comprobada != null) {
            app.copy(
                tipoFicha = tipoFicha,
                tipoEnlace = tipo,
                license = comprobada,
                licenciaVerificada = true,
                licenciaFuente = ficha.fuente,
                licenciaNota = when {
                    ficha.identificadorRetirado && vaga ->
                        "Verificada en ${ficha.fuente}. Identificador retirado por SPDX: no distingue «only» de «or-later», se conserva literal"
                    ficha.identificadorRetirado ->
                        "La fuente declaraba «$declarada». Identificador retirado por SPDX: no distingue «only» de «or-later», se conserva literal"
                    vaga -> "Verificada en ${ficha.fuente}; la fuente no declaraba una licencia concreta"
                    declarada != comprobada -> "La fuente declaraba «$declarada»; la auditoría encontró $comprobada"
                    else -> null
                },
                estadoEnlace = estado,
                enlaceArchivado = ficha.archivado,
            )
        } else {
            val motivo = when {
                ficha != null -> ficha.motivo
                app.repository.isNullOrBlank() ->
                    "La ficha no trae enlace: no hay repositorio ni sitio donde comprobar la licencia"
                else -> "El enlace es ${etiquetaDe(tipo)}, no código fuente, así que no hay nada que auditar"
            }
            app.copy(
                tipoFicha = tipoFicha,
                tipoEnlace = tipo,
                license = SIN_VERIFICAR,
                licenciaVerificada = false,
                licenciaFuente = ficha?.fuente,
                licenciaNota = if (vaga) motivo else "$motivo. La fuente declaraba «$declarada»",
                estadoEnlace = estado,
                enlaceArchivado = ficha?.archivado == true,
            )
        }
    }

    init {
        // El catálogo está desde la construcción: es una lista fija de
        // aplicaciones, no trabajo pesado, y la pantalla lo enseña en cuanto se
        // abre aunque initialize() siga corriendo en su hilo.
        registerAllApps()
    }

    override fun initialize(config: Map<String, Any>): Boolean {
        registerAllApps()
        _initialized = true
        return true
    }

    override fun start(): Boolean {
        if (!_initialized) return false
        _started = true
        return true
    }

    override fun stop(): Boolean {
        _started = false
        return true
    }

    override fun healthCheck(): ModuleHealth {
        return ModuleHealth(
            name, version,
            if (_started) "healthy" else "stopped",
            // Las categorías se derivan de las fichas. La lista fija anterior
            // declaraba «audio» y «servers», que no existen en los datos, y no
            // declaraba «server», «tracking» ni «ereader», que sí existen.
            mapOf(
                "apps_catalogued" to apps.size,
                "apps_excluding_stores" to apps.count { it.tipoFicha == TipoFicha.APP },
                "stores_separated" to apps.count { it.tipoFicha == TipoFicha.TIENDA },
                "curated_directories" to directorios.size,
                "sources" to directorios.map { it.url.removePrefix("https://").removeSuffix("/") },
                "categories" to categoriasReales(),
                "links_audited" to auditoria.size,
                "licenses_verified" to apps.count { it.licenciaVerificada },
                "licenses_unverified" to apps.count { !it.licenciaVerificada },
                "dead_links" to apps.count { it.estadoEnlace == EstadoEnlace.MUERTO_404 },
                "audit_date" to FECHA_AUDITORIA,
                "downloads" to "nunca: los enlaces del catálogo sólo abren el navegador"
            )
        )
    }

    private fun registerAllApps() {
        // Idempotente: se llama en init y otra vez en initialize().
        if (apps.isNotEmpty()) return
        // === APPS DE MIYOMI ===
        addApp(CatalogApp(
            id = "mihon",
            name = "Mihon",
            repository = "https://github.com/mihonapp/mihon",
            license = "Apache-2.0",
            platform = "Android",
            category = "manga",
            bestFeatures = listOf(
                "Extension system (Keiyoushi, MHRepo, Nyora)",
                "Library management with tracking",
                "Local reading and backup",
                "17.2M downloads",
                "Tachiyomi lineage"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "komikku",
            name = "Komikku",
            repository = "https://github.com/komikku-app/komikku",
            license = "Apache-2.0",
            platform = "Android",
            category = "manga",
            bestFeatures = listOf(
                "Fork of Mihon",
                "Modern UI with themes",
                "Extension compatible with TachiyomiSY, J2K, Neko"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "houbi",
            name = "Houbi (PineappleTwilight/houri)",
            repository = "https://github.com/PineappleTwilight/houri",
            license = "Apache-2.0",
            platform = "Android",
            category = "manga",
            bestFeatures = listOf(
                "WebGPU viewer for manga and webtoons",
                "Dark-mode filter with AMOLED subtoggle",
                "Upscaler with PNG cache",
                "Universal tracker (MangaBaka, MU, Hikka, Kitsu)",
                "Achievements framework",
                "ViewerRegistry + PageLoaderFactory polymorphism",
                "AppDispatchers concurrency foundation",
                "Single SQLite connection (Metro delegation)",
                "ZERO-X BASE PRINCIPAL"
            ),
            status = CatalogStatus.ACTIVE,
            isPrimaryReader = true
        ))

        addApp(CatalogApp(
            id = "kisara",
            name = "Kisara (ifritraen/kisara)",
            repository = "https://github.com/ifritraen/kisara",
            license = "Apache-2.0",
            platform = "Android",
            category = "manga",
            bestFeatures = listOf(
                "Fork of Komikku",
                "ONNX Runtime Mobile (FP16/FP32)",
                "NNAPI Hardware Acceleration",
                "KisaraColorizer - neural manga colorization",
                "Jetpack Compose + Material3",
                "Injekt DI + Kotlin Coroutines"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "reikai",
            name = "Reikai (unseensnick/Reikai)",
            repository = "https://github.com/unseensnick/Reikai",
            license = "Apache-2.0",
            platform = "Android 8+",
            category = "manga",
            bestFeatures = listOf(
                "Built on Mihon",
                "Single-list library + category hopper",
                "Dynamic grouping by tag/author/source/status",
                "Cover-color theming (from Komikku)",
                "Multi-source grouping and merge",
                "Light novels alongside manga"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "mangayomi",
            name = "Mangayomi",
            repository = "https://github.com/kodjodevf/mangayomi",
            license = "Apache-2.0",
            platform = "Android, iOS, macOS, Linux, Windows",
            category = "manga",
            bestFeatures = listOf(
                "Manga, novels, and anime in one app",
                "Flutter cross-platform",
                "Available on all major platforms",
                "302.7k downloads"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "aniyomi",
            name = "Aniyomi",
            repository = "https://github.com/AniyomiDev/Aniyomi",
            license = "Apache-2.0",
            platform = "Android 8.0+",
            category = "anime",
            bestFeatures = listOf(
                "Manga and anime in one app",
                "Extension-based content sources",
                "Tracking via MyAnimeList and AniList",
                "mpvKt video player",
                "Subtitle language codes"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "anikku",
            name = "Anikku",
            repository = "https://anikku-app.github.io/",
            license = "Apache-2.0",
            platform = "Android",
            category = "anime",
            bestFeatures = listOf(
                "Free and open source anime watcher",
                "Modern UI"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "cloudstream",
            name = "Cloudstream",
            repository = "https://github.com/recloudstream/cloudstream",
            license = "Apache-2.0",
            platform = "Android, Android TV",
            category = "video",
            bestFeatures = listOf(
                "Multiplatform video streaming",
                "Extension-based sources",
                "Android TV support"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "anymex",
            name = "AnymeX",
            repository = "https://anymex.vercel.app/",
            license = "Open Source",
            platform = "Android",
            category = "tracking",
            bestFeatures = listOf(
                "Tracking Multi Service (AL, MAL, SIMKL)",
                "225.5k downloads"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "dodoStream",
            name = "DodoStream",
            license = "Open Source",
            platform = "Android",
            category = "video",
            bestFeatures = listOf(
                "Privacy-focused media hub",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "dantotsu",
            name = "Dantotsu",
            repository = "https://github.com/itsmechinmoy/dantotsu-updater",
            license = "Open Source",
            platform = "Android",
            category = "anime",
            bestFeatures = listOf(
                "Anime watcher",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "hayase",
            name = "Hayase",
            repository = "https://hayase.watch/",
            license = "Open Source",
            platform = "Android, PC",
            category = "anime",
            bestFeatures = listOf(
                "Anime watcher for Android and PC",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "nuvio",
            name = "Nuvio",
            repository = "https://nuvio.tv/",
            license = "Open Source",
            platform = "Android, PC, iOS",
            category = "anime",
            bestFeatures = listOf(
                "Anime watcher multiplatform",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "seanime",
            name = "Seanime",
            repository = "https://seanime.app/",
            license = "Open Source",
            platform = "PC",
            category = "anime",
            bestFeatures = listOf(
                "Anime server + PC client",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "miru",
            name = "Miru App",
            repository = "https://github.com/miru-project/miru-app",
            license = "Apache-2.0",
            platform = "Android, Windows, Web",
            category = "video",
            bestFeatures = listOf(
                "Multi-functional: video, comics, novels",
                "Extended source system",
                "Cross-platform"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "shosetsu",
            name = "Shosetsu",
            license = "Apache-2.0",
            platform = "Android",
            category = "novel",
            bestFeatures = listOf(
                "Free and open source novel reader",
                "Android"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "ireader",
            name = "IReader",
            license = "Apache-2.0",
            platform = "Android, Desktop",
            category = "novel",
            bestFeatures = listOf(
                "Light novel reader",
                "Android and Desktop"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "openlib",
            name = "Openlib",
            repository = "https://github.com/Openlib-app/Openlib",
            license = "Apache-2.0",
            platform = "Android",
            category = "novel",
            bestFeatures = listOf(
                "Open source app to download and read books from shadow library",
                "Anna's Archive integration",
                "61.1k downloads"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "koreader",
            name = "KOReader",
            repository = "https://koreader.rocks/",
            license = "AGPLv3",
            platform = "Android, Kindle, Kobo, Cervantes, PocketBook",
            category = "ereader",
            bestFeatures = listOf(
                "Document viewer for e-ink readers",
                "PDF, EPUB, FB2 support",
                "Multiple platforms"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "tachiyomi_az",
            name = "Tachiyomi AZ",
            repository = "https://github.com/az4521/TachiyomiAZ",
            license = "Apache-2.0",
            platform = "Android",
            category = "manga",
            bestFeatures = listOf(
                "Fork of Mihon",
                "Email and free doujin reader",
                "280.9k downloads"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "tachiyomi_sy",
            name = "Tachiyomi SY",
            repository = "https://github.com/riderkick/TachiyomiSY",
            license = "Apache-2.0",
            platform = "Android",
            category = "manga",
            bestFeatures = listOf(
                "Fork of Mihon",
                "Enhanced usability and features",
                "1.4M downloads"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "taison",
            name = "Taison",
            license = "Apache-2.0",
            platform = "Android",
            category = "manga",
            bestFeatures = listOf(
                "Fork of Mihon",
                "Polished quality-of-life upgrades",
                "More control and oversight"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "tsundoku",
            name = "Tsundoku",
            license = "Apache-2.0",
            platform = "Android",
            category = "novel",
            bestFeatures = listOf(
                "Novel and manga reader",
                "Fork of Mihon"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "yokai",
            name = "Yokai",
            repository = "https://github.com/null2264/yokai",
            license = "Apache-2.0",
            platform = "Android",
            category = "manga",
            bestFeatures = listOf(
                "Fork of Mihon",
                "545.6k downloads",
                "Free and open source manga reader"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "futon",
            name = "Futon",
            license = "Apache-2.0",
            platform = "Android",
            category = "manga",
            bestFeatures = listOf(
                "Fork of Kotatsu",
                "Built-in online content sources",
                "11.5k downloads"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "kotatsu_redo",
            name = "Kotatsu Redo",
            license = "Apache-2.0",
            platform = "Android",
            category = "manga",
            bestFeatures = listOf(
                "Fork of Kotatsu",
                "Manga reader for Android"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "kototoro",
            name = "Kototoro",
            license = "Apache-2.0",
            platform = "Android",
            category = "manga",
            bestFeatures = listOf(
                "Fork of Kotatsu",
                "Manga, Novel, and Video reader",
                "37.6k downloads"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "lightnovelreader",
            name = "LightNovelReader",
            license = "Apache-2.0",
            platform = "Android",
            category = "novel",
            bestFeatures = listOf(
                "Minimal open-source light novel reader",
                "Multiple sources, offline reading, custom themes, EPUB export"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "neojqn",
            name = "NeoQN",
            license = "Apache-2.0",
            platform = "Android",
            category = "novel",
            bestFeatures = listOf(
                "Fork of QuickNovel",
                "Android app for downloading and reading novels"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "noveldokusha",
            name = "NovelDokusha",
            license = "Apache-2.0",
            platform = "Android",
            category = "novel",
            bestFeatures = listOf(
                "Webnovel reader with translation",
                "Offline reading features"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "kotinovelreader",
            name = "Kototoro (Novel support)",
            note = "Kototoro includes novel support alongside manga and video",
            license = "Apache-2.0",
            platform = "Android",
            category = "novel",
            bestFeatures = listOf(
                "Novel support in unified reader",
                "Part of Kototoro"
            ),
            status = CatalogStatus.ACTIVE
        ))

        // === APPS CERRADAS / DISCONTINUADAS PERO CON BUEN CÓDIGO ===
        addApp(CatalogApp(
            id = "kotatsu",
            name = "Kotatsu (Discontinued)",
            repository = "https://github.com/KotatsuApp/Kotatsu",
            license = "GPLv3",
            platform = "Android",
            category = "manga",
            bestFeatures = listOf(
                "Manga reader with built-in online content sources",
                "Base for many forks (Kotatsu Redo, Futon, Kototoro)",
                "Shut down but code available for reference"
            ),
            status = CatalogStatus.DISCONTINUED,
            note = "Kotatsu shut down support, but its code is the foundation of Kotatsu Redo, Futon, and Kototoro"
        ))

        addApp(CatalogApp(
            id = "tachiyomi",
            name = "Tachiyomi (Original, archived)",
            repository = "https://github.com/tachiyomiorg/tachiyomi",
            license = "Apache-2.0",
            platform = "Android",
            category = "manga",
            bestFeatures = listOf(
                "Original manga reader",
                "Hundreds of sources",
                "Tracker support (MAL, AniList, Kitsu, Shikimori, Bangumi)",
                "Local backups",
                "Multiple reading modes and viewers",
                "Custom color filters"
            ),
            status = CatalogStatus.DISCONTINUED,
            note = "Original Tachiyomi archived, succeeded by Mihon"
        ))

        // === APPS DE WOTAKU.WIKI NO AÚN INTEGRADAS ===
        addApp(CatalogApp(
            id = "animiru",
            name = "Animiru",
            repository = "https://github.com/quickdesh/Animiru",
            license = "Open Source",
            platform = "Android",
            category = "anime",
            bestFeatures = listOf(
                "Android anime watcher",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "animetail",
            name = "Animetail",
            repository = "https://github.com/Animetailapp/Animetail",
            license = "Open Source",
            platform = "Android",
            category = "anime",
            bestFeatures = listOf(
                "Android anime watcher",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "dartotsu",
            name = "Dartotsu",
            repository = "https://github.com/aayush2622/Dartotsu",
            license = "Open Source",
            platform = "Android, PC",
            category = "anime",
            bestFeatures = listOf(
                "Android and PC anime watcher",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "nyantv",
            name = "NyanTV",
            repository = "https://nyantv.vercel.app/",
            license = "Open Source",
            platform = "Android",
            category = "anime",
            bestFeatures = listOf(
                "Android anime watcher",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "unyo",
            name = "Unyo",
            repository = "https://github.com/K3vinb5/unyo-app",
            license = "Open Source",
            platform = "PC",
            category = "anime",
            bestFeatures = listOf(
                "PC anime client",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "toru",
            name = "Toru",
            repository = "https://github.com/sweetbbak/toru",
            license = "Open Source",
            platform = "PC",
            category = "anime",
            bestFeatures = listOf(
                "PC anime client",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "zenshin",
            name = "Zenshin",
            repository = "https://hitarth-gg.github.io/zenshin-website/",
            license = "Open Source",
            platform = "PC",
            category = "anime",
            bestFeatures = listOf(
                "PC anime client",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "curd",
            name = "Curd",
            repository = "https://github.com/Wraient/curd",
            license = "Open Source",
            platform = "PC",
            category = "anime",
            bestFeatures = listOf(
                "PC anime client",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "ani_cli",
            name = "ani-cli",
            repository = "https://github.com/pystardust/ani-cli",
            license = "Open Source",
            platform = "PC",
            category = "anime",
            bestFeatures = listOf(
                "CLI anime client",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        // === SERVIDORES ===
        addApp(CatalogApp(
            id = "jellyfin",
            name = "Jellyfin",
            repository = "https://jellyfin.org/",
            license = "GPLv2",
            platform = "Server",
            category = "server",
            bestFeatures = listOf(
                "Media server for anime",
                "Open source",
                "Self-hosted"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "komga",
            name = "Komga",
            repository = "https://komga.org/",
            license = "Open Source",
            platform = "Server",
            category = "server",
            bestFeatures = listOf(
                "Manga server",
                "Open source",
                "Self-hosted"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "kavita",
            name = "Kavita",
            repository = "https://www.kavitareader.com/",
            license = "Open Source",
            platform = "Server",
            category = "server",
            bestFeatures = listOf(
                "Manga and novel server",
                "Open source",
                "Self-hosted"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "lanraragi",
            name = "LANraragi",
            repository = "https://sugoi.gitbook.io/lanraragi/dev",
            license = "Open Source",
            platform = "Server",
            category = "server",
            bestFeatures = listOf(
                "Manga server",
                "Open source",
                "Self-hosted"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "suwayomi",
            name = "Suwayomi",
            repository = "https://github.com/Suwayomi/Suwayomi-Server",
            license = "Open Source",
            platform = "Server",
            category = "server",
            bestFeatures = listOf(
                "Tachiyomi extension server",
                "Open source",
                "Self-hosted"
            ),
            status = CatalogStatus.ACTIVE
        ))

        // === TOOLS ===
        addApp(CatalogApp(
            id = "gallery_dl",
            name = "gallery-dl",
            repository = "https://codeberg.org/mikf/gallery-dl/",
            license = "Public Domain / Unlicense",
            platform = "CLI",
            category = "tools",
            bestFeatures = listOf(
                "Command-line manga downloader",
                "Supports hundreds of sites",
                "Open source"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "github_store",
            name = "Github Store",
            license = "Open Source",
            platform = "Android, Desktop",
            category = "tools",
            bestFeatures = listOf(
                "Free, open-source app store for GitHub releases",
                "Browse, discover, and install apps with one click",
                "Powered by Kotlin and Compose Multiplatform"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "ehviewer",
            name = "EhViewer",
            license = "GPLv3",
            platform = "Android 8.0+, iOS coming soon",
            category = "manga",
            bestFeatures = listOf(
                "Huge manga catalog",
                "Offline downloads",
                "Zoom-perfect reading",
                "Custom themes",
                "Privacy-first, no accounts, no tracking",
                "1.14.6 (Dec 2025)"
            ),
            status = CatalogStatus.ACTIVE
        ))

        addApp(CatalogApp(
            id = "jhentai",
            name = "JHenTai",
            license = "Open Source",
            platform = "Cross-platform (Flutter)",
            category = "manga",
            bestFeatures = listOf(
                "Cross-platform e-hentai & ExHentai client",
                "Full control over data caching",
                "Polished mobile and desktop experience"
            ),
            status = CatalogStatus.ACTIVE
        ))
    }

    fun listApps(): List<CatalogApp> = apps.toList()

    fun listAppsByCategory(category: String): List<CatalogApp> {
        return apps.filter { it.category == category }
    }

    fun listActiveApps(): List<CatalogApp> {
        return apps.filter { it.status == CatalogStatus.ACTIVE }
    }

    fun listDiscontinuedApps(): List<CatalogApp> {
        return apps.filter { it.status == CatalogStatus.DISCONTINUED }
    }

    fun searchApps(query: String): List<CatalogApp> = filtrarCatalogo(apps, query, null)

    fun getPrimaryReader(): CatalogApp? {
        return apps.firstOrNull { it.isPrimaryReader }
    }

    /** Apps del ecosistema, sin tiendas ni instaladores. */
    fun aplicaciones(): List<CatalogApp> = apps.filter { it.tipoFicha == TipoFicha.APP }

    /** Tiendas e instaladores: se listan aparte porque no son apps del ecosistema. */
    fun tiendas(): List<CatalogApp> = apps.filter { it.tipoFicha == TipoFicha.TIENDA }

    /** Por qué una ficha está clasificada como tienda. */
    fun motivoDeTienda(id: String): String? = tiendasDeclaradas[id]

    /** Los directorios curados de los que salió el catálogo. */
    fun directoriosCurados(): List<DirectorioCurado> = directorios

    /** La auditoría entera, con el endpoint para repetirla. */
    fun fichasAuditadas(): Map<String, FichaAuditada> = auditoria

    fun fichaAuditada(id: String): FichaAuditada? = auditoria[id]

    fun licenciasVerificadas(): List<CatalogApp> = apps.filter { it.licenciaVerificada }

    fun licenciasSinVerificar(): List<CatalogApp> = apps.filter { !it.licenciaVerificada }

    fun enlacesMuertos(): List<CatalogApp> = apps.filter { it.estadoEnlace == EstadoEnlace.MUERTO_404 }

    fun appsPorTipoDeEnlace(): Map<TipoEnlace, Int> = apps.groupingBy { it.tipoEnlace }.eachCount()

    /** Categorías que existen de verdad en las fichas, derivadas de los datos. */
    fun categoriasReales(): List<String> = apps.map { it.category }.distinct().sorted()
}

data class CatalogApp(
    val id: String,
    val name: String,
    val repository: String? = null,
    val license: String = "Unknown",
    val platform: String = "Unknown",
    val category: String = "Unknown",
    val bestFeatures: List<String> = emptyList(),
    val status: CatalogStatus = CatalogStatus.ACTIVE,
    val isPrimaryReader: Boolean = false,
    val note: String? = null,
    val description: String = "",
    // Los campos siguientes los rellena CatalogModule.auditar() a partir de la
    // auditoría real; no se escriben a mano en las fichas.
    val tipoFicha: TipoFicha = TipoFicha.APP,
    val tipoEnlace: TipoEnlace = TipoEnlace.SIN_ENLACE,
    val licenciaVerificada: Boolean = false,
    val licenciaFuente: String? = null,
    val licenciaNota: String? = null,
    val estadoEnlace: EstadoEnlace = EstadoEnlace.SIN_VERIFICAR,
    val enlaceArchivado: Boolean = false
) {
    /** Plataformas normalizadas desde el texto libre que declaró la fuente. */
    val plataformas: Set<Plataforma> get() = plataformasDe(platform)
}

enum class CatalogStatus {
    ACTIVE, DISCONTINUED, ARCHIVED, BETA
}

/** Filtro puro compartido por la pantalla y las pruebas; categoría y texto se acumulan. */
fun filtrarCatalogo(
    apps: List<CatalogApp>,
    consulta: String,
    categoria: String?,
): List<CatalogApp> {
    val q = consulta.trim().lowercase()
    return apps.filter { app ->
        (categoria.isNullOrBlank() || app.category == categoria) &&
            (q.isEmpty() || listOf(
                app.name, app.description, app.category, app.platform,
                app.repository.orEmpty(), app.bestFeatures.joinToString(" "),
            ).any { it.contains(q, ignoreCase = true) })
    }
}
// ─── Auditoría de enlaces, licencias y plataformas ──────────────────────────
// Funciones puras y sin dependencias de Android: las pruebas las comprueban
// directamente, y clasificar un enlace no depende de lo que diga el nombre de
// la ficha.

/** Valor explícito para lo que no se pudo comprobar. Nunca se rellena con una suposición. */
const val SIN_VERIFICAR = "SIN_VERIFICAR"

/** Fecha de la auditoría real contra api.github.com, codeberg.org y los archivos LICENSE. */
const val FECHA_AUDITORIA = "2026-11-13"

/** Qué es realmente el enlace de una ficha, mirando el host y la ruta. */
enum class TipoEnlace {
    REPOSITORIO_FUENTE, TIENDA, DIRECTORIO_CURADO, DOCUMENTACION, SITIO_PRODUCTO, SIN_ENLACE
}

/** Una tienda no es una app del ecosistema, y un directorio curado tampoco. */
enum class TipoFicha { APP, TIENDA, DIRECTORIO_CURADO }

/** Estado del enlace observado en la auditoría; no es una opinión sobre la app. */
enum class EstadoEnlace { VERIFICADO_200, MUERTO_404, SIN_VERIFICAR, SIN_ENLACE }

/**
 * Vocabulario cerrado de plataformas. Lo que no encaja se marca
 * [Plataforma.SIN_DETERMINAR] en vez de adivinarse.
 */
enum class Plataforma {
    ANDROID, ANDROID_TV, IOS, MACOS, LINUX, WINDOWS, ESCRITORIO, WEB, SERVIDOR, CLI, LECTOR_EBOOK, SIN_DETERMINAR
}

/**
 * Lo que la app hace con un enlace del catálogo. Abrir el navegador es lo único
 * permitido: desde el catálogo no se descarga ni se instala nada.
 */
enum class DecisionApertura {
    ABRIR_NAVEGADOR, RECHAZADO_NO_HTTPS, RECHAZADO_BINARIO_INSTALABLE, SIN_ENLACE
}

/** Resultado de consultar un repositorio en la API de su alojamiento. */
data class FichaAuditada(
    /** Enlace que trae la ficha. */
    val url: String,
    /** Código HTTP observado en la auditoría; 404 es enlace muerto. */
    val http: Int?,
    /** Identificador literal que devolvió la API, o null si no reconoció ninguno. */
    val spdxApi: String?,
    /** El alojamiento lo marca como archivado (solo lectura). */
    val archivado: Boolean,
    /**
     * La API devolvió un identificador que SPDX retiró (GPL-3.0, AGPL-3.0…). Se
     * conserva literal: traducirlo a «-only» o a «-or-later» sería elegir un
     * alcance legal que la API no afirma.
     */
    val identificadorRetirado: Boolean,
    /** Por qué no hay licencia comprobada, cuando no la hay. */
    val motivo: String,
    /** Descripción legible de dónde salió el dato. */
    val fuente: String,
    /** URL exacta para repetir la comprobación; null si no la hay. */
    val endpoint: String?
) {
    /** Licencia comprobada, o null si el alojamiento no permitió comprobarla. */
    val licencia: String? get() = spdxApi?.takeIf { it.isNotBlank() && it != "NOASSERTION" }
    val enlaceVivo: Boolean get() = http == 200
}

/** Un directorio curado publica listas de recursos; no es software instalable. */
data class DirectorioCurado(
    val id: String,
    val nombre: String,
    val url: String,
    val quePublica: String,
    val entradasMedidas: Int,
    val limitacion: String,
    val fechaMedicion: String = "2026-11-08"
)

private val HOSTS_FUENTE = setOf("github.com", "codeberg.org", "gitlab.com", "sr.ht", "bitbucket.org")
private val HOSTS_TIENDA = setOf(
    "f-droid.org", "apt.izzyondroid.org", "play.google.com", "apps.apple.com", "aur.archlinux.org"
)
private val HOSTS_DIRECTORIO = setOf("everythingmoe.com", "miyomi.app", "wotaku.wiki")
private val EXTENSIONES_INSTALABLES = listOf(".apk", ".aab", ".xapk", ".apkm")

/**
 * Clasifica un enlace por lo que es, no por el nombre de la ficha.
 *
 * Una página de releases reparte binarios aunque viva en github.com, así que
 * cuenta como tienda; `*.github.io` es un sitio, no un repositorio.
 */
fun clasificarEnlace(url: String?): TipoEnlace {
    val limpio = url?.trim().orEmpty()
    if (limpio.isEmpty() || !limpio.startsWith("https://")) return TipoEnlace.SIN_ENLACE
    val sinEsquema = limpio.removePrefix("https://")
    val host = sinEsquema.substringBefore('/').lowercase().removePrefix("www.")
    val ruta = ("/" + sinEsquema.substringAfter('/', "")).lowercase()
        .substringBefore('?').substringBefore('#')
    return when {
        EXTENSIONES_INSTALABLES.any { ruta.endsWith(it) } -> TipoEnlace.TIENDA
        host in HOSTS_TIENDA -> TipoEnlace.TIENDA
        host in HOSTS_FUENTE && (ruta.contains("/releases") || ruta.contains("/tags")) -> TipoEnlace.TIENDA
        host in HOSTS_DIRECTORIO -> TipoEnlace.DIRECTORIO_CURADO
        host in HOSTS_FUENTE -> TipoEnlace.REPOSITORIO_FUENTE
        host.endsWith(".gitbook.io") || ruta.startsWith("/docs") || ruta.contains("/wiki") -> TipoEnlace.DOCUMENTACION
        else -> TipoEnlace.SITIO_PRODUCTO
    }
}

/**
 * Decide qué hacer con un enlace antes de tocarlo.
 *
 * Abrir un `.apk` en el navegador provoca la descarga de un binario instalable:
 * justo la descarga implícita que el catálogo no debe causar. Se rechaza aquí,
 * en una función pura que las pruebas pueden comprobar.
 */
fun decisionDeApertura(url: String?): DecisionApertura {
    val limpio = url?.trim().orEmpty()
    if (limpio.isEmpty()) return DecisionApertura.SIN_ENLACE
    if (!limpio.startsWith("https://")) return DecisionApertura.RECHAZADO_NO_HTTPS
    val ruta = ("/" + limpio.removePrefix("https://").substringAfter('/', ""))
        .lowercase().substringBefore('?').substringBefore('#')
    if (EXTENSIONES_INSTALABLES.any { ruta.endsWith(it) }) {
        return DecisionApertura.RECHAZADO_BINARIO_INSTALABLE
    }
    return DecisionApertura.ABRIR_NAVEGADOR
}

/**
 * Normaliza el texto libre de plataformas a un vocabulario cerrado.
 *
 * Dos reglas evitan afirmar más de lo que la fuente dijo: un anuncio
 * («iOS coming soon») no es soporte, y un framework («Cross-platform (Flutter)»)
 * no es una plataforma, así que acaba en [Plataforma.SIN_DETERMINAR].
 */
fun plataformasDe(texto: String): Set<Plataforma> {
    val segmentos = texto.split(',', '/', ';')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .filter { !it.contains("coming soon") && !it.contains("próxim") && !it.contains("proxim") }
    val resultado = mutableSetOf<Plataforma>()
    for (s in segmentos) {
        when {
            "android tv" in s -> resultado += Plataforma.ANDROID_TV
            "android" in s -> resultado += Plataforma.ANDROID
            "ios" in s || "iphone" in s || "ipad" in s -> resultado += Plataforma.IOS
            "macos" in s || "mac os" in s || "osx" in s -> resultado += Plataforma.MACOS
            "linux" in s -> resultado += Plataforma.LINUX
            "windows" in s -> resultado += Plataforma.WINDOWS
            "desktop" in s || "escritorio" in s || "pc" in s -> resultado += Plataforma.ESCRITORIO
            "web" in s -> resultado += Plataforma.WEB
            "server" in s || "servidor" in s -> resultado += Plataforma.SERVIDOR
            "cli" in s || "terminal" in s || "consola" in s -> resultado += Plataforma.CLI
            "kindle" in s || "kobo" in s || "pocketbook" in s || "cervantes" in s || "e-ink" in s ->
                resultado += Plataforma.LECTOR_EBOOK
        }
    }
    if (resultado.isEmpty()) resultado += Plataforma.SIN_DETERMINAR
    return resultado
}

/** Cómo se nombra el enlace en la interfaz. */
fun etiquetaDe(tipo: TipoEnlace): String = when (tipo) {
    TipoEnlace.REPOSITORIO_FUENTE -> "repositorio de código"
    TipoEnlace.TIENDA -> "tienda o instalador"
    TipoEnlace.DIRECTORIO_CURADO -> "directorio curado"
    TipoEnlace.DOCUMENTACION -> "documentación"
    TipoEnlace.SITIO_PRODUCTO -> "sitio del producto"
    TipoEnlace.SIN_ENLACE -> "sin enlace"
}

/**
 * Texto de la acción según lo que el enlace es de verdad.
 *
 * Antes la interfaz decía «Abrir repositorio» en las 52 fichas, y 27 no llevan a
 * ningún repositorio: 12 son sitios de producto o documentación y 15 no tienen
 * enlace.
 */
fun accionPara(tipo: TipoEnlace): String = when (tipo) {
    TipoEnlace.REPOSITORIO_FUENTE -> "Abrir repositorio de código"
    TipoEnlace.TIENDA -> "Abrir la tienda en el navegador"
    TipoEnlace.DIRECTORIO_CURADO -> "Abrir el directorio curado"
    TipoEnlace.DOCUMENTACION -> "Abrir la documentación"
    TipoEnlace.SITIO_PRODUCTO -> "Abrir el sitio del producto"
    TipoEnlace.SIN_ENLACE -> "Sin enlace que abrir"
}
