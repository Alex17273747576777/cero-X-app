package com.ceroX.app.modules.adaptive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdaptacionPantallaTest {
    @Test fun no_reordena_sin_aplicar_ni_guarda_consultas() {
        val archivo = File("src/main/java/com/ceroX/app/modules/directories/DirectoryScreen.kt")
        val texto = archivo.readText()
        assertTrue(texto.contains("remember(categoria, revisionAdaptacion, habilitado)"))
        assertTrue(texto.contains("Aplicar ("))
        assertTrue(texto.contains("Deshacer"))
        assertTrue(texto.contains("Restablecer"))
        assertTrue(texto.contains("rememberSaveable { UUID.randomUUID().toString() }"))
        assertFalse(Regex("registrar\\([^\\n]*consulta").containsMatchIn(texto))
    }
}
