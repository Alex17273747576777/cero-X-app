#!/bin/sh
# Verificar un APK descargado contra su huella.
#
# Por qué esto existe: en el ecosistema se publican cientos de MiB sin un solo
# número con el que comprobar que el archivo llegó entero. Con esto, cualquiera
# puede saber en dos segundos si su descarga está bien o se corrompió a medias.
#
#   sh como-verificar.sh ~/Descargas/cero-X-1.0.0-beta-453MiB-feed-ia.apk
set -e
fichero="${1:?uso: sh como-verificar.sh <archivo.apk>}"
cd "$(dirname "$0")"
if command -v sha256sum >/dev/null 2>&1; then
    grep " $(basename "$fichero")$" SHA256SUMS.txt | sha256sum -c -
else
    esperado=$(grep " $(basename "$fichero")$" SHA256SUMS.txt | cut -d' ' -f1)
    real=$(shasum -a 256 "$fichero" | cut -d' ' -f1)
    [ "$esperado" = "$real" ] && echo "$(basename "$fichero"): OK" || { echo "NO COINCIDE"; exit 1; }
fi
