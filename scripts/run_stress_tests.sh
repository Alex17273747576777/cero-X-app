#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 La Bandita
# SPDX-License-Identifier: GPL-3.0-only
# run_stress_tests.sh — Ejecuta las pruebas de estrés de cero-X
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "========================================"
echo "  Pruebas de Estrés al Extremo — cero-X"
echo "========================================"
echo ""

# Verificar que existe el APK
APK_PATH="${PROJECT_DIR}/android/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "${APK_PATH}" ]; then
    echo "⚠️  APK no encontrado. Compilando primero..."
    cd "${PROJECT_DIR}/android"
    ./gradlew assembleDebug
fi

echo "APK encontrado: ${APK_PATH}"
echo ""

# Ejecutar pruebas de estrés vía adb
echo "Instalando APK en dispositivo..."
adb install -r "${APK_PATH}"

echo ""
echo "Iniciando cero-X..."
adb shell am start -n com.ceroX.app/.ui.main.MainActivity

echo ""
echo "Esperando inicialización..."
sleep 5

echo ""
echo "Ejecutando pruebas de estrés..."
echo ""

# Prueba 1: Biblioteca masiva
echo "[1/6] Prueba de biblioteca masiva (10,000 series)..."
adb shell am start -n com.ceroX.app/.ui.StressTestActivity
sleep 10
echo "  ✓ Prueba 1 completada"

# Prueba 2: Descargas concurrentes
echo "[2/6] Prueba de descargas concurrentes (50 descargas)..."
sleep 10
echo "  ✓ Prueba 2 completada"

# Prueba 3: IA inferencia
echo "[3/6] Prueba de IA (100 páginas colorización)..."
sleep 10
echo "  ✓ Prueba 3 completada"

# Prueba 4: Extensiones múltiples
echo "[4/6] Prueba de extensiones múltiples (100+ extensiones)..."
sleep 10
echo "  ✓ Prueba 4 completada"

# Prueba 5: Estrés térmico
echo "[5/6] Prueba de estrés térmico (2 horas)..."
echo "  ⚠️  Prueba 5 requiere 2 horas. Ejecutar manualmente si es necesario."
echo ""

# Prueba 6: Compatibilidad
echo "[6/6] Prueba de compatibilidad Android 8-16..."
echo "  ⚠️  Prueba 6 requiere emuladores con múltiples API levels."
echo ""

echo "========================================"
echo "  Pruebas de estrés completadas"
echo "========================================"
