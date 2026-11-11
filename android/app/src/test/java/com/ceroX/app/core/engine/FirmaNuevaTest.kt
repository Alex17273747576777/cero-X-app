package com.ceroX.app.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Las pruebas de la firma y del flujo que construye el APK.
 *
 * El certificado de la 1.0.0-beta se perdió: se firmó a mano y la clave no quedó
 * en ninguna parte recuperable, así que hubo que desinstalar la build del teléfono
 * y empezar de cero con otro certificado. Estas pruebas no firman nada (no hay
 * keystore en el repo, ni debe haberlo): atan los tres hechos que hacen que el
 * próximo APK sea reproducible y que la auditoría no apruebe una huella que ya no
 * existe.
 */
class FirmaNuevaTest {

    /** Busca un fichero del repo subiendo desde el directorio de trabajo del test. */
    private fun delRepo(rel: String): File? =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, rel) }
            .firstOrNull { it.exists() }

    @Test
    fun laHuellaViveEnUnSoloFicheroYTieneLaFormaDeUnaHuella() {
        val f = delRepo("entorno/HUELLA-CERTIFICADO.txt")
        assertTrue("falta entorno/HUELLA-CERTIFICADO.txt", f != null)
        val h = f!!.readText().trim()
        assertEquals("la huella son 64 hexadecimales y nada más", 64, h.length)
        assertTrue("y todos son hexadecimales en minúscula", h.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun laAuditoriaLeeLaHuellaDelFicheroYNoLaViejaDePerdida() {
        val a = delRepo(".github/workflows/auditoria-publicaciones.yml")
        assertTrue("falta el flujo de auditoría", a != null)
        val t = a!!.readText()
        assertTrue("la auditoría lee el fichero de la huella", t.contains("entorno/HUELLA-CERTIFICADO.txt"))
        assertFalse(
            "la huella del certificado perdido sigue escrita a mano en la auditoría",
            t.contains("f03505ce861af5ddbf1a2c31fe308a5d7ae822c40689cf5893a8263b81232fb3\""),
        )
    }

    @Test
    fun elFlujoDelApkFirmaCompruebaYNuncaLlevaLaClaveDentro() {
        val f = delRepo(".github/workflows/apk.yml")
        assertTrue("falta el flujo del APK", f != null)
        val w = f!!.readText()
        assertTrue("compila de release", w.contains(":app:assembleRelease"))
        assertTrue("sin modelos", w.contains("-PsinModelos"))
        assertTrue("verifica con apksigner", w.contains("apksigner"))
        assertTrue("compara la huella con la del fichero", w.contains("entorno/HUELLA-CERTIFICADO.txt"))
        assertTrue("y cuenta los ONNX del ZIP", w.contains("onnx"))
        assertTrue("la clave viene de los secretos", w.contains("secrets.CEROX_KEYSTORE_B64"))
        assertFalse(
            "ningún flujo lleva la clave en claro",
            w.contains("BEGIN PRIVATE KEY"),
        )
    }
}
