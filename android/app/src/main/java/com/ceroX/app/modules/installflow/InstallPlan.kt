package com.ceroX.app.modules.installflow

import com.ceroX.app.modules.permissions.PrivilegeLevel

/**
 * En qué plano corre una instalación.
 *
 * El usuario pidió «instalaciones automatizadas para correr en el primer o segundo
 * plano». Eso significa cosas distintas según el privilegio, y aquí se dice cuál:
 *
 *   • **Primer plano**: la app está abierta, se ve la pantalla de confirmación
 *     del sistema y el usuario pulsa. Funciona siempre, con cualquier privilegio.
 *   • **Segundo plano**: un `ForegroundService` instala sin que la pantalla esté
 *     abierta. Android exige que el servicio sea visible en la barra de estado y
 *     que exista una notificación; no es opcional y no se puede esquivar.
 *   • **Silencioso**: sin diálogo del sistema. Requiere root o Shizuku autorizado.
 *     **No** es lo mismo que segundo plano: un segundo plano sin privilegio sigue
 *     necesitando que alguien confirme.
 */
enum class InstallMode(val id: String, val displayName: String) {
    FOREGROUND("primer_plano", "Primer plano"),
    BACKGROUND("segundo_plano", "Segundo plano (servicio en primer plano del sistema)"),
    SILENT("silencioso", "Silencioso (sin diálogo)"),
}

/**
 * Con qué método se instala. Cada uno tiene un requisito distinto y una
 * consecuencia distinta si falla.
 */
enum class InstallMethod(val id: String, val requires: PrivilegeLevel) {
    /** Session-based con confirmación del usuario. Siempre disponible. */
    PACKAGE_INSTALLER("package_installer", PrivilegeLevel.NORMAL),

    /** Shizuku, que actúa como shell. Necesita los tres escalones de autorización. */
    SHIZUKU("shizuku", PrivilegeLevel.SHIZUKU),

    /** `pm install` por su. Silencioso de verdad. */
    ROOT_PM("root_pm", PrivilegeLevel.ROOT),
    ;

    val isSilent: Boolean get() = this == SHIZUKU || this == ROOT_PM
}

/**
 * Un paso del plan. Los pasos son datos, no código, para poder enseñárselos al
 * usuario **antes** de que ocurra nada.
 */
data class InstallStep(
    val order: Int,
    val description: String,
    val mode: InstallMode,
    /** Si el usuario tiene que tocar algo. Si es true, "automático" es mentira. */
    val needsUserTouch: Boolean,
)

/**
 * El plan completo: qué se va a hacer, con qué método, y qué va a requerir al
 * usuario.
 *
 * Esta es la pieza que evita el patrón de las 14 mentiras: **el plan se calcula
 * antes de ejecutar y se puede rechazar**. Si el plan dice que hay que pulsar un
 * botón, la app no puede anunciar "instalación automática".
 */
data class InstallPlan(
    val method: InstallMethod,
    val mode: InstallMode,
    val steps: List<InstallStep>,
    val warnings: List<String> = emptyList(),
) {
    /** Si de verdad no requiere intervención del usuario. */
    val fullyAutomatic: Boolean get() = steps.none { it.needsUserTouch }

    /**
     * Cómo describirlo sin mentir. Es lo que se pone en la notificación y en la
     * pantalla, y por eso no puede ser una frase hecha.
     */
    fun honestDescription(): String = when {
        fullyAutomatic && mode == InstallMode.SILENT ->
            "instalación silenciosa vía ${method.id}, sin tocar la pantalla"
        fullyAutomatic && mode == InstallMode.BACKGROUND ->
            "instalación automática en segundo plano vía ${method.id}"
        else ->
            "instalación con ${steps.count { it.needsUserTouch }} confirmación(es) del sistema"
    }
}

/**
 * Decide el plan según el privilegio **real**.
 *
 * Orden de preferencia, y el motivo:
 *   1. ROOT → silencioso por `pm`. Es el único que no toca la pantalla.
 *   2. SHIZUKU → silencioso por shell. Igual de silencioso, menos invasivo.
 *   3. NORMAL → PackageInstaller con confirmación. **Siempre funciona**, y por eso
 *      es el que se usa cuando no hay nada mejor.
 *
 * Lo que este planificador **no** hace: fingir que NORMAL puede ser silencioso.
 * No puede, y ninguna cantidad de código lo cambia sin privilegio.
 */
object InstallPlanner {

    fun plan(level: PrivilegeLevel, wantBackground: Boolean = true): InstallPlan =
        when (level) {
            PrivilegeLevel.ROOT -> InstallPlan(
                method = InstallMethod.ROOT_PM,
                mode = InstallMode.SILENT,
                steps = listOf(
                    InstallStep(1, "verificar el APK (firma y sha256)", InstallMode.SILENT, false),
                    InstallStep(2, "ejecutar pm install por su", InstallMode.SILENT, false),
                    InstallStep(3, "confirmar que el paquete quedó instalado", InstallMode.SILENT, false),
                ),
                warnings = listOf("root es de tu teléfono: la app no lo pide ni lo escala sola"),
            )

            PrivilegeLevel.SHIZUKU -> InstallPlan(
                method = InstallMethod.SHIZUKU,
                mode = InstallMode.SILENT,
                steps = listOf(
                    InstallStep(1, "verificar el APK (firma y sha256)", InstallMode.SILENT, false),
                    InstallStep(2, "instalar por el binder de Shizuku", InstallMode.SILENT, false),
                    InstallStep(3, "confirmar que el paquete quedó instalado", InstallMode.SILENT, false),
                ),
                warnings = listOf("si Shizuku se detiene, la instalación falla con un mensaje, no en silencio"),
            )

            PrivilegeLevel.NORMAL, PrivilegeLevel.UNKNOWN -> InstallPlan(
                method = InstallMethod.PACKAGE_INSTALLER,
                mode = if (wantBackground) InstallMode.BACKGROUND else InstallMode.FOREGROUND,
                steps = listOf(
                    InstallStep(1, "verificar el APK (firma y sha256)", InstallMode.BACKGROUND, false),
                    InstallStep(2, "abrir la sesión de instalación del sistema", InstallMode.BACKGROUND, false),
                    InstallStep(
                        3,
                        "el usuario confirma en la pantalla de Android",
                        InstallMode.FOREGROUND,
                        // El único paso que no se puede automatizar sin privilegio.
                        true,
                    ),
                    InstallStep(4, "confirmar que el paquete quedó instalado", InstallMode.BACKGROUND, false),
                ),
                warnings = listOf(
                    "sin root ni Shizuku, Android exige que alguien confirme: " +
                        "no es una limitación de la app",
                    "SourceUnknownSources tiene que estar permitido para cero-X",
                ),
            )
        }

    /**
     * Si un método concreto puede usarse con este nivel. Se usa para no ofrecer
     * en la UI algo que va a fallar.
     */
    fun canUse(method: InstallMethod, level: PrivilegeLevel): Boolean = when (method.requires) {
        PrivilegeLevel.NORMAL -> true
        // Con root se puede hacer todo lo que hace Shizuku: el root es un shell
        // con más permisos, no menos.
        PrivilegeLevel.SHIZUKU -> level == PrivilegeLevel.SHIZUKU || level == PrivilegeLevel.ROOT
        PrivilegeLevel.ROOT -> level == PrivilegeLevel.ROOT
        // `when` sobre un enum es exhaustivo, pero Android Studio avisa si algún
        // día se añade un nivel. UNKNOWN nunca habilita nada: no se instala en
        // silencio por no haber podido averiguar el privilegio.
        PrivilegeLevel.UNKNOWN -> false
    }
}
