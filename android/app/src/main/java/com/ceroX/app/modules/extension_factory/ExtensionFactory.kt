package com.ceroX.app.modules.extension_factory

import java.io.File

/**
 * ExtensionFactory — Motor de creación de extensiones.
 */
class ExtensionFactory {

    private var enabled = false

    fun initialize() { enabled = true }

    /**
     * Antes devolvía `true` fijo, sin que nadie hubiera inicializado nada: la
     * fábrica decía estar disponible desde el primer instante. Ahora refleja si
     * `initialize()` se ejecutó.
     */
    fun isEnabled(): Boolean = enabled

    fun createExtension(template: ExtensionTemplate, config: ExtensionConfig): FactoryResult {
        val projectDir = File("${System.getProperty("java.io.tmpdir")}/ceroX_extensions/${config.packageName}")
        projectDir.mkdirs()

        // Crear estructura de proyecto
        createProjectStructure(projectDir, template, config)

        return FactoryResult(true, "Extensión creada en ${projectDir.absolutePath}", projectDir.absolutePath)
    }

    private fun createProjectStructure(dir: File, template: ExtensionTemplate, config: ExtensionConfig) {
        // Crear directorios
        File(dir, "src/main/java/${config.packageName.replace(".", "/")}").mkdirs()
        File(dir, "src/main/res").mkdirs()
        File(dir, "src/main/res/values").mkdirs()

        // Crear build.gradle
        File(dir, "build.gradle").writeText(generateBuildGradle(config))

        // Crear AndroidManifest.xml
        File(dir, "src/main/AndroidManifest.xml").writeText(generateManifest(config))

        // Crear código fuente de la extensión
        File(dir, "src/main/java/${config.packageName.replace(".", "/")}/MainExtension.kt").writeText(template.sourceCode)

        // Crear archivo de configuración de extensión
        File(dir, "extension.json").writeText(generateExtensionJson(config))
    }

    private fun generateBuildGradle(config: ExtensionConfig): String = """
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace '${config.packageName}'
    compileSdk 36
    defaultConfig {
        applicationId '${config.packageName}'
        minSdk 26
        targetSdk 36
        versionCode 1
        versionName '${config.version}'
    }
}

dependencies {
    implementation project(':ceroX-extension-api')
}
""".trimIndent()

    private fun generateManifest(config: ExtensionConfig): String = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${config.packageName}">

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="${config.name}"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.ceroX">

        <service
            android:name=".MainExtension"
            android:exported="true">
            <intent-filter>
                <action android:name="com.ceroX.app.EXTENSION" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </service>
    </application>
</manifest>
""".trimIndent()

    private fun generateExtensionJson(config: ExtensionConfig): String = """
{
  "name": "${config.name}",
  "package": "${config.packageName}",
  "version": "${config.version}",
  "author": "${config.author}",
  "description": "${config.description}",
  "type": "${config.type}",
  "baseUrl": "${config.baseUrl}",
  "sandbox": true,
  "ceroXCompatible": true
}
""".trimIndent()

    /**
     * Plantillas disponibles para crear una extensión desde cero.
     *
     * ExtensionSandboxModule llamaba a `factory.listTemplates()` y a
     * `factory.compileExtension()`, dos métodos que nunca se escribieron: el
     * módulo del sandbox estaba escrito contra una fábrica imaginaria.
     * `compileExtension` delega en ExtensionCompiler, que sí existe.
     */
    fun listTemplates(): List<ExtensionTemplate> = TEMPLATES

    fun compileExtension(projectPath: String): CompileResult =
        ExtensionCompiler().compileExtension(projectPath)

    companion object {
        /**
         * Plantillas mínimas. Se generan con el mismo código que usa la fábrica,
         * así una extensión creada desde plantilla y una creada a mano salen
         * idénticas en estructura.
         */
        val TEMPLATES: List<ExtensionTemplate> = listOf(
            ExtensionTemplate(
                id = "manga-html",
                name = "Manga (HTML)",
                description = "Extensión de manga que lee un sitio HTML con selectores CSS.",
                type = ExtensionType.MANGA,
                sourceCode = "// Extensión de manga generada por cero-X\n" +
                    "class MainExtension : ExtensionAPI() {\n" +
                    "    override val name = \"Mi fuente\"\n" +
                    "    override val baseUrl = \"https://ejemplo.org\"\n" +
                    "}\n",
            ),
            ExtensionTemplate(
                id = "anime-html",
                name = "Anime (HTML)",
                description = "Extensión de anime que lee un catálogo HTML.",
                type = ExtensionType.ANIME,
                sourceCode = "// Extensión de anime generada por cero-X\n" +
                    "class MainExtension : ExtensionAPI() {\n" +
                    "    override val name = \"Mi fuente de anime\"\n" +
                    "}\n",
            ),
            ExtensionTemplate(
                id = "novela-epub",
                name = "Novela (EPUB)",
                description = "Extensión de novela que sirve capítulos desde EPUB.",
                type = ExtensionType.NOVEL,
                sourceCode = "// Extensión de novela generada por cero-X\n" +
                    "class MainExtension : ExtensionAPI() {\n" +
                    "    override val name = \"Mi novela\"\n" +
                    "}\n",
            ),
        )
    }
}
