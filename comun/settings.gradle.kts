// Settings propio de `comun`, para poder compilarlo y probarlo SOLO (jvmTest,
// jsNodeTest) sin arrastrar todo el build de Android. Cuando el build de Android
// lo incluye como `:comun`, este archivo no se lee: manda el de Android.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        kotlin("multiplatform") version "2.0.21"
        kotlin("plugin.serialization") version "2.0.21"
        id("com.android.library") version "8.9.1"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cerox-comun"
