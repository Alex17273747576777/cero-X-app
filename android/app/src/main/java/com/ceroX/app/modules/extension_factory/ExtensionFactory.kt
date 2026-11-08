package com.ceroX.app.modules.extension_factory

import java.io.File

/**
 * ExtensionFactory — Motor de creación de extensiones.
 */
class ExtensionFactory {

    fun initialize() {
        // Inicializar fábrica
    }

    fun isEnabled(): Boolean = true

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
}