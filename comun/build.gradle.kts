// El núcleo compartido de cero-X: las reglas que valen en Android, escritorio,
// iOS y web, sin copiarlas tres veces.
//
// POR QUÉ KOTLIN MULTIPLATAFORMA Y NO UN MONOREPO DE CÓDIGO DUPLICADO
// -----------------------------------------------------------------
// Lo que comparten las cuatro plataformas no es interfaz gráfica: es el parseo
// de un capítulo, el límite de una cola de descargas, la regla de no retroceder
// el progreso de un tracker y el motor JavaScript. Eso es lógica pura, y la
// lógica pura se puede compilar a un `.jar` (escritorio), a un framework
// (iOS) y a `.js` (web) desde el mismo fuente.
plugins {
    kotlin("multiplatform") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

/**
 * `withJava()` añade `compileJava` con el JDK de la máquina (21) mientras Kotlin
 * compila a 11, y Gradle se niega —con razón— a mezclar bytecode. Se fijan los
 * dos al mismo nivel: 11, que es lo que corre en Android 8 y en cualquier
 * escritorio con una JVM de 2018.
 */
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes("Main-Class" to "com.cerox.comun.Escritorio")
    }
}

/**
 * Un `.jar` con todo dentro: stdlib y coroutines.
 *
 * El `jvmJar` normal pesa 33 KB y **no arranca solo**, porque en tiempo de
 * ejecución le faltan las clases de Kotlin. Que un artefacto no arranque es el
 * tipo de detalle que hace que «hay versión de escritorio» sea mentira. Este
 * junta las dos cosas y se comprueba ejecutándolo.
 */
val jarCompleto by tasks.registering(Jar::class) {
    group = "build"
    description = "Empaqueta el núcleo con sus dependencias para ejecutarlo con java -jar"
    archiveClassifier.set("todo")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to "com.cerox.comun.Escritorio")
    }
    from(kotlin.targets.getByName("jvm").compilations.getByName("main").output.allOutputs)
    dependsOn("jvmJar")
    val configuracion = configurations.named("jvmRuntimeClasspath")
    from(configuracion.map { cfg -> cfg.map { if (it.isDirectory) it else zipTree(it) } })
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
        // El `.jar` tiene que poder ejecutarse sin escribir un `java -cp` con
        // cinco rutas: `java -jar cerox-comun-jvm.jar estado`. Eso es lo que
        // hace que «hay escritorio» signifique algo.
        withJava()
    }
    js(IR) {
        nodejs()
        binaries.library()
    }
    // iOS: los binarios solo se pueden enlazar en macOS con Xcode. Declarar el
    // target aquí deja el código listo y el build avisa de que no se compila en
    // Linux en vez de fallar de forma confusa.
    val iosTargets = listOf(iosX64(), iosArm64(), iosSimulatorArm64())
    iosTargets.forEach {
        it.binaries.framework {
            baseName = "CeroxComun"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}
