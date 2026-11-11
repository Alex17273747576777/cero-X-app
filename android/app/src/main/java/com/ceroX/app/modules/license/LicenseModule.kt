package com.ceroX.app.modules.license

import android.content.Context
import com.ceroX.app.core.engine.ModuleHealth
import com.ceroX.app.core.engine.ModuleInterface
import java.io.File

/**
 * LicenseModule — conecta el comprobador con la realidad del paquete instalado.
 *
 * El comprobador puro ([LicenseCompliance]) sabe aplicar las reglas. Este módulo
 * le dice qué hay de verdad: si el APK trae el `NOTICE` en sus assets, si trae la
 * licencia, y qué modelos van dentro.
 *
 * La distinción importante: **un NOTICE en el repositorio no cumple Apache-2.0 en
 * la APK**. La obligación es sobre lo que se distribuye. Si alguien compila sin
 * empaquetar el NOTICE, el usuario final no lo tiene, y el cumplimiento se rompe
 * aunque el archivo exista en git. Por eso se comprueba el asset, no el repo.
 */
class LicenseModule(private val context: Context) : ModuleInterface() {

    override val name = "license"
    override val version = "1.0.0-beta"
    override val dependencies: List<String> = emptyList()

    private var compliance: LicenseCompliance? = null

    /**
     * Los modelos que viajan en el APK. Desde la 1.0.1 la lista está VACÍA y eso
     * es un hecho medido, no una intención: el APK publicado
     * (v1.0.1-beta-arranque-honesto, 31.577.211 bytes) tiene 884 entradas y cero
     * archivos `.onnx`. Antes esta lista declaraba cinco modelos que el build ya
     * no empaqueta, o sea que la app informaba de una obligación sobre unos bytes
     * que no existen.
     *
     * El día que un build vuelva a llevar modelos, se declaran aquí con su
     * licencia y su crédito visible. `anime4x` no puede volver sin permiso del
     * autor: su repositorio de origen no declara licencia (ver NOTICE).
     */
    private val modelos: List<LicenseCompliance.BundledAsset> = emptyList()

    override fun initialize(config: Map<String, Any>): Boolean {
        compliance = build()
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
        val c = compliance ?: build()
        return ModuleHealth(
            name, version,
            when {
                !c.isCompliant() -> "degraded"   // no se llama "healthy" si falta algo
                _started -> "healthy"
                else -> "stopped"
            },
            mapOf(
                "license" to c.license.spdx,
                "compliant" to c.isCompliant(),
                "pending" to c.pending().map { it.id }.joinToString(",").ifEmpty { "ninguna" },
                "summary" to c.summary(),
            ),
        )
    }

    fun current(): LicenseCompliance = compliance ?: build()

    /** Lo que hay que enseñar al usuario. Ninguna obligación se oculta. */
    fun obligations(): List<LicenseObligation> = current().obligations()

    private fun build(): LicenseCompliance {
        val assets = context.assets
        val tieneNotice = runCatching { assets.open("NOTICE").use { it.read() > 0 } }.getOrDefault(false)
        val tieneLicense = runCatching { assets.open("LICENSE").use { it.read() > 0 } }.getOrDefault(false)

        return LicenseCompliance(
            // GPL-3.0 desde el 2026-11-11; el linaje Apache-2.0 conserva sus
            // avisos en NOTICE. Ver LicenseId y docs/LICENCIA.md.
            license = LicenseId.GPL3,
            hasLicenseFile = tieneLicense,
            hasNoticeFile = tieneNotice,
            // El repo es público: con la GPL-3.0 esto ya no es una hipótesis sino
            // una obligación, y por eso se comprueba (obligation `source-available`).
            sourcePublished = true,
            bundledAssets = modelos,
        )
    }

    /** Qué assets de licencia faltan dentro del APK. Lista vacía = todo bien. */
    fun missingAssets(): List<String> {
        val out = mutableListOf<String>()
        listOf("NOTICE", "LICENSE").forEach { n ->
            val ok = runCatching { context.assets.open(n).use { true } }.getOrDefault(false)
            if (!ok) out += n
        }
        return out
    }

    /** ¿Está el NOTICE empaquetado? Se usa desde las pruebas de empaquetado. */
    fun noticeBundled(): Boolean = missingAssets().none { it == "NOTICE" }

    companion object {
        /**
         * Comprueba el cumplimiento sobre una ruta de assets en disco, sin
         * necesitar un Context. Sirve para verificarlo en el CI sobre el APK
         * descomprimido.
         */
        fun checkDir(assetsDir: File, modelos: List<LicenseCompliance.BundledAsset>): LicenseCompliance =
            LicenseCompliance(
                license = LicenseId.GPL3,
                hasLicenseFile = File(assetsDir, "LICENSE").exists(),
                hasNoticeFile = File(assetsDir, "NOTICE").exists(),
                sourcePublished = true,
                bundledAssets = modelos,
            )
    }
}
