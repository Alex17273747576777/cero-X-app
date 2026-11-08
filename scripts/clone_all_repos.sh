#!/usr/bin/env bash
# clone_all_repos.sh — Clona los 8 repositorios de referencia de cero-X
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ECOSYSTEMS_DIR="${PROJECT_DIR}/android/assets/ecosystems"

mkdir -p "${ECOSYSTEMS_DIR}"

echo "========================================"
echo "  Clonando ecosistemas de referencia para cero-X"
echo "========================================"
echo ""

# 1. Houbi (PineappleTwilight/houri) — LECTOR BASE PRINCIPAL
echo "[1/8] Clonando Houbi (PineappleTwilight/houri)..."
if [ ! -d "${ECOSYSTEMS_DIR}/houbi" ]; then
    git clone --depth 1 https://github.com/PineappleTwilight/houri.git "${ECOSYSTEMS_DIR}/houbi"
    echo "  ✓ Houbi clonado"
else
    echo "  ✓ Houbi ya existe (omitiendo)"
fi

# 2. Mihon (mihonapp/mihon) — Base de Houbi y muchos forks
echo "[2/8] Clonando Mihon (mihonapp/mihon)..."
if [ ! -d "${ECOSYSTEMS_DIR}/mihon" ]; then
    git clone --depth 1 https://github.com/mihonapp/mihon.git "${ECOSYSTEMS_DIR}/mihon"
    echo "  ✓ Mihon clonado"
else
    echo "  ✓ Mihon ya existe (omitiendo)"
fi

# 3. Foxlations (Foxlations/Foxlations)
echo "[3/8] Clonando Foxlations (Foxlations/Foxlations)..."
if [ ! -d "${ECOSYSTEMS_DIR}/foxlations" ]; then
    git clone --depth 1 https://github.com/Foxlations/Foxlations.git "${ECOSYSTEMS_DIR}/foxlations"
    echo "  ✓ Foxlations clonado"
else
    echo "  ✓ Foxlations ya existe (omitiendo)"
fi

# 4. Kototoro (fork de Kotatsu/Tachiyomi)
echo "[4/8] Clonando Kototoro (fork de Kotatsu)..."
if [ ! -d "${ECOSYSTEMS_DIR}/kototoro" ]; then
    git clone --depth 1 https://github.com/KotatsuApp/kotatsu.git "${ECOSYSTEMS_DIR}/kototoro"
    echo "  ✓ Kototoro/Kotatsu clonado"
else
    echo "  ✓ Kototoro/Kotatsu ya existe (omitiendo)"
fi

# 5. Kisara (ifritraen/kisara)
echo "[5/8] Clonando Kisara (ifritraen/kisara)..."
if [ ! -d "${ECOSYSTEMS_DIR}/kisara" ]; then
    git clone --depth 1 https://github.com/ifritraen/kisara.git "${ECOSYSTEMS_DIR}/kisara"
    echo "  ✓ Kisara clonado"
else
    echo "  ✓ Kisara ya existe (omitiendo)"
fi

# 6. Reikai (unseensnick/Reikai)
echo "[6/8] Clonando Reikai (unseensnick/Reikai)..."
if [ ! -d "${ECOSYSTEMS_DIR}/reikai" ]; then
    git clone --depth 1 https://github.com/unseensnick/Reikai.git "${ECOSYSTEMS_DIR}/reikai"
    echo "  ✓ Reikai clonado"
else
    echo "  ✓ Reikai ya existe (omitiendo)"
fi

# 7. Echo-Nightly (itsmechinmoy/echo-nightly)
echo "[7/8] Clonando Echo-Nightly (itsmechinmoy/echo-nightly)..."
if [ ! -d "${ECOSYSTEMS_DIR}/echo-nightly" ]; then
    git clone --depth 1 https://github.com/itsmechinmoy/echo-nightly.git "${ECOSYSTEMS_DIR}/echo-nightly"
    echo "  ✓ Echo-Nightly clonado"
else
    echo "  ✓ Echo-Nightly ya existe (omitiendo)"
fi

# 8. Namida (namidaco/namida)
echo "[8/8] Clonando Namida (namidaco/namida)..."
if [ ! -d "${ECOSYSTEMS_DIR}/namida" ]; then
    git clone --depth 1 https://github.com/namidaco/namida.git "${ECOSYSTEMS_DIR}/namida"
    echo "  ✓ Namida clonado"
else
    echo "  ✓ Namida ya existe (omitiendo)"
fi

echo ""
echo "========================================"
echo "  Clonación completada"
echo "========================================"
echo ""
echo "Ecosistemas clonados en: ${ECOSYSTEMS_DIR}"
ls -la "${ECOSYSTEMS_DIR}"