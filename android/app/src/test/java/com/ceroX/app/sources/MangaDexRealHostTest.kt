package com.ceroX.app.sources

import android.app.Application
import android.content.SharedPreferences
import com.ceroX.app.core.AlmacenExterno
import com.ceroX.app.core.PreferenciasExtensiones
import com.ceroX.app.modules.extension_sandbox.ExtensionValidator
import com.ceroX.app.modules.extension_sandbox.RepositoriosAuditados
import com.ceroX.app.modules.extension_sandbox.sha256
import eu.kanade.tachiyomi.source.ExtensionSourceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Inspección JVM del APK auténtico. No afirma ejecutar DEX: eso pertenece a
 * androidTest/MangaDexAndroidTest, donde Android proporciona DexClassLoader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = MangaDexRealHostTest.HostApplicationPrueba::class)
class MangaDexRealHostTest {
    class HostApplicationPrueba : Application() {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            if (name.startsWith("source_")) PreferenciasExtensiones.obtener(this, name)
            else super.getSharedPreferences(name, mode)

        override fun getCacheDir(): File = File(
            requireNotNull(AlmacenExterno.directorio(this)), "cache",
        ).apply { mkdirs() }
    }

    @Test
    fun apk_real_conserva_hash_firma_y_contrato_16() {
        val ruta = System.getenv("CEROX_MANGADEX_APK")
        assumeTrue("Sólo con CEROX_MANGADEX_APK=/ruta/mangadex.apk", !ruta.isNullOrBlank())
        val apk = File(ruta)
        assertTrue("No existe el APK fijado: $apk", apk.isFile)
        assertEquals(
            "176784fcf18d5ec5e29d22039b8bc4bb2b939865f2d301ce712229a8cf6b36cf",
            sha256(apk),
        )
        val firma = ExtensionValidator(setOf(RepositoriosAuditados.FIRMA_PROJECT_NOX)).validate(apk.path)
        assertTrue(firma.errors.joinToString("; "), firma.isValid)
        val loader = ExtensionSourceLoader(
            RuntimeEnvironment.getApplication() as Application,
            trustedSignerSha256 = setOf(RepositoriosAuditados.FIRMA_PROJECT_NOX),
        )
        assertTrue("El manifiesto 1.6 real no fue reconocido", loader.declaresContract(apk))
        println("NOX_APK_OK hash_firma_contrato=verificados ejecucion_dex=androidTest")
    }
}
