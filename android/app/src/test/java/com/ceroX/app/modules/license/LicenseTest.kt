package com.ceroX.app.modules.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Licencia: qué obliga la GPL y qué no.
 *
 * Estas pruebas fijan las cláusulas que más se malinterpretan, para que una
 * futura "simplificación" no convierta la GPL en MIT por descuido.
 */
class LicenseTest {

    @Test
    fun `gpl obliga a publicar el codigo al distribuir`() {
        assertTrue(LicenseId.GPL3.requiresSourceOnDistribution)
        assertTrue(LicenseId.LGPL3.requiresSourceOnDistribution)
    }

    @Test
    fun `apache y mit no obligan`() {
        assertFalse(LicenseId.APACHE2.requiresSourceOnDistribution)
        assertFalse(LicenseId.MIT.requiresSourceOnDistribution)
    }

    @Test
    fun `la gpl no se puede relicenciar a permisiva`() {
        assertFalse(
            "bajar de GPL a MIT quita derechos a quien ya recibio bajo GPL",
            LicenseId.GPL3.canRelicensePermissively,
        )
    }

    @Test
    fun `apache y mit si se pueden relicenciar`() {
        assertTrue(LicenseId.APACHE2.canRelicensePermissively)
        assertTrue(LicenseId.MIT.canRelicensePermissively)
    }

    @Test
    fun `el spdx de cero-X es GPL-3-0-only`() {
        assertEquals("GPL-3.0-only", LicenseId.GPL3.spdx)
    }

    // ── Cumplimiento: no se declara cumplido sin evidencia ───────────────────

    private fun compliance(
        license: Boolean = true,
        notice: Boolean = true,
        source: Boolean = true,
        assets: List<LicenseCompliance.BundledAsset> = emptyList(),
    ) = LicenseCompliance(
        license = LicenseId.GPL3,
        hasLicenseFile = license,
        hasNoticeFile = notice,
        sourcePublished = source,
        bundledAssets = assets,
    )

    @Test
    fun `sin LICENSE no cumple`() {
        assertFalse(compliance(license = false).isCompliant())
        assertTrue(compliance(license = false).pending().any { it.id == "license-file" })
    }

    @Test
    fun `con GPL el codigo tiene que estar publicado`() {
        val c = compliance(source = false)
        assertFalse(c.isCompliant())
        assertTrue(c.pending().any { it.id == "source-available" })
    }

    @Test
    fun `sin NOTICE no cumple, porque hay linaje`() {
        val c = compliance(notice = false)
        assertFalse(c.isCompliant())
        assertTrue(c.pending().any { it.id == "notice" })
    }

    @Test
    fun `cada modelo empaquetado arrastra su licencia`() {
        val c = compliance(
            assets = listOf(
                LicenseCompliance.BundledAsset("manga-ocr", "apache-2.0", attributionInApp = true),
                LicenseCompliance.BundledAsset("real-esrgan", "BSD-3-Clause", attributionInApp = false),
            ),
        )
        val pend = c.pending()
        assertEquals(1, pend.size)
        assertEquals("asset:real-esrgan", pend[0].id)
    }

    @Test
    fun `todas las obligaciones cumplidas da cumplido`() {
        val c = compliance(
            assets = listOf(LicenseCompliance.BundledAsset("manga-ocr", "apache-2.0", true)),
        )
        assertTrue(c.isCompliant())
        assertTrue(c.summary().contains("sin obligaciones pendientes"))
    }

    @Test
    fun `una licencia permisiva no exige publicar el codigo`() {
        val c = LicenseCompliance(
            license = LicenseId.MIT,
            hasLicenseFile = true,
            hasNoticeFile = true,
            sourcePublished = false,
            bundledAssets = emptyList(),
        )
        assertTrue(c.isCompliant())
        assertFalse(c.obligations().any { it.id == "source-available" })
    }

    @Test
    fun `el resumen cuenta las pendientes, no las esconde`() {
        val c = compliance(license = false, notice = false)
        assertTrue(c.summary().contains("2 pendiente"))
    }
}
