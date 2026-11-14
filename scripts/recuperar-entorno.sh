#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 La Bandita
# SPDX-License-Identifier: GPL-3.0-only
#
# Recuperación de UNA sola orden tras un reinicio del sandbox.
#
# POR QUÉ EXISTE
#   El sandbox se reinicia y pierde de golpe: `.git/config` de TODOS los repos (remoto,
#   credential.helper, identidad, upstream), los bits de ejecución de los ficheros
#   versionados, el modo 0600 de ~/.cerox-keys/tokens.txt (lo deja en 644, legible por
#   cualquiera), el toolchain (JDK 21, Android SDK) y el swap. El 2026-11-14 eso se
#   recuperó a mano en unas diez órdenes y una salió mal: se incrustó el token en la URL
#   del remoto, una fuga en un repo público que latido tuvo que señalar. Además es fácil
#   rearmar un repo y olvidarse del otro (falla C11 del registro común). Este script
#   convierte la recuperación en UNA orden idempotente que rearma los DOS repos y el
#   entorno, y verifica al final midiendo, no suponiendo.
#
# QUÉ HACE, en este orden
#   1. firma del reinicio → mide qué se perdió (java, swap, tokens.txt, remoto de cada repo)
#   2. rearmar-git.sh en CADA repo de la lista, pasándole su URL del mapa de remotos
#   3. preparar-entorno-android.sh (idempotente: si el toolchain ya está, pasa rápido)
#   4. verificación: comprobar_entorno.py (ENTORNO_OK), auth REAL (git ls-remote) en cada
#      repo, y resumen de latido.py del repo principal
#   5. veredicto único: RECUPERADO_OK / RECUPERADO_INCOMPLETO, con lo que falta impreso
#
# QUÉ NO HACE
#   · No commitea, no empuja, no conmuta ramas, no fusiona, no toca `main`, no borra nada.
#   · No imprime ningún secreto: las URLs son públicas y la clave sólo la usa el helper.
#   · No decide por ti: si un repo no puede resolver su remoto, lo reporta y sigue con el
#     siguiente; el veredicto final refleja lo que quedó sin armar.
#
# USO
#   scripts/recuperar-entorno.sh                 # rearma Git (todos los repos) + entorno
#   scripts/recuperar-entorno.sh --sin-entorno   # sólo la capa de Git (toolchain intacto)
#   CEROX_REPOS="/ruta/a /ruta/b" scripts/recuperar-entorno.sh
#
# SALIDAS
#   0  RECUPERADO_OK
#   1  RECUPERADO_INCOMPLETO (se imprime qué falta; cada falta requiere decisión o reintento)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPOS="${CEROX_REPOS:-/home/user/work/cero-X-app /home/user/work/edt}"
PRINCIPAL="${CEROX_PRINCIPAL:-/home/user/work/cero-X-app}"
CLAVES="${CEROX_CLAVES:-/home/user/.cerox-keys/tokens.txt}"
MAPA_REMOTOS="${CEROX_MAPA_REMOTOS:-/home/user/.cerox-keys/remotos.txt}"
CON_ENTORNO=1
[ "${1:-}" = "--sin-entorno" ] && CON_ENTORNO=0

FALTAN=()
HECHOS=()
anotar() { HECHOS+=("$1"); printf '  hecho    %s\n' "$1"; }
faltar() { FALTAN+=("$1"); printf '  FALTA    %s\n' "$1"; }
info()   { printf '  ·        %s\n' "$1"; }

url_de() {  # $1 = ruta del repo → URL del mapa, o vacío
    [ -f "$MAPA_REMOTOS" ] || { printf ''; return 0; }
    awk -F'\t' -v p="$1" '$1==p {print $2; exit}' "$MAPA_REMOTOS"
}

echo "══════ 1. firma del reinicio (medida, no supuesta) ══════"
java_actual="$(java -version 2>&1 | head -1 || echo 'sin java')"
info "java: $java_actual"
info "swap: $(swapon --show=NAME --noheadings 2>/dev/null | tr '\n' ' ' || true)$(grep SwapTotal /proc/meminfo | awk '{printf " (%.0f GiB)", $2/1024/1024}')"
info "disco libre: $(df --output=avail -BG / | tail -1 | tr -d ' ')"
if [ -f "$CLAVES" ]; then
    modo_claves="$(stat -c%a "$CLAVES")"
    info "tokens.txt modo $modo_claves $([ "$modo_claves" = 600 ] && echo '(correcto)' || echo '(EXPUESTO: el reinicio lo dejó legible; rearmar-git.sh lo pone a 600)')"
else
    faltar "no existe $CLAVES: sin clave no hay push; hay que volver a entregarla"
fi
for repo in $REPOS; do
    if [ -d "$repo/.git" ] || (cd "$repo" 2>/dev/null && git rev-parse --show-toplevel >/dev/null 2>&1); then
        remoto="$(cd "$repo" && git remote get-url origin 2>/dev/null || echo '<vacío>')"
        helper="$(cd "$repo" && git config --get credential.helper 2>/dev/null || echo '<vacío>')"
        modos="$(cd "$repo" && git diff --summary 2>/dev/null | grep -c 'mode change' || true)"
        info "$(basename "$repo"): remoto=$remoto · helper=$helper · modos_perdidos=$modos"
    else
        info "$(basename "$repo"): no es un repo Git (se omite)"
    fi
done

echo
echo "══════ 2. rearmar Git en cada repo (rearmar-git.sh, idempotente) ══════"
for repo in $REPOS; do
    [ -d "$repo" ] || { faltar "repo ausente: $repo"; continue; }
    if ! (cd "$repo" && git rev-parse --show-toplevel >/dev/null 2>&1); then
        info "$(basename "$repo"): no es un repo Git, se omite"; continue
    fi
    url="$(url_de "$repo")"
    echo "  --- $(basename "$repo") ${url:+(URL del mapa)} ---"
    set +e
    if [ -n "$url" ]; then
        (cd "$repo" && bash "$ROOT/scripts/rearmar-git.sh" "$url")
    else
        (cd "$repo" && bash "$ROOT/scripts/rearmar-git.sh")
    fi
    rc=$?
    set -e
    if [ "$rc" = 0 ]; then
        anotar "$(basename "$repo"): REARME_OK"
    else
        faltar "$(basename "$repo"): rearmar-git.sh salió $rc (ver arriba qué requiere decisión)"
    fi
done

if [ "$CON_ENTORNO" = 1 ]; then
    echo
    echo "══════ 3. rearmar el toolchain (preparar-entorno-android.sh) ══════"
    set +e
    bash "$ROOT/scripts/preparar-entorno-android.sh"
    rc=$?
    set -e
    [ "$rc" = 0 ] && anotar "toolchain: preparar-entorno-android.sh salió 0" \
                  || faltar "toolchain: preparar-entorno-android.sh salió $rc"
else
    info "--sin-entorno: se omite el toolchain"
fi

echo
echo "══════ 4. verificación (medida) ══════"
# 4a. ENTORNO_OK
if [ "$CON_ENTORNO" = 1 ] || command -v sdkmanager >/dev/null 2>&1 || [ -x /usr/lib/jvm/java-21-openjdk-amd64/bin/java ]; then
    if python3 "$ROOT/tools/comprobar_entorno.py" 2>/dev/null | grep -q '^ENTORNO_OK'; then
        anotar "comprobar_entorno.py: ENTORNO_OK"
    else
        faltar "comprobar_entorno.py no dio ENTORNO_OK"
    fi
fi
# 4b. auth REAL en cada repo (git ls-remote): caza un helper roto o un token vencido aquí,
#     no en el primer push. No imprime la URL con credencial (no la hay: el token va por helper).
for repo in $REPOS; do
    (cd "$repo" 2>/dev/null && git rev-parse --show-toplevel >/dev/null 2>&1) || continue
    if (cd "$repo" && GIT_TERMINAL_PROMPT=0 git ls-remote --heads origin >/dev/null 2>&1); then
        anotar "$(basename "$repo"): auth contra el remoto OK (ls-remote)"
    else
        faltar "$(basename "$repo"): ls-remote falló (helper roto, token vencido o remoto mal)"
    fi
done
# 4c. tokens.txt a 600 tras el rearme
if [ -f "$CLAVES" ] && [ "$(stat -c%a "$CLAVES")" = 600 ]; then
    anotar "tokens.txt quedó en 0600"
elif [ -f "$CLAVES" ]; then
    faltar "tokens.txt sigue en $(stat -c%a "$CLAVES"), no en 0600"
fi
# 4d. latido del repo principal (informativo: la recuperación puede dejar árbol sucio con
#     sus propias ediciones; el veredicto de latido se atiende antes de commitear, no aquí).
if [ -d "$PRINCIPAL" ] && [ -f "$PRINCIPAL/tools/latido.py" ]; then
    resumen="$(cd "$PRINCIPAL" && python3 tools/latido.py 2>&1 | grep -E 'HEARTBEAT|bloqueos=' | head -1 || true)"
    info "latido ($PRINCIPAL): ${resumen:-sin salida}"
fi

echo
if [ "${#FALTAN[@]}" -ne 0 ]; then
    echo "RECUPERADO_INCOMPLETO: ${#FALTAN[@]} cosa(s) por resolver (${#HECHOS[@]} hechas)"
    for f in "${FALTAN[@]}"; do echo "  - $f"; done
    exit 1
fi
echo "RECUPERADO_OK: ${#HECHOS[@]} comprobaciones en verde · Git (todos los repos), toolchain y permisos listos"
