# cero-X App — Ecosistema Todo-en-Uno de Código Abierto

> **"El núcleo de Linux para el ecosistema de IA, lectura, audio y video — sin dependencias de GPU propietarias."**

## Visión

cero-X es una aplicación todo-en-uno de código abierto que integra los mejores ecosistemas de entrenamiento de IA, descargas, lectura de manga/novelas, audio y video en una sola plataforma modular. Inspirada en la filosofía del núcleo de Linux: un núcleo abierto, extensible y comunitario que sirve como base para todo lo demás.

## Filosofía de Diseño

| Principio | Descripción |
|-----------|-------------|
| **Código Abierto Primero** | Todo el stack es de código abierto. Sin blobs propietarios. |
| **Modularidad** | Cada módulo es independiente y pluggable. |
| **Local-First** | Procesamiento on-device. Sin dependencia de la nube. |
| **Multiplataforma** | Android, Linux, Windows, macOS. |
| **Sin GPU Propietaria** | Optimizado para CPU y NPU via NNAPI/ONNX. No requiere NVIDIA CUDA. |
| **Ingeniería Inversa Integrada** | Herramientas de RE incluidas para análisis y clonación de ecosistemas. |

## Módulos del Sistema

```
cero-X/
├── core/                 # Núcleo del sistema (motor, configuración, eventos)
├── modules/
│   ├── ai-training/      # Entrenamiento y fine-tuning de modelos IA
│   ├── downloads/        # Gestor de descargas P2P y directas
│   ├── reader/           # Lector de manga, novelas y cómics (Mihon/Komikku-based)
│   ├── audio/            # Reproductor y gestor de audio (Wotaku-based)
│   ├── video/            # Reproductor y gestor de video
│   ├── reverse-engineering/ # Herramientas de RE (Antigravity, DeepSeek Harness)
│   └── environments/     # Gestor de entornos (Debian, contenedores, VMs)
├── docs/                 # Documentación técnica
├── scripts/              # Scripts de instalación y clonación
├── config/               # Archivos de configuración
└── tests/                # Suite de pruebas
```

## Referencias de Ecosistema Integrado

- **Miyomi / Mihon / Komikku** — Lectores de manga código abierto para Android
- **Wotaku** — Índice otaku: música, anime, wiki, manga, novelas
- **ifritraen/kisara** — Fork de Komikku con ONNX Runtime y colorización neural
- **KisaraColorizer** — Colorización neural on-device con ONNX + NNAPI
- **Chukie99/Kisara** — Generador de videos de historias con IA local

## Instalación Rápida

```bash
# Clonar el repositorio
git clone https://github.com/tu-usuario/cero-X-app.git
cd cero-X-app

# Ejecutar script de instalación
chmod +x scripts/install.sh
./scripts/install.sh

# Iniciar la aplicación
python -m ceroX
```

## Roadmap

- [ ] v0.1 — Núcleo base y arquitectura modular
- [ ] v0.2 — Módulo lector (Mihon/Komikpu-based)
- [ ] v0.3 — Módulo audio (Wotaku-based)
- [ ] v0.4 — Módulo video
- [ ] v0.5 — Módulo IA y entrenamiento
- [ ] v0.6 — Ingeniería inversa integrada
- [ ] v0.7 — Gestor de entornos (Debian, contenedores)
- [ ] v1.0 — Release estable todo-en-uno

## Licencia

Apache-2.0 — Véase `LICENSE` para detalles.

---

*Desarrollado con la mentalidad del núcleo de Linux: abierto, comunitario, y sin dependencias de hardware propietario.*