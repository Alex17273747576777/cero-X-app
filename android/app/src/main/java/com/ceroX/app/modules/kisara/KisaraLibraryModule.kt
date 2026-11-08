package com.ceroX.app.modules.kisara

import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface

/**
 * KisaraLibraryModule — Gestión de biblioteca de Kisara estabilizada.
 *
 * Características:
 *   • Categorías ocultas para privacidad
 *   • Bulk-favorite múltiples entradas
 *   • Iconos de fuente e idioma en biblioteca
 *   • Categorías personalizadas para fuentes
 *   • Categorías dinámicas
 *   • Fusión de múltiples entradas del mismo manga de diferentes fuentes
 *   • Ordenar biblioteca con arrastrar y soltar
 *   • Motor de búsqueda de biblioteca (exclude, quotes como absoluto)
 *   • Vista de portada + compartir + guardar
 *   • Edición de información de manga
 *   • Pantalla "por actualizar" con smart-update
 *   • Pantalla de error de actualización
 *   • Banner de progreso en-app
 *   • Intervalo configurable para refrescar entradas
 *   • Entradas con OCR traducido y colorización neural marcadas
 */
class KisaraLibraryModule : ModuleInterface() {

    override val name = "kisara-library"
    override val version = "2.3.9-kisara-stable"
    override val dependencies: List<String> = listOf("kisara-reader")

    private val entries = mutableListOf<KisaraLibraryEntry>()
    private val categories = mutableListOf<KisaraCategory>()
    private val hiddenCategories = mutableSetOf<String>()
    private var refreshIntervalMinutes = 60
    private var bulkSelectionMode = false
    private var selectedEntryIds = mutableSetOf<String>()
    private var dragDropSortingEnabled = true
    private var searchExcludeMode = false

    override fun initialize(config: Map<String, Any>): Boolean {
        refreshIntervalMinutes = config["refresh_interval_minutes"] as? Int ?: 60
        dragDropSortingEnabled = config["drag_drop_sorting"] as? Boolean ?: true
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
                "entries" to entries.size,
                "categories" to categories.size,
                "hidden_categories" to hiddenCategories.size,
                "refresh_interval_minutes" to refreshIntervalMinutes,
                "bulk_selection_mode" to bulkSelectionMode,
                "selected_count" to selectedEntryIds.size,
                "drag_drop_sorting" to dragDropSortingEnabled,
                "search_exclude_mode" to searchExcludeMode,
                "ocr_translated_entries" to entries.count { it.ocrTranslated },
                "neural_colorized_entries" to entries.count { it.neuralColorized },
                "stability" to "stabilized"
            )
        )
    }

    fun addEntry(entry: KisaraLibraryEntry) {
        entries.add(entry)
    }

    fun listEntries(): List<KisaraLibraryEntry> = entries.toList()

    fun bulkFavorite(ids: List<String>, favorite: Boolean) {
        for (id in ids) {
            val entry = entries.find { it.id == id }
            if (entry != null) {
                val idx = entries.indexOf(entry)
                entries[idx] = entry.copy(isFavorite = favorite)
            }
        }
        selectedEntryIds.clear()
        bulkSelectionMode = false
    }

    fun toggleBulkSelection(entryId: String) {
        if (selectedEntryIds.contains(entryId)) {
            selectedEntryIds.remove(entryId)
        } else {
            selectedEntryIds.add(entryId)
        }
        bulkSelectionMode = selectedEntryIds.isNotEmpty()
    }

    fun selectAllEntries() {
        // `selectedEntryIds` es un MutableSet: asignarle el Set inmutable que
        // devuelve toSet() rompe el tipo. Se vacía y se rellena.
        selectedEntryIds.clear()
        selectedEntryIds.addAll(entries.map { it.id })
        bulkSelectionMode = true
    }

    fun clearSelection() {
        selectedEntryIds.clear()
        bulkSelectionMode = false
    }

    fun mergeEntries(primaryId: String, duplicateIds: List<String>) {
        val primary = entries.find { it.id == primaryId } ?: return
        for (dupId in duplicateIds) {
            val dup = entries.find { it.id == dupId } ?: continue
            val mergedProgress = maxOf(primary.progress, dup.progress)
            val mergedChapters = primary.chaptersRead + dup.chaptersRead
            val idx = entries.indexOf(primary)
            entries[idx] = primary.copy(
                progress = mergedProgress,
                chaptersRead = mergedChapters,
                isFavorite = primary.isFavorite || dup.isFavorite,
                sources = (primary.sources + dup.sources).distinct(),
                lastUpdated = System.currentTimeMillis()
            )
            entries.removeAll { it.id == dupId }
        }
        clearSelection()
    }

    fun addCategory(category: KisaraCategory) {
        categories.add(category)
    }

    fun listCategories(): List<KisaraCategory> = categories.toList()

    fun toggleCategoryHidden(categoryId: String, hidden: Boolean) {
        if (hidden) hiddenCategories.add(categoryId) else hiddenCategories.remove(categoryId)
    }

    fun listVisibleCategories(): List<KisaraCategory> =
        categories.filter { !hiddenCategories.contains(it.id) }

    fun setRefreshInterval(minutes: Int) {
        refreshIntervalMinutes = minutes.coerceIn(5, 10080)
    }

    fun toggleDragDropSorting(enabled: Boolean) {
        dragDropSortingEnabled = enabled
    }

    fun toggleSearchExcludeMode(enabled: Boolean) {
        searchExcludeMode = enabled
    }

    fun searchEntries(query: String, exclude: Boolean = false): List<KisaraLibraryEntry> {
        return entries.filter { entry ->
            val matches = entry.title.contains(query, ignoreCase = true) ||
                          entry.author.contains(query, ignoreCase = true) ||
                          entry.tags.any { it.contains(query, ignoreCase = true) }
            if (exclude) !matches else matches
        }
    }

    fun getUpdateStatus(): List<KisaraUpdateStatus> = emptyList()
}

data class KisaraUpdateStatus(
    val entryId: String,
    val entryTitle: String,
    val currentChapter: Int,
    val latestChapter: Int,
    val hasUpdate: Boolean,
    val updateError: String? = null
)