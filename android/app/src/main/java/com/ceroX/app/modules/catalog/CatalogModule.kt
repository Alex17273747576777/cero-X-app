package com.ceroX.app.modules.catalog

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * CatalogModule — Catálogo integrado de cero-X con todas las apps descubiertas
 * en Miyomi, Wotaku.wiki y EverythingMoe.
 *
 * Referencia todas las apps de código abierto analizadas con sus mejores funciones,
 * para que cero-X pueda integrarlas gradualmente.
 */
class CatalogModule : ModuleInterface() {

    override val name = "app-catalog"
    override val version = "1.0.0-beta"
    override val dependencies = emptyList()

    private val apps = mutableListOf<CatalogApp>()

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
            mapOf(
                "apps_catalogued" to apps.size,
                "sources" to listOf("miyomi.app", "wotaku.wiki", "everythingmoe.com"),
                "categories" to listOf("manga", "anime", "novel", "video", "audio", "tools", "servers")
            )
        )
    }

    private fun registerAllApps() {
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

    fun searchApps(query: String): List<CatalogApp> {
        val q = query.lowercase()
        return apps.filter {
            it.name.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
        }
    }

    fun getPrimaryReader(): CatalogApp? {
        return apps.firstOrNull { it.isPrimaryReader }
    }
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
    val description: String = ""
)

enum class CatalogStatus {
    ACTIVE, DISCONTINUED, ARCHIVED, BETA
}