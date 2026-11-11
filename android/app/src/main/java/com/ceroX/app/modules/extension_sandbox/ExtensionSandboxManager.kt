package com.ceroX.app.modules.extension_sandbox

import android.content.Context

/**
 * ExtensionSandboxManager — Gestor del SanTBox aislado.
 * Basado en Miyorare Private / Sandbox — Internal.
 */
class ExtensionSandboxManager {

    private var isActive = false
    private var sandboxDir: java.io.File? = null

    /**
     * Foto del estado, para que quien orqueste el módulo pueda LEER sin poder
     * escribir. Antes el módulo leía `isActive` directamente y no podía porque es
     * private; la tentación era hacerla pública y entonces cualquiera podría
     * activar el sandbox sin inicializarlo.
     */
    data class SandboxState(
        val active: Boolean,
        val directorio: String?,
        val existeEnDisco: Boolean,
    )

    fun state(): SandboxState = SandboxState(
        active = isActive,
        directorio = sandboxDir?.absolutePath,
        // No basta con isActive: si el directorio no se pudo crear, el sandbox
        // está "activo" en memoria y roto en disco. Esa diferencia importa.
        existeEnDisco = sandboxDir?.isDirectory == true,
    )

    fun initialize() {
        // Crear directorio de sandbox aislado
        sandboxDir = java.io.File("/data/data/com.ceroX.app/extension_sandbox")
        sandboxDir?.mkdirs()
        isActive = true
    }

    fun isSandboxActive(): Boolean = isActive

    fun getSandboxDir(): java.io.File? = sandboxDir

    fun createExtensionIsolationDir(extensionId: String): java.io.File? {
        val dir = java.io.File(sandboxDir, extensionId)
        dir.mkdirs()
        return dir
    }

    fun removeExtensionIsolationDir(extensionId: String) {
        java.io.File(sandboxDir, extensionId).deleteRecursively()
    }

    /**
     * Extensiones con su directorio de aislamiento creado de verdad.
     *
     * Devolvía 0 fijo, que es el mismo numero que da un sandbox vacio: no habia
     * forma de distinguir "no hay ninguna" de "no se ha mirado". Se mira.
     */
    fun getActiveExtensionsCount(): Int =
        sandboxDir?.listFiles { f -> f.isDirectory }?.size ?: 0
}