#!/usr/bin/env bash
# setup.sh — Configuración inicial de cero-X App
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "Configurando cero-X App..."

# Clonar ecosistemas de referencia
echo "Clonando ecosistemas de referencia..."

# Miyomi / Mihon software catalog (documentación de referencia)
if [ ! -d "${PROJECT_DIR}/assets/miyomi-catalog" ]; then
    echo "  - Documentación de Miyomi/Mihon (referencia solo)"
    mkdir -p "${PROJECT_DIR}/assets/miyomi-catalog"
fi

# Wotaku (referencia de índice otaku)
if [ ! -d "${PROJECT_DIR}/assets/wotaku-index" ]; then
    echo "  - Documentación de Wotaku (referencia solo)"
    mkdir -p "${PROJECT_DIR}/assets/wotaku-index"
fi

# Kisara / ifritraen (referencia de fork con características)
if [ ! -d "${PROJECT_DIR}/assets/kisara-reference" ]; then
    echo "  - Documentación de Kisara (referencia solo)"
    mkdir -p "${PROJECT_DIR}/assets/kisara-reference"
fi

echo ""
echo "Configuración completada."
echo "Revise config/settings.yaml para ajustar los módulos habilitados."