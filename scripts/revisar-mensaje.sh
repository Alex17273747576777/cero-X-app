#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 La Bandita
# SPDX-License-Identifier: GPL-3.0-only
#
# Revisa el MENSAJE de un commit ANTES de commitarlo.
#
# POR QUÉ EXISTE (falla C14 del registro común)
#   El barrido de pre-commit de la casa miraba el `git diff` (el contenido de los ficheros)
#   pero NO el mensaje del commit. El 2026-11-14 un lapsus coló dos ideogramas chinos (rango
#   CJK, U+5000-U+5FFF) en el mensaje de «F3 paso 1», pegados a la palabra «refactor». Se cazó
#   al leer el commit ya hecho y se enmendó antes del push (el commit no estaba empujado, así
#   que `--amend` fue seguro y no hubo force-push a main). Pero el hueco era real: nada
#   comprobaba el mensaje. Este script lo cierra. Es la contraparte de C14 igual que
#   recuperar-entorno.sh lo es de C13.
#   Nota: los caracteres prohibidos NO se escriben aquí (ni en ningún doc) —se nombran por su
#   rango—, porque incrustarlos volvería a sembrar justo lo que este script prohíbe.
#
# QUÉ HACE
#   · Falla (sale 1) si el mensaje tiene caracteres de CJK, kana, Hangul, árabe o cirílico:
#     ésos NUNCA deben aparecer, ni en prosa ni en código, y no hay falso positivo posible.
#   · Avisa (sale 0) de posibles anglicismos/italianismos en la prosa: se revisan a ojo porque
#     las citas textuales en inglés (la descripción de un repo) y las palabras clave de código
#     (`for`, `with`) son falsos positivos legítimos.
#
# USO
#   scripts/revisar-mensaje.sh mensaje.txt        # revisa un fichero
#   echo "mensaje" | scripts/revisar-mensaje.sh    # revisa stdin (sin argumento)
#   # flujo seguro: escribir el mensaje a un fichero, revisarlo, y sólo entonces
#   # git commit -F mensaje.txt
#
# SALIDAS
#   0  limpio, o sólo con avisos de anglicismos
#   1  tiene algún caracter prohibido (CJK/kana/Hangul/árabe/cirílico): NO commitar
set -euo pipefail

# La comprobación de fichero va ANTES del pipe: dentro de un pipe el `exit 1` sólo sale de la
# subshell de la izquierda y python correría con el stdin vacío, imprimiendo un OK que miente
# (familia C5/C7: una comprobación que dice éxito cuando no comprobó nada). Fuera del pipe,
# `exit 1` termina el script de verdad y no se llega a python.
ENTRADA=""
if [ "$#" -ge 1 ] && [ "$1" != "-" ]; then
    [ -f "$1" ] || { echo "ERROR no existe el fichero de mensaje: $1" >&2; exit 1; }
    ENTRADA="$1"
fi
if [ -n "$ENTRADA" ]; then cat "$ENTRADA"; else cat; fi | python3 -c '
import sys, re
t = sys.stdin.read()
rangos = [(0x3040,0x30FF,"kana"),(0x3400,0x4DBF,"CJK-ext"),(0x4E00,0x9FFF,"CJK"),
          (0xAC00,0xD7AF,"Hangul"),(0x0600,0x06FF,"árabe"),(0x0400,0x04FF,"cirílico")]
prohibidos = []
for i, ln in enumerate(t.splitlines(), 1):
    for c in ln:
        for a, b, nom in rangos:
            if a <= ord(c) <= b:
                prohibidos.append((i, nom, c, ln.strip()[:60])); break
avisos = []
for i, ln in enumerate(t.splitlines(), 1):
    for m in re.finditer(r"\b(those|this|the|and|with|for|that|have|from|aggiorn|please|maybe)\b", ln, re.I):
        avisos.append((i, m.group(0), ln.strip()[:60]))
if prohibidos:
    print("REVISAR_MENSAJE_FALLIDO: %d caracter(es) prohibido(s) en el mensaje" % len(prohibidos))
    for i, nom, c, ln in prohibidos[:12]:
        print("  línea %d [%s] %r | %s" % (i, nom, c, ln))
    sys.exit(1)
if avisos:
    print("REVISAR_MENSAJE_AVISO: %d posible(s) anglicismo/italianismo (revisar a ojo;" % len(avisos))
    print("  las citas en inglés y las palabras clave de código son falsos positivos)")
    for i, w, ln in avisos[:12]:
        print("  línea %d %r | %s" % (i, w, ln))
print("REVISAR_MENSAJE_OK: sin caracteres prohibidos en el mensaje")
'
