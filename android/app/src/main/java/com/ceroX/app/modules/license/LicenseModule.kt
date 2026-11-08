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

    /** Los cinco modelos que viajan en el APK, con su licencia real. */
    private val modelos = listOf(
        LicenseCompliance.BundledAsset("encoder_fp16", "apache-2.0", attributionInApp = true),
        LicenseCompliance.BundledAsset("colorize", "apache-2.0", attributionInApp = true),
        LicenseCompliance.BundledAsset("decoder_model_quantized", "apache-2.0", attributionInApp = true),
        LicenseCompliance.BundledAsset("esrgan_x4", "BSD-3-Clause", attributionInApp = true),
        // Sin licencia declarada en origen. Se marca igual y se acredita.
        LicenseCompliance.BundledAsset("anime4x", "NO DECLARADA", attributionInApp = true),
    )

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
            // Apache-2.0, medido contra el linaje real. Ver LicenseId.
            license = LicenseId.APACHE2,
            hasLicenseFile = tieneLicense,
            hasNoticeFile = tieneNotice,
            // El repo es público: eso satisface el "source available" si algún día
            // se cambia a una licencia que lo exija. Con Apache-2.0 no se pide.
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
                license = LicenseId.APACHE2,
                hasLicenseFile = File(assetsDir, "LICENSE").exists(),
                hasNoticeFile = File(assetsDir, "NOTICE").exists(),
                sourcePublished = true,
                bundledAssets = modelos,
            )
    }
}
