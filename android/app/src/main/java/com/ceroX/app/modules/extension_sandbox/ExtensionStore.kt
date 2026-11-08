package com.ceroX.app.modules.extension_sandbox

/**
 * ExtensionStore — Tienda de extensiones integrada en cero-X.
 * Permite descubrir, instalar y actualizar extensiones propias y de terceros.
 */
class ExtensionStore {

    private val availableExtensions = mutableListOf<AvailableExtension>()
    private val installedExtensions = mutableListOf<InstalledExtension>()

    fun initialize() {
        registerDefaultExtensions()
    }

    private fun registerDefaultExtensions() {
        availableExtensions.add(AvailableExtension(
            id = "ext_madara_manga",
            name = "Madara Manga",
            type = "manga",
            version = "1.0.0",
            author = "Community",
            description = "Extensión para sitios tipo Madara",
            downloadUrl = "https://extensions.cero-x.app/madara.apk",
            sha256 = "placeholder",
            sandboxCompatible = true
        ))
        availableExtensions.add(AvailableExtension(
            id = "ext_wordpress_anime",
            name = "WordPress Anime",
            type = "anime",
            version = "1.0.0",
            author = "Community",
            description = "Extensión para sitios anime tipo WordPress",
            downloadUrl = "https://extensions.cero-x.app/wordpress_anime.apk",
            sha256 = "placeholder",
            sandboxCompatible = true
        ))
        availableExtensions.add(AvailableExtension(
            id = "ext_novel_updates",
            name = "Novel Updates",
            type = "novel",
            version = "1.0.0",
            author = "Community",
            description = "Extensión para Novel Updates",
            downloadUrl = "https://extensions.cero-x.app/novel_updates.apk",
            sha256 = "placeholder",
            sandboxCompatible = true
        ))
    }

    fun listAvailable(): List<AvailableExtension> = availableExtensions.toList()

    fun search(query: String): List<AvailableExtension> {
        val q = query.lowercase()
        return availableExtensions.filter {
            it.name.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.type.lowercase().contains(q)
        }
    }

    fun getExtension(extensionId: String): AvailableExtension? {
        return availableExtensions.firstOrNull { it.id == extensionId }
    }

    fun markInstalled(extension: InstalledExtension) {
        installedExtensions.add(extension)
    }

    fun listInstalled(): List<InstalledExtension> = installedExtensions.toList()
}

data class AvailableExtension(
    val id: String,
    val name: String,
    val type: String,
    val version: String,
    val author: String,
    val description: String,
    val downloadUrl: String,
    val sha256: String,
    val sandboxCompatible: Boolean
)