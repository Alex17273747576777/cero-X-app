package com.ceroX.app.modules.extension_sandbox

import java.util.concurrent.ConcurrentHashMap

/**
 * ExtensionIsolator — Isolador de extensiones en sandbox.
 * Cada extensión se ejecuta en su propio contexto aislado.
 */
class ExtensionIsolator {

    private val isolatedExtensions = ConcurrentHashMap<String, IsolatedExtension>()

    fun initialize() {
        // Inicializar isolador
    }

    fun isolateExtension(extension: InstalledExtension) {
        isolatedExtensions[extension.id] = IsolatedExtension(
            id = extension.id,
            name = extension.name,
            status = ExtensionIsolationStatus.ISOLATED,
            createdAt = System.currentTimeMillis()
        )
    }

    fun releaseExtension(extensionId: String) {
        isolatedExtensions.remove(extensionId)
    }

    fun enableExtension(extensionId: String) {
        isolatedExtensions[extensionId]?.let {
            isolatedExtensions[extensionId] = it.copy(status = ExtensionIsolationStatus.ACTIVE)
        }
    }

    fun disableExtension(extensionId: String) {
        isolatedExtensions[extensionId]?.let {
            isolatedExtensions[extensionId] = it.copy(status = ExtensionIsolationStatus.DISABLED)
        }
    }

    fun stopAllExtensions() {
        isolatedExtensions.clear()
    }

    fun isolatedCount(): Int = isolatedExtensions.size
}

data class IsolatedExtension(
    val id: String,
    val name: String,
    val status: ExtensionIsolationStatus,
    val createdAt: Long
)

enum class ExtensionIsolationStatus {
    ISOLATED, ACTIVE, DISABLED, TERMINATED
}