package com.ceroX.app.modules.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.UserManager
import java.io.File

/**
 * Mide los hechos del aparato y se los pasa a [Bancos], que decide.
 *
 * Todo va dentro de `runCatching`: un diagnóstico que se cae no puede tumbar la
 * pantalla que lo muestra, y un dato que no se pudo leer se dice como no leído
 * en vez de inventarse.
 */
object BancosAndroid {

    fun lineas(c: Context): List<String> {
        val ramMb = runCatching {
            val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem / 1_048_576L
        }.getOrDefault(-1L)

        val perfiles = runCatching {
            (c.getSystemService(Context.USER_SERVICE) as UserManager).userProfiles.size
        }.getOrDefault(1)

        val sandbox = Bancos.PAQUETES_DE_SANDBOX.map { it.first }.filter { paquete ->
            runCatching { c.packageManager.getPackageInfo(paquete, 0) }.isSuccess
        }

        val cpuinfo = runCatching { File("/proc/cpuinfo").readText() }.getOrDefault("")
        val kvm = Bancos.kvmPresente(listOf("/dev/kvm").filter { File(it).exists() })

        return Bancos.lineas(
            huella = Build.FINGERPRINT,
            hardware = Build.HARDWARE,
            producto = Build.PRODUCT,
            fabricante = Build.MANUFACTURER,
            modelo = Build.MODEL,
            abis = Build.SUPPORTED_ABIS.toList(),
            nucleos = Runtime.getRuntime().availableProcessors(),
            ramMb = ramMb,
            banderas = Bancos.banderasDeVirtualizacion(cpuinfo),
            kvm = kvm,
            paquetesSandbox = sandbox,
            perfiles = perfiles,
        )
    }
}
