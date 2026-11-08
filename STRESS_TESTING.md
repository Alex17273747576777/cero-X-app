# Pruebas de Estrés al Extremo — cero-X

## Suite de Pruebas

cero-X incluye una suite completa de pruebas de estrés diseñada para llevar la aplicación al límite en todos sus módulos.

## Estructura de Pruebas

```
tests/
├── stress/
│   ├── library_stress_test.kt        # Biblioteca masiva
│   ├── download_stress_test.kt       # Descargas concurrentes
│   ├── ai_stress_test.kt             # Inferencia IA on-device
│   ├── extension_stress_test.kt      # 100+ extensiones
│   ├── thermal_stress_test.kt        # Estrés térmico 2h
│   └── compatibility_stress_test.kt  # Android 8-14
├── unit/
└── integration/
```

## Prueba 1: Biblioteca Masiva (Library Stress Test)

```kotlin
// Cargar 10,000 series en la biblioteca
val seriesCount = 10000
val startTime = System.currentTimeMillis()

for (i in 1..seriesCount) {
    readerModule.addToLibrary(LibraryItem(
        id = "series_$i",
        title = "Manga Series $i",
        author = "Author ${i % 100}",
        type = "manga"
    ))
}

val loadTime = System.currentTimeMillis() - startTime
// Umbral: < 5000ms para 10,000 series
```

### Métricas:
- Tiempo de carga de la biblioteca
- Uso de memoria después de cargar
- Tiempo de búsqueda en biblioteca
- Tiempo de scroll por 1000 items

## Prueba 2: Descargas Concurrentes (Download Stress Test)

```kotlin
// 50 descargas simultáneas
val concurrentDownloads = 50
val downloadUrls = generateTestUrls(concurrentDownloads)

val results = downloadUrls.parallelMap { url ->
    downloadsModule.downloadFile(url, "test_chapter_${url.hashCode()}.cbz")
}

// Verificar integridad SHA-256 de cada descarga
results.forEach { result ->
    assertTrue(downloadsModule.verifySha256(result.path, result.expectedHash))
}
```

### Métricas:
- Tiempo de descarga por archivo
- Tasa de éxito (debe ser 100%)
- Integridad SHA-256 verificada
- Ancho de banda pico
- Reanudación de descargas interrumpidas

## Prueba 3: IA On-Device (AI Stress Test)

```kotlin
// Colorización de 100 páginas de manga
val pages = listOf(/* 100 páginas B&W */)
val results = pages.parallelMap { page ->
    aiModule.neuralColorizer.colorize(page)
}

// Medir tiempo por página
val avgTimePerPage = results.map { it.inferenceTimeMs }.average()
// Umbral: < 2000ms por página en dispositivo medio
```

### Métricas:
- Tiempo de inferencia por página
- Uso de NPU/GPU vs CPU
- Temperatura del dispositivo durante inferencia
- Calidad de colorización (puntuación DeepSeek Harness)
- Estabilidad sin caídas

## Prueba 4: Extensiones Múltiples (Extension Stress Test)

```kotlin
// Activar 100+ extensiones simultáneas
val extensions = (1..100).map { ExtensionInfo("ext_$it", "https://source$it.com") }
extensions.forEach { readerModule.registerExtension(it.id, it.source) }

// Búsqueda en paralelo en todas las extensiones
val searchResults = extensions.parallelMap { ext ->
    readerModule.search("One Piece", source = ext.id)
}
```

### Métricas:
- Tiempo de búsqueda agregado
- Tasa de éxito por extensión
- Uso de memoria con 100 extensiones
- Detección de extensiones colapsadas

## Prueba 5: Estrés Térmico (Thermal Stress Test)

```kotlin
// Ejecutar carga máxima durante 2 horas continuas
val startTime = System.currentTimeMillis()
val twoHours = 2 * 60 * 60 * 1000

while (System.currentTimeMillis() - startTime < twoHours) {
    // Carga mixta: lectura + descarga + IA
    readerModule.readChapter(chapter)
    downloadsModule.downloadFile(url, filename)
    aiModule.neuralColorizer.colorize(page)
    
    // Monitorear temperatura cada 5 minutos
    if (shouldCheckTemperature()) {
        val temp = hardwareMonitor.getDeviceTemperature()
        if (temp > THROTTLING_THRESHOLD) {
            logWarning("Dispositivo en throttling: $temp°C")
        }
    }
}
```

### Métricas:
- Temperatura pico del dispositivo
- Throttling de CPU detectado
- Caídas de la app durante la prueba
- Consumo de batería
- Estabilidad de frames (FPS) durante lectura

## Prueba 6: Compatibilidad Android 8-14

```kotlin
// Ejecutar en cada versión de Android
val androidVersions = listOf(26, 27, 28, 29, 30, 31, 32, 33, 34)

androidVersions.forEach { apiLevel ->
    val result = runOnEmulator(apiLevel) {
        // Instalar app
        // Ejecutar todas las funciones principales
        // Verificar permisos
        // Verificar foreground service
        // Verificar notificaciones
    }
    assertTrue(result.allTestsPassed)
}
```

### Métricas por versión:
- Instalación exitosa
- Todos los permisos otorgados
- Lectura de capítulo funcionando
- Descarga funcionando
- IA on-device funcionando
- Notificaciones funcionando

## Informe de Pruebas de Estrés

Tras completar todas las pruebas, se genera un informe:

```
docs/stress_testing/
├── library_stress_report.md
├── download_stress_report.md
├── ai_stress_report.md
├── extension_stress_report.md
├── thermal_stress_report.md
└── compatibility_report.md
```

Cada informe incluye:
- Fecha y hora de ejecución
- Dispositivo(s) utilizado(s)
- Versión de cero-X
- Métricas recopiladas
- Fallos detectados (si los hay)
- Recomendaciones de optimización

## Umbrales de Aprobación

| Prueba | Umbral de Aprobación |
|--------|---------------------|
| Biblioteca 10,000 series | Carga < 5s, sin caídas |
| 50 descargas concurrentes | 100% éxito, SHA-256 verificado |
| IA 100 páginas | < 2s/página, sin caídas |
| 100 extensiones | Búsqueda < 3s, sin extensiones rotas |
| Estrés térmico 2h | 0 caídas, temperatura < 45°C |
| Compatibilidad 8-14 | Todas las versiones pasan |

---

*Suite de pruebas de estrés integrada en cero-X para validación de calidad al extremo.*