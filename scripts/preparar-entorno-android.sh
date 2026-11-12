#!/usr/bin/env bash
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
if [[ "$(free -m | awk '/^Mem:/ {print $2}')" -lt 3072 ]] && \
   ! sudo swapon --show=NAME --noheadings | grep -qx '/swapfile-cerox'; then
  if [[ ! -f /swapfile-cerox ]]; then
    sudo fallocate -l 3G /swapfile-cerox
    sudo chmod 600 /swapfile-cerox
    sudo mkswap /swapfile-cerox
  fi
  sudo swapon /swapfile-cerox
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
