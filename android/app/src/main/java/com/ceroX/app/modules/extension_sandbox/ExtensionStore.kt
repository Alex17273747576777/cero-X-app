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

    /**
     * El catalogo empieza vacio, a proposito.
     *
     * Aqui habia tres extensiones inventadas —Madara Manga, WordPress Anime y
     * Novel Updates— con `downloadUrl` apuntando a un dominio que no existe y
     * `sha256` sin valor real. Listarlas era prometer descargas que no se podian
     * hacer ni verificar, y una huella falsa en un instalador de extensiones es
     * justo lo que no se puede fingir.
     *
     * El catalogo de verdad se llena desde un repositorio de extensiones externo
     * que trae su index y su huella de firma; ese repositorio vive fuera del APK.
     */
    private fun registerDefaultExtensions() {
        // Nada que registrar: el catalogo lo llena el repositorio externo.
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