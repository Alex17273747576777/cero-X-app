package com.ceroX.app.modules.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las decisiones de [Bancos] son puras, así que se prueban sin aparato.
 *
 * Los dos casos reales que importan: el LG V60 ThinQ 5G (físico, sin KVM, y aun
 * así banco válido porque no hace falta virtualizarlo) y la máquina de trabajo
 * (0 banderas, sin /dev/kvm, 1.982 MB), donde un emulador TCG no equivale a un
 * banco completo y el fallo real se informa sin convertirlo en una ley general.
 */
class BancosTest {

    private val v60 = listOf(
        "lge/v60lm_eea/v60lm:12/SP1A.210812.016/21325142437d5:user/release-keys",
        "msmnile", "v60lm_eea", "LGE",
    )

    @Test
    fun un_telefono_real_no_se_confunde_con_un_emulador() {
        assertFalse(
            "el V60 se tomó por emulador",
            Bancos.esEmulador(v60[0], v60[1], v60[2], v60[3]),
        )
        val veredicto = Bancos.veredicto(emulador = false, kvm = false, banderas = 0)
        assertTrue("el veredicto no dice que es físico: $veredicto", veredicto.contains("dispositivo físico"))
    }

    @Test
    fun un_emulador_se_reconoce_por_lo_que_declara() {
        assertTrue(
            Bancos.esEmulador("google/sdk_gphone64_arm64/generic64:14", "ranchu",
                              "sdk_gphone64_arm64", "Google"),
        )
        assertTrue(Bancos.esEmulador("generic", "goldfish", "sdk", "unknown"))
        assertFalse("una marca parecida no debe bastar", Bancos.esEmulador("a", "b", "c", "d"))
    }

    @Test
    fun las_banderas_de_virtualizacion_se_cuentan_de_verdad() {
        val conVmx = "flags\t\t: fpu vme de pse tsc msr pae mce cx8 vmx smep"
        val conSvm = "flags : fpu vme de pse svm sep"
        val sinNada = "Flags : fp asimd evtstrm aes pmull sha1 crc32"
        assertTrue(Bancos.banderasDeVirtualizacion(conVmx) >= 1)
        assertTrue(Bancos.banderasDeVirtualizacion(conSvm) >= 1)
        assertEquals(0, Bancos.banderasDeVirtualizacion(sinNada))
        assertEquals(0, Bancos.banderasDeVirtualizacion(""))
    }

    @Test
    fun kvm_solo_esta_si_la_medida_dice_que_esta() {
        assertTrue(Bancos.kvmPresente(listOf("/dev/kvm")))
        assertFalse(Bancos.kvmPresente(emptyList()))
        assertFalse("una ruta parecida no es KVM", Bancos.kvmPresente(listOf("/dev/kvm0")))
    }

    @Test
    fun un_emulador_sin_kvm_no_es_un_banco_completo() {
        val v = Bancos.veredicto(emulador = true, kvm = false, banderas = 0)
        assertTrue("el veredicto no avisa de la limitación: $v", v.contains("no es un banco completo"))
        assertTrue("no explica TCG: $v", v.contains("TCG"))
        val valido = Bancos.veredicto(emulador = true, kvm = true, banderas = 2)
        assertTrue("con KVM y banderas debería valer: $valido", valido.contains("banco válido"))
    }

    @Test
    fun el_sandbox_se_lista_por_lo_que_hay_instalado() {
        assertTrue(Bancos.sandboxDetectado(emptyList(), 1).isEmpty())
        val shizuku = Bancos.sandboxDetectado(listOf("rikka.shizuku"), 1)
        assertEquals(1, shizuku.size)
        assertTrue(shizuku[0].contains("Shizuku"))
        val perfil = Bancos.sandboxDetectado(emptyList(), 2)
        assertEquals(1, perfil.size)
        assertTrue(perfil[0].contains("perfil aislado"))
    }

    @Test
    fun una_afirmacion_sin_medidas_no_es_un_banco() {
        val vacia = Bancos.bancoDeclarado("VM instalada", emptyList())
        assertTrue("no se dijo que faltan medidas: $vacia", vacia.contains("intención"))
        val medida = Bancos.bancoDeclarado(
            "VM instalada",
            listOf("/dev/kvm presente", "banderas vmx/svm: 2"),
        )
        assertTrue(medida.contains("2 medidas"))
        assertTrue(medida.contains("/dev/kvm presente"))
    }

    @Test
    fun el_informe_lleva_los_hechos_y_el_veredicto() {
        val lineas = Bancos.lineas(
            huella = v60[0], hardware = v60[1], producto = v60[2], fabricante = v60[3],
            modelo = "LG V60 ThinQ 5G", abis = listOf("arm64-v8a", "armeabi-v7a"),
            nucleos = 8, ramMb = 7643L, banderas = 0, kvm = false,
            paquetesSandbox = emptyList(), perfiles = 1,
        )
        val texto = lineas.joinToString("\n")
        assertTrue("falta el veredicto", texto.contains("dispositivo físico"))
        assertTrue("falta la RAM medida", texto.contains("7643 MB"))
        assertTrue("faltan las ABI", texto.contains("arm64-v8a"))
        assertTrue("falta decir que no hay sandbox alrededor", texto.contains("ninguno instalado"))
        assertTrue(
            "el informe promete un sandbox que no mide",
            texto.contains("extensiones en QuickJS"),
        )
        assertEquals("la cabecera no es la que busca la prueba publicada",
                     Bancos.SECCION, "── BANCO DE PRUEBAS ──")
    }
}
