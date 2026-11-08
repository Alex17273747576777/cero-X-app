# Arquitectura de Software — cero-X App

## Visión General

cero-X sigue una arquitectura de **núcleo modular** inspirada en el diseño del núcleo de Linux. El núcleo (`core/`) gestiona el ciclo de vida, eventos, configuración y comunicación entre módulos. Cada módulo es un paquete independiente con su propia interfaz pública.

## Capas del Sistema

```
┌─────────────────────────────────────────────────────────────┐
│                    capa de aplicación (UI)                  │
├─────────────────────────────────────────────────────────────┤
│              capa de orquestación de módulos                │
│  (ai-training · downloads · reader · audio · video · RE · env)│
├─────────────────────────────────────────────────────────────┤
│                    capa del núcleo (core)                   │
│           (event bus · configuración · ciclo de vida)       │
├─────────────────────────────────────────────────────────────┤
│              capa de integración (adapters)                 │
│   (Mihon adapter · Wotaku adapter · Kisara adapter · ONNX)  │
├─────────────────────────────────────────────────────────────┤
│             capa de entorno y ejecución                     │
│     (Debian containers · ONNX Runtime · NNAPI · CPU)        │
└─────────────────────────────────────────────────────────────┘
```

## Core Engine

El `core/engine.py` implementa:

- **Event Bus**: Sistema de publicación/suscripción para comunicación entre módulos
- **Module Registry**: Registro de módulos cargados y sus metadatos
- **Config Manager**: Gestión de configuración jerárquica (YAML + variables de entorno)
- **Lifecycle Manager**: Inicio, pausa, reinicio y apagado ordenado de módulos

## Módulos

Cada módulo implementa la interfaz `ModuleInterface`:

```python
class ModuleInterface:
    def initialize(self, config: dict) -> bool: ...
    def start(self) -> bool: ...
    def stop(self) -> bool: ...
    def health_check(self) -> dict: ...
    def get_metadata(self) -> dict: ...
```

### Módulo: ai-training
Entrenamiento y fine-tuning de modelos de IA locales. Soporta ONNX Runtime, CPU y NNAPI.

### Módulo: downloads
Gestor de descargas con soporte para múltiples protocolos, reanudación y verificación de integridad (SHA-256).

### Módulo: reader
Lector de manga, novelas y cómics. Basado en la arquitectura de Mihon/Komikku con sistema de extensiones.

### Módulo: audio
Reproductor y gestor de audio. Índice integrado de Wotaku para descubrimiento de música.

### Módulo: video
Reproductor y gestor de video con soporte para codecs abiertos.

### Módulo: reverse-engineering
Herramientas de ingeniería inversa integradas para análisis de binarios, protocolos y ecosistemas de terceros.

### Módulo: environments
Gestor de entornos de trabajo. Soporta Debian (estable/testing), contenedores y máquinas virtuales.

## Patrones de Diseño Utilizados

| Patrón | Uso |
|--------|-----|
| Observer | Event bus para comunicación entre módulos |
| Factory | Creación de instancias de módulos y adaptadores |
| Strategy | Selección de motor de ejecución (CPU/NNAPI/GPU) |
| Adapter | Integración con ecosistemas externos (Mihon, Wotaku, Kisara) |
| Singleton | Gestor de configuración y núcleo del sistema |
| Plugin | Módulos cargables dinámicamente |

## Dependencias Clave

- Python 3.10+
- ONNX Runtime (inferencia IA on-device)
- PyYAML (configuración)
- Requests (cliente HTTP)
- SQLite (almacenamiento local)
- FFmpeg (procesamiento de audio/video)

## Licencia

Apache-2.0