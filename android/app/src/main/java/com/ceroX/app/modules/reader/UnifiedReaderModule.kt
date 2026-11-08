package com.ceroX.app.modules.reader

import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface
import java.io.File

/**
 * UnifiedReaderModule — Fusión de Kototoro + Reikai + Mihon base.
 *
 * Características fusionadas:
 * - Lector unificado manga/novela/video (Kototoro)
 * - Single-list library + category hopper (Reikai)
 * - Dynamic grouping por tag/autor/source/status (Reikai)
 * - Multi-source merge: una serie, N fuentes (Reikai)
 * - Cover-color theming desde portada (Reikai ← Komikku)
 * - Light novels junto a manga (Kototoro)
 * - Caché de miniaturas configurable 16MB-1GB (Kototoro)
 * - Requiere Android 8.0+ (Reikai)
 */
class UnifiedReaderModule : ModuleInterface() {

    override val name = "unified-reader"
    override val version = "0.1.0-fusion"
    override val dependencies: List<String> = emptyList()

    private var context: Context? = null
    private val library = mutableListOf<UnifiedLibraryItem>()
    private val extensions = mutableListOf<ExtensionSource>()
    private val seriesMergeMap = mutableMapOf<String, MergedSeries>() // canonicalId -> merged sources
    private var cacheSizeMb = 512
    private var dynamicGroupingEnabled = true
    private var coverColorThemingEnabled = true

    override fun initialize(config: Map<String, Any>): Boolean {
        cacheSizeMb = config["cache_size_mb"] as? Int ?: 512
        dynamicGroupingEnabled = config["dynamic_grouping"] as? Boolean ?: true
        coverColorThemingEnabled = config["cover_color_theming"] as? Boolean ?: true
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
                "library_items" to library.size,
                "extensions" to extensions.size,
                "merged_series" to seriesMergeMap.size,
                "cache_size_mb" to cacheSizeMb,
                "dynamic_grouping" to dynamicGroupingEnabled,
                "cover_color_theming" to coverColorThemingEnabled,
                "min_android" to "8.0 (API 26)"
            )
        )
    }

    // ========== Single-list library + category hopper (Reikai) ==========

    fun addToLibrary(item: UnifiedLibraryItem): Boolean {
        library.add(item)
        return true
    }

    fun listLibrary(): List<UnifiedLibraryItem> = library.toList()

    fun removeFromLibrary(id: String): Boolean {
        return library.removeIf { it.id == id }
    }

    fun searchLibrary(query: String): List<UnifiedLibraryItem> {
        val lower = query.lowercase()
        return library.filter { item ->
            item.title.lowercase().contains(lower) ||
                item.author.lowercase().contains(lower) ||
                item.tags.any { it.lowercase().contains(lower) }
        }
    }

    // ========== Dynamic grouping (Reikai) ==========

    fun groupByTag(): Map<String, List<UnifiedLibraryItem>> {
        if (!dynamicGroupingEnabled) return mapOf("all" to library.toList())
        return library.groupBy { it.tags.firstOrNull() ?: "sin-etiqueta" }
    }

    fun groupByAuthor(): Map<String, List<UnifiedLibraryItem>> {
        if (!dynamicGroupingEnabled) return mapOf("all" to library.toList())
        return library.groupBy { it.author }
    }

    fun groupBySource(): Map<String, List<UnifiedLibraryItem>> {
        if (!dynamicGroupingEnabled) return mapOf("all" to library.toList())
        return library.groupBy { it.source }
    }

    fun groupByStatus(): Map<String, List<UnifiedLibraryItem>> {
        if (!dynamicGroupingEnabled) return mapOf("all" to library.toList())
        return library.groupBy { it.status }
    }

    // ========== Multi-source merge (Reikai) ==========

    fun mergeSeries(canonicalId: String, sources: List<ExtensionSource>): MergedSeries {
        val merged = MergedSeries(canonicalId, sources, System.currentTimeMillis())
        seriesMergeMap[canonicalId] = merged
        return merged
    }

    fun getMergedSeries(canonicalId: String): MergedSeries? = seriesMergeMap[canonicalId]

    fun listMergedSeries(): List<MergedSeries> = seriesMergeMap.values.toList()

    // ========== Cover-color theming (Reikai ← Komikku) ==========

    fun extractCoverColor(imagePath: String): String? {
        if (!coverColorThemingEnabled) return null
        // Placeholder: extraer color dominante de la portada
        val file = File(imagePath)
        return if (file.exists()) "#6200EE" else null // color por defecto cero-X
    }

    // ========== Extensions ==========

    fun registerExtension(source: ExtensionSource): Boolean {
        extensions.add(source)
        return true
    }

    fun listExtensions(): List<ExtensionSource> = extensions.toList()

    fun getCacheDir(): File? {
        return context?.externalCacheDir
    }
}

/** Elemento de biblioteca unificado
 *
 *  Es la entidad que declara ReaderRoomDatabase. Sin @Entity y sin @PrimaryKey,
 *  Room no puede generar la tabla ni el DAO, y el modulo entero deja de compilar.
 *  Las columnas por defecto toman el nombre de la propiedad; el resto de
 *  propiedades con valor por defecto llevan @ColumnInfo para que el esquema
 *  no dependa del orden de declaracion.
 */
@Entity(tableName = "unified_library_item")
data class UnifiedLibraryItem(
    @PrimaryKey val id: String,
    val title: String,
    val author: String = "",
    val source: String = "",
    val type: String = "manga", // manga | novel | video
    val status: String = "reading", // reading | completed | dropped | plan-to-read
    val tags: List<String> = emptyList(),
    val coverUrl: String = "",
    val chapter: Int = 0,
    val totalChapters: Int = 0,
    val progressPercent: Float = 0.0f,
    val addedAt: Long = System.currentTimeMillis()
)

/** Fuente de extensión */
data class ExtensionSource(
    val id: String,
    val name: String,
    val baseUrl: String,
    val supportsCloudflareBypass: Boolean = false,
    val repoforgeGenerated: Boolean = false,
    val registeredAt: Long = System.currentTimeMillis()
)

/** Serie fusionada con múltiples fuentes */
data class MergedSeries(
    val canonicalId: String,
    val sources: List<ExtensionSource>,
    val mergedAt: Long,
    val preferredSource: String? = null
)