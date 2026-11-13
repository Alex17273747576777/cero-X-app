#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Baja las fuentes de referencia del objetivo todo-en-uno (docs/OBJETIVO-TODO-EN-UNO.md).
#
# Por qué es un script y no un estado guardado: /var/tmp y /opt/cerox NO sobreviven al
# reinicio del sandbox. El 2026-11-13 un reinicio a mitad de turno borró /opt/cerox, el
# SDK de Android, el JDK 21 (quedó 11), el swap y los bits de ejecución de 26 ficheros.
# Así que las referencias se re-bajan cuando hacen falta, y este script es idempotente:
# lo que ya está y coincide no se vuelve a clonar.
#
# Uso:
#   scripts/bajar-referencias.sh              # las 7 fuentes Kotlin/Java (las auditables)
#   scripts/bajar-referencias.sh --todas      # las 7 + las 4 Dart
#   scripts/bajar-referencias.sh --solo Kototoro-app/Kototoro
#   scripts/bajar-referencias.sh --listar     # qué bajaría, sin bajar nada
#
# Destino: /var/tmp/referencias (fuera del workspace; el presupuesto persistente es de
# 110 MiB / 8.500 ficheros y un solo clon, Kototoro, son 347 MB y 6.803 ficheros: meterlo
# en work/ rompió el margen el 2026-11-13).
# Evidencia: work/ci/frankie/referencias.tsv, una fila por fuente con su SHA y su tamaño.
set -euo pipefail

DESTINO="${REFERENCIAS_DESTINO:-/var/tmp/referencias}"
# La evidencia vive en work/ci/, que NO está dentro de este repo (está en ~/work/ci, al
# mismo nivel que ~/work/cero-X-app). No se calcula subiendo niveles desde $0: eso ya
# falló antes. Se fija con $HOME y se puede sobrescribir.
EVIDENCIA="${REFERENCIAS_EVIDENCIA:-$HOME/work/ci/frankie/referencias.tsv}"
# owner/repo:licencia:lenguaje  — las que se pueden auditar contra fuente y compilar mentalmente
KOTLIN=(
  "Kototoro-app/Kototoro:Apache-2.0:Kotlin"
  "salmanbappi/AniZen:Apache-2.0:Kotlin"
  "unseensnick/Reikai:Apache-2.0:Kotlin"
  "andarcanum/Tadami-Aniyomi-fork:Apache-2.0:Kotlin"
  "Noirero/Miyorare:GPL-3.0:Kotlin"
  "Arthurpendragon9876/DeepSync:GPL-3.0:Kotlin"
  "AnantPlay/android:MIT:Java"
)
# Lectores de manga candidatos a base del modelo de fuentes (añadidos el 2026-11-13 a
# petición del dueño, que preguntó por kisara frente a ShinKu + houri). Los tres son
# Kotlin y Apache-2.0, así que copiables a GPL-3.0 conservando NOTICE:
#   ifritraen/kisara       — fork de Komikku, se declara WIP y no listo para uso público
#   Harrys-HQ/ShinKu       — fork de TachiyomiSY/Mihon
#   PineappleTwilight/houri— fork de komikku-app/komikku (★5.019) según la API
LECTORES=(
  "ifritraen/kisara:Apache-2.0:Kotlin"
  "Harrys-HQ/ShinKu:Apache-2.0:Kotlin"
  "PineappleTwilight/houri:Apache-2.0:Kotlin"
)
# Dart: no se portan ni se compilan aquí. Se leen; y para leer un fichero suelto no hace
# falta el clon (la API de GitHub lo sirve). Se bajan sólo con --todas.
DART=(
  "kodjodevf/mangayomi:Apache-2.0:Dart"
  "RyanYuuki/AnymeX:MIT:Dart"
  "ferelking242/watchtower:Apache-2.0:Dart"
  "Foxlations/Foxlations:Apache-2.0:Dart"
)
# Nahiro18/Kiroku NO está: su licencia es una "Unabandon Public License" no reconocida por
# SPDX y combinar código bajo una licencia personalizada es decisión del dueño. Mientras
# no la tome, Kiroku se lee por la API como referencia de producto y no se clona.

TODAS=0 SOLO="" LISTAR=0
while [ $# -gt 0 ]; do
  case "$1" in
    --todas) TODAS=1 ;;
    --solo)  SOLO="${2:-}"; shift ;;
    --listar) LISTAR=1 ;;
    *) echo "argumento no reconocido: $1" >&2; exit 2 ;;
  esac
  shift
done

lista=("${KOTLIN[@]}" "${LECTORES[@]}")
[ "$TODAS" = 1 ] && lista+=("${DART[@]}")   # LECTORES ya va en la lista por defecto

if [ -n "$SOLO" ]; then
  filtro=()
  for e in "${KOTLIN[@]}" "${LECTORES[@]}" "${DART[@]}"; do [ "${e%%:*}" = "$SOLO" ] && filtro+=("$e"); done
  [ ${#filtro[@]} -eq 0 ] && { echo "no conozco la fuente '$SOLO'" >&2; exit 2; }
  lista=("${filtro[@]}")
fi

echo "destino: $DESTINO"
echo "evidencia: $EVIDENCIA"
libres=$(df -m --output=avail / | tail -1 | tr -d ' ')
echo "disco libre: ${libres} MiB"

if [ "$LISTAR" = 1 ]; then
  printf '  %-34s %-12s %s\n' FUENTE LICENCIA LENGUAJE
  for e in "${lista[@]}"; do
    printf '  %-34s %-12s %s\n' "${e%%:*}" "$(echo "$e" | cut -d: -f2)" "$(echo "$e" | cut -d: -f3)"
  done
  echo "  (Kiroku excluido a propósito: licencia UPL, decisión del dueño pendiente)"
  exit 0
fi

mkdir -p "$DESTINO" "$(dirname "$EVIDENCIA")"
tmp="${EVIDENCIA}.nueva"
printf 'fuente\tlicencia\tlenguaje\tsha\tmb\tficheros\testado\n' > "$tmp"
ok=0; fallidos=0

for e in "${lista[@]}"; do
  repo="${e%%:*}"; lic="$(echo "$e" | cut -d: -f2)"; leng="$(echo "$e" | cut -d: -f3)"
  nombre="$(basename "$repo")"
  ruta="$DESTINO/$nombre"
  if [ -d "$ruta/.git" ]; then
    sha=$(git -C "$ruta" rev-parse HEAD 2>/dev/null || echo SIN_SHA)
    mb=$(du -sm "$ruta" | cut -f1)
    fich=$(find "$ruta" -type f | wc -l | tr -d ' ')
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$repo" "$lic" "$leng" "$sha" "$mb" "$fich" "ya-estaba" >> "$tmp"
    echo "  ya estaba  $repo ($mb MB, $fich ficheros, ${sha:0:7})"
    ok=$((ok+1)); continue
  fi
  # margen: no empezar un clon si quedan menos de 800 MB libres
  libres=$(df -m --output=avail / | tail -1 | tr -d ' ')
  if [ "$libres" -lt 800 ]; then
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$repo" "$lic" "$leng" "-" "-" "-" "sin-disco" >> "$tmp"
    echo "  SIN DISCO  $repo: quedan ${libres} MiB y el margen mínimo es 800" >&2
    fallidos=$((fallidos+1)); continue
  fi
  echo "  clonando   $repo …"
  if git clone --depth 1 --single-branch --quiet "https://github.com/$repo.git" "$ruta" 2>/dev/null; then
    sha=$(git -C "$ruta" rev-parse HEAD)
    mb=$(du -sm "$ruta" | cut -f1)
    fich=$(find "$ruta" -type f | wc -l | tr -d ' ')
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$repo" "$lic" "$leng" "$sha" "$mb" "$fich" "clonado" >> "$tmp"
    echo "             $mb MB, $fich ficheros, ${sha:0:7}"
    ok=$((ok+1))
  else
    rm -rf "$ruta"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$repo" "$lic" "$leng" "-" "-" "-" "FALLO" >> "$tmp"
    echo "             FALLO al clonar (¿repo movido? la API dijo Moved Permanently para dos de estas URLs)" >&2
    fallidos=$((fallidos+1))
  fi
done

# la evidencia sólo se reemplaza si se pudo escribir entera
mv "$tmp" "$EVIDENCIA"
echo
echo "referencias: $ok listas, $fallidos fallidas"
echo "disco libre tras bajar: $(df -h --output=avail / | tail -1 | tr -d ' ')"
[ "$fallidos" -eq 0 ] || { echo "REFERENCIAS_INCOMPLETAS: $fallidos fuente(s) no están" >&2; exit 1; }
echo "REFERENCIAS_OK"
