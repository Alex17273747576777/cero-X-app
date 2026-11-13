#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 La Bandita
# SPDX-License-Identifier: GPL-3.0-only
# Entorno mínimo y reproducible; no instala emulador, imágenes de Android ni modelos.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/.local/share/cerox-android-sdk}"
DL="$HOME/.local/share/cerox-android-downloads"
CACHE_ROOT="${CEROX_CACHE_ROOT:-/opt/cerox}"
export DEBIAN_FRONTEND=noninteractive
sudo mkdir -p "$CACHE_ROOT/gradle" "$CACHE_ROOT/m2"
sudo chown "$(id -u):$(id -g)" "$CACHE_ROOT" "$CACHE_ROOT/gradle" "$CACHE_ROOT/m2"

if ! /usr/lib/jvm/java-21-openjdk-amd64/bin/java -version >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y --no-install-recommends openjdk-21-jdk-headless curl unzip
fi
# La máquina de 2 GiB necesita swap para Gradle/R8; no se crea en equipos con más RAM.
# Con 3 GiB el cierre limpio (clean + suite + dos APK, R8 incluido) llegaba justo.
#
# Tamaño: el mayor entre 10 y 15 GiB que además deje SUELO GiB de disco libre.
# El suelo no es un número redondo al azar, es la suma medida de lo que hace
# falta después: toolchain ~3 GiB (JDK + /opt/cerox + SDK) + builds ~3 GiB
# (android/build y android/app/build con los dos APK) + los 3 GiB por debajo de
# los cuales tools/comprobar_entorno.py da ERROR.
#
# En el sandbox real (disco de 25 GiB, ~21 libres al rearmar) esto elige 12 GiB:
# con 15 GiB quedarían ~1-2 GiB libres en el pico del build y la auditoría de
# entorno fallaría. Más swap no acelera nada con 2 GiB de RAM: evita que el OOM
# killer se lleve el demonio de Gradle a mitad de R8, que es lo que se busca.
#
# zram no es alternativa aquí: el sandbox no tiene /dev/kvm ni modprobe, así que
# no se puede cargar el módulo, y con 1,9 GiB de RAM un zram útil sería de 2-4 GiB
# comprimidos que además consumen RAM al llenarse. Swap en disco es lo correcto.
SWAP_MIN_GIB=10
SWAP_MAX_GIB=15
SWAP_SUELO_LIBRE_GIB=9
SWAP_MINIMO_RESPALDO_GIB=5
SWAP_SUELO_RESPALDO_GIB=6
SWAP_ARCHIVO=/swapfile-cerox
if [[ "$(free -m | awk '/^Mem:/ {print $2}')" -lt 3072 ]] && \
   ! sudo swapon --show=NAME --noheadings | grep -qx "$SWAP_ARCHIVO"; then
  libres_gib=$(df --output=avail -BG / | tail -1 | tr -dc '0-9')
  libres_gib=${libres_gib:-0}
  objetivo_gib=0
  for candidato in $(seq "$SWAP_MAX_GIB" -1 "$SWAP_MIN_GIB"); do
    if (( libres_gib - candidato >= SWAP_SUELO_LIBRE_GIB )); then
      objetivo_gib=$candidato
      break
    fi
  done
  # Respaldo: si no cabe el rango pedido, mejor un swap pequeño que ninguno,
  # porque sin él R8 mata el demonio a mitad del cierre.
  if (( objetivo_gib == 0 )) && (( libres_gib - SWAP_MINIMO_RESPALDO_GIB >= SWAP_SUELO_RESPALDO_GIB )); then
    objetivo_gib=$SWAP_MINIMO_RESPALDO_GIB
    echo "AVISO swap: no caben ${SWAP_MIN_GIB}-${SWAP_MAX_GIB} GiB con ${libres_gib} GiB libres; se ponen ${objetivo_gib} GiB de respaldo." >&2
  fi
  if (( objetivo_gib == 0 )); then
    echo "AVISO swap: hay ${libres_gib} GiB libres en / y no cabe ni el respaldo de ${SWAP_MINIMO_RESPALDO_GIB} GiB. Se sigue sin swap: R8 puede morir por OOM." >&2
  else
    # Un archivo heredado de otro tamaño (los 3 GiB de antes) se recrea:
    # reutilizarlo dejaría el swap viejo para siempre sin que nada lo avisara.
    if [[ -f $SWAP_ARCHIVO ]]; then
      actual_gib=$(( $(sudo stat -c%s "$SWAP_ARCHIVO") / 1024 / 1024 / 1024 ))
      if (( actual_gib != objetivo_gib )); then
        echo "swap: se recrea $SWAP_ARCHIVO de ${actual_gib} GiB a ${objetivo_gib} GiB"
        sudo swapoff "$SWAP_ARCHIVO" 2>/dev/null || true
        sudo rm -f "$SWAP_ARCHIVO"
      fi
    fi
    if [[ ! -f $SWAP_ARCHIVO ]]; then
      sudo fallocate -l "${objetivo_gib}G" "$SWAP_ARCHIVO" || \
        sudo dd if=/dev/zero of="$SWAP_ARCHIVO" bs=1M count=$(( objetivo_gib * 1024 )) status=none
      sudo chmod 600 "$SWAP_ARCHIVO"
      sudo mkswap "$SWAP_ARCHIVO" >/dev/null
    fi
    sudo swapon "$SWAP_ARCHIVO"
    echo "swap: $SWAP_ARCHIVO activo con ${objetivo_gib} GiB (disco libre antes: ${libres_gib} GiB)"
  fi
fi

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME="$SDK"
export PATH="$JAVA_HOME/bin:$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:$PATH"

if [[ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]]; then
  mkdir -p "$SDK/cmdline-tools" "$DL"
  curl -fL --retry 3 \
    'https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip' \
    -o "$DL/android-cli.zip"
  rm -rf "$SDK/cmdline-tools/extract" "$SDK/cmdline-tools/latest"
  mkdir -p "$SDK/cmdline-tools/extract"
  unzip -q "$DL/android-cli.zip" -d "$SDK/cmdline-tools/extract"
  mv "$SDK/cmdline-tools/extract/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$SDK/cmdline-tools/extract" "$DL/android-cli.zip"
fi

yes | sdkmanager --licenses >/dev/null || true
sdkmanager 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0' 'build-tools;36.0.0'
chmod +x "$ROOT/android/gradlew"
python3 "$ROOT/tools/comprobar_entorno.py"
