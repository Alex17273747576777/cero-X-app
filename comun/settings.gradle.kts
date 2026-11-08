// Los repositorios van aquí y no en cada módulo: `comun` es la raíz de su
// propio build, así que sin `dependencyResolutionManagement` Gradle no sabe de
// dónde bajar ni el stdlib de Kotlin, y el fallo sale como «no repositories
// are defined» en vez de decir que falta este bloque.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "cerox-comun"
