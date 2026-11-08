#!/usr/bin/env bash
# install.sh — Script de instalación de cero-X App
set -euo pipefail

echo "========================================"
echo "  Instalando cero-X App v0.1.0"
echo "======================================="

# Detectar directorio del script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Verificar Python
if ! command -v python3 &>/dev/null; then
    echo "ERROR: Python 3 no encontrado. Instale Python 3.10+ primero."
    exit 1
fi

PYTHON_VERSION=$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')
echo "Python detectado: ${PYTHON_VERSION}"

# Crear entorno virtual
VENV_DIR="${PROJECT_DIR}/.venv"
if [ ! -d "${VENV_DIR}" ]; then
    echo "Creando entorno virtual en ${VENV_DIR}..."
    python3 -m venv "${VENV_DIR}"
fi

# Activar entorno virtual
# shellcheck source=/dev/null
source "${VENV_DIR}/bin/activate"

# Instalar dependencias
echo "Instalando dependencias de Python..."
pip install --upgrade pip
pip install -r "${PROJECT_DIR}/requirements.txt"

# Crear directorios necesarios
mkdir -p "${PROJECT_DIR}/logs"
mkdir -p "${PROJECT_DIR}/downloads"
mkdir -p "${PROJECT_DIR}/data"

echo ""
echo "========================================"
echo "  Instalación completada"
echo "========================================"
echo ""
echo "Para ejecutar cero-X:"
echo "  source ${VENV_DIR}/bin/activate"
echo "  cd ${PROJECT_DIR}"
echo "  python -m ceroX"
echo ""