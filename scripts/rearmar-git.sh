#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 La Bandita
# SPDX-License-Identifier: GPL-3.0-only
#
# Rearma la capa de Git que el sandbox pierde en cada reinicio (regla 11 de AGENTS.md).
#
# POR QUÉ EXISTE
#   `.git/config` está excluido del snapshot del workspace, así que tras un reinicio el
#   repo amanece sin remoto, sin `credential.helper` y sin identidad, y los ficheros
#   versionados pierden el bit de ejecución. El 2026-11-13 eso se arregló a mano con seis
#   órdenes, y una de ellas salió mal: se buscaron los modos perdidos con
#   `git status --porcelain`, que NO los muestra —sólo `git diff --summary` los muestra—,
#   así que el bucle no arregló nada y seis ficheros siguieron sin bit de ejecución.
#   Este script existe para que el rearme sea una orden y no seis oportunidades de
#   equivocarse, y para que la detección de modos use el comando que sí los ve.
#
# QUÉ HACE, en este orden
#   1. bits de ejecución perdidos → chmod +x (nunca se commitean modos)
#   2. permisos de las credenciales → tokens.txt 0600, helper y carpeta 0700
#      (el reinicio del 2026-11-13 los dejó en 644: legibles por cualquier usuario)
#   3. credential.helper → lo fija, y si el fichero no existe lo recrea; el helper no
#      contiene ningún secreto, lo lee de ~/.cerox-keys/tokens.txt cuando Git lo pide
#   4. identidad Git → del autor de HEAD, para que el commit nuevo siga la misma línea
#   5. remoto origin → si falta, lo deduce de los docs del repo o del parámetro; jamás
#      con un token dentro de la URL
#   6. fetch --tags con GIT_TERMINAL_PROMPT=0 (si pide contraseña, falla en vez de colgar)
#   7. upstream de la rama actual
#
# QUÉ NO HACE
#   · No conmuta ramas, no fusiona, no empuja, no commitea y no toca `main`.
#   · No borra nada: si algo ya está bien, no lo cambia (es idempotente).
#   · No decide por ti cuando hace falta una decisión: si no puede deducir la URL del
#     remoto, lo dice y sale 1 en vez de inventar un remoto.
#   · No imprime ningún secreto. Donde hace falta la clave, se pasa por el helper.
#
# USO
#   scripts/rearmar-git.sh                                  # deduce todo lo que puede
#   scripts/rearmar-git.sh https://github.com/dueno/repo.git
#   CEROX_REPO_URL=... scripts/rearmar-git.sh
#
# SALIDAS
#   0  quedó armado (o ya lo estaba)
#   1  falta algo que requiere decisión humana; se imprime qué
#   2  esto no es un repo Git
set -euo pipefail

CLAVES="${CEROX_CLAVES:-/home/user/.cerox-keys/tokens.txt}"
HELPER="${CEROX_HELPER:-/home/user/.gh-credential}"
HECHOS=()
FALTAN=()

anotar() { HECHOS+=("$1"); printf '  hecho    %s\n' "$1"; }
ya()     { printf '  ya       %s\n' "$1"; }
faltar() { FALTAN+=("$1"); printf '  FALTA    %s\n' "$1"; }

git rev-parse --show-toplevel >/dev/null 2>&1 || {
    echo "ERROR esto no es un repo Git (busca .git hacia arriba desde $(pwd))" >&2; exit 2; }
RAIZ="$(git rev-parse --show-toplevel)"
cd "$RAIZ"
echo "rearme de Git en $RAIZ"

# 1) Bits de ejecución. `git status --porcelain` no muestra cambios de modo; el que los
#    muestra es `git diff --summary`. Se restauran en el árbol y no se commitean: el modo
#    correcto ya está en el índice, lo que se perdió fue el bit del sandbox.
MODOS="$(git diff --summary | awk '/mode change 100755 => 100644/ {$1=$2=$3=$4=$5=""; sub(/^ +/, ""); print}')"
if [ -n "$MODOS" ]; then
    n=0
    while IFS= read -r fichero; do
        [ -n "$fichero" ] || continue
        chmod +x "$fichero" && n=$((n + 1))
    done <<EOF
$MODOS
EOF
    anotar "$n fichero(s) recuperaron el bit de ejecución"
else
    ya "bits de ejecución (ningún modo cambiado)"
fi
RESTAN="$(git diff --summary | grep 'mode change' || true)"
if [ -n "$RESTAN" ]; then
    faltar "quedan cambios de modo: $(printf '%s' "$RESTAN" | head -3 | tr '\n' ';')"
fi

# 2) Permisos de las credenciales.
if [ -f "$CLAVES" ]; then
    antes="$(stat -c%a "$CLAVES")"
    chmod 600 "$CLAVES"; chmod 700 "$(dirname "$CLAVES")" 2>/dev/null || true
    [ "$antes" = "600" ] && ya "permisos de $(basename "$CLAVES") (600)" \
                         || anotar "$(basename "$CLAVES") de $antes a 600 (y su carpeta a 700)"
else
    faltar "no existe $CLAVES: sin clave no hay push. Hay que volver a entregarla."
fi

# 3) Credential helper: se fija siempre; se recrea si el fichero no está.
if [ ! -f "$HELPER" ]; then
    cat > "$HELPER" <<'HELPEREOF'
#!/bin/sh
# Credential helper de Git. No contiene ningún secreto: lee la clave del único
# archivo que la tiene, /home/user/.cerox-keys/tokens.txt (modo 0600), y la
# devuelve sólo cuando Git la pide. Así el token no acaba en .git/config ni en
# la URL del remoto, que es lo que docs/POLITICA-DE-ALMACENAMIENTO.md prohíbe.
case "$1" in
  get) printf 'username=x-access-token\npassword=%s\n' \
         "$(grep -E '^GP=' /home/user/.cerox-keys/tokens.txt | head -1 | cut -d= -f2- | tr -d '\r\n')" ;;
esac
HELPEREOF
    anotar "recreado $HELPER (no contiene secretos: los lee de tokens.txt)"
fi
chmod 700 "$HELPER"
if [ ! -x "$HELPER" ]; then chmod +x "$HELPER"; anotar "$HELPER recuperó el bit de ejecución"; fi
if [ "$(git config --get credential.helper || true)" != "$HELPER" ]; then
    git config credential.helper "$HELPER"
    anotar "credential.helper → $HELPER"
else
    ya "credential.helper ($HELPER)"
fi

# 4) Identidad: la del autor de HEAD, para no inventar una ni mezclar líneas de trabajo.
if [ -z "$(git config --get user.name || true)" ] || [ -z "$(git config --get user.email || true)" ]; then
    NOMBRE="$(git log -1 --format='%an' 2>/dev/null || true)"
    CORREO="$(git log -1 --format='%ae' 2>/dev/null || true)"
    if [ -n "$NOMBRE" ] && [ -n "$CORREO" ]; then
        git config user.name "$NOMBRE"; git config user.email "$CORREO"
        anotar "identidad del autor de HEAD: $NOMBRE <$CORREO>"
    else
        faltar "sin identidad y sin historial del que deducirla: git config user.name/user.email"
    fi
else
    ya "identidad ($(git config --get user.name))"
fi

# 5) Remoto. Si falta, se deduce: primero del entorno o del parámetro, después de los
#    docs del repo. Nunca se escribe un token en la URL, y se comprueba después.
if [ -z "$(git remote || true)" ]; then
    URL="${1:-${CEROX_REPO_URL:-}}"
    if [ -z "$URL" ]; then
        CANDIDATOS="$(grep -rhoE 'github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+' \
            docs AGENTS.md README.md 2>/dev/null | sort -u || true)"
        UNICOS="$(printf '%s\n' "$CANDIDATOS" | grep -c . || true)"
        if [ "${UNICOS:-0}" = "1" ]; then
            URL="https://$(printf '%s\n' "$CANDIDATOS" | head -1).git"
            anotar "URL deducida de los docs: $URL"
        elif [ "${UNICOS:-0}" = "0" ]; then
            faltar "sin remoto y ninguna URL en los docs; pasa la URL como parámetro"
        else
            faltar "sin remoto y $UNICOS URLs distintas en los docs; no se elige sola:"
            printf '%s\n' "$CANDIDATOS" | sed 's/^/             /'
        fi
    fi
    if [ -n "${URL:-}" ]; then
        git remote add origin "$URL"
        anotar "remote add origin $URL"
    fi
else
    ya "remoto $(git remote | tr '\n' ' ')"
fi
if [ -n "$(git remote || true)" ]; then
    if git config --get remote.origin.url | grep -qE '://[^/]*@|github_pat|ghp_'; then
        faltar "la URL del remoto lleva credencial dentro: reescribirla sin token"
    else
        ya "URL del remoto sin token embebido"
    fi
fi

# 6) Fetch. GIT_TERMINAL_PROMPT=0 para que una credencial rota falle en segundos en vez
#    de dejar la orden colgada esperando una contraseña que nadie va a teclear.
if [ -n "$(git remote || true)" ]; then
    if GIT_TERMINAL_PROMPT=0 git fetch --quiet origin --tags 2>/tmp/rearmar-fetch.$$; then
        anotar "fetch --tags de $(git remote | tr '\n' ' ') ($(git branch -r | wc -l) ramas remotas)"
    else
        faltar "el fetch falló: $(head -2 /tmp/rearmar-fetch.$$ | tr '\n' ' ')"
    fi
    rm -f /tmp/rearmar-fetch.$$

    # 7) Upstream de la rama actual, si la rama existe en el remoto.
    RAMA="$(git rev-parse --abbrev-ref HEAD)"
    if [ "$RAMA" != "HEAD" ]; then
        if git rev-parse --abbrev-ref '@{upstream}' >/dev/null 2>&1; then
            ya "upstream de $RAMA ($(git rev-parse --abbrev-ref '@{upstream}'))"
        elif git show-ref --verify --quiet "refs/remotes/origin/$RAMA"; then
            git branch --set-upstream-to="origin/$RAMA" "$RAMA" >/dev/null
            anotar "upstream de $RAMA → origin/$RAMA"
        else
            printf '  aviso    %s no existe en el remoto: el primer push necesita -u\n' "$RAMA"
        fi
    fi
fi

echo
echo "  tokens en .git/config: $(grep -cE 'github_pat|ghp_|x-access-token' .git/config || true)"
if [ "${#FALTAN[@]}" -ne 0 ]; then
    echo "REARME_INCOMPLETO: ${#FALTAN[@]} cosa(s) requieren decisión"
    for f in "${FALTAN[@]}"; do echo "  - $f"; done
    exit 1
fi
echo "REARME_OK: ${#HECHOS[@]} acción(es) nuevas, remoto y credenciales listos"
