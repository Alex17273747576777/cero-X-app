#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 La Bandita
# SPDX-License-Identifier: GPL-3.0-only
"""
Índice y buscador de FMHY a partir de la wiki oficial.

El método, que es lo que importa: FMHY no tiene API. Su web (fmhy.net) es VitePress
con un índice de búsqueda generado, y su repo (fmhy/FMHY) sólo contiene fmhy.md, que
es un índice de 2,8 KB con enlaces a la wiki. El contenido real —1,9 MB, 19 páginas—
vive en `https://github.com/fmhy/FMHY.wiki.git`, que ES un repositorio git y se clona.

O sea que la búsqueda de verdad no es rascar HTML: es clonar la wiki, parsear sus
entradas a una estructura y consultarla. Cada línea de entrada tiene esta forma:

    * ⭐ **[Nombre](url)** - Descripción / [GitHub](...) / [Telegram](...)

y cuelga de una sección (`# ► Sección`) y una subsección (`## ▷ Subsección`). Este
script convierte eso en registros con nombre, urls, descripción, marcadores, sección
y subsección, y permite buscar por texto con peso (nombre > descripción > sección).

Uso:
    python3 fmhy_buscar.py indice                    # clona/actualiza y genera indice.json
    python3 fmhy_buscar.py buscar "equalizer" "iptv" # busca en el índice
"""
import json
import os
import re
import subprocess
import sys
import unicodedata

AQUI = os.path.dirname(os.path.abspath(__file__))
WIKI = os.path.join(AQUI, "wiki")
INDICE = os.path.join(AQUI, "indice.json")
URL_WIKI = "https://github.com/fmhy/FMHY.wiki.git"

RE_SECCION = re.compile(r"^#\s+►?\s*\[?(?P<nombre>[^\]]+)\]?\(?")
RE_SUBSECCION = re.compile(r"^##\s+▷?\s*\[?(?P<nombre>[^\]]+)\]?\(?")
RE_LINK = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")
RE_ENTRADA = re.compile(r"^\*\s+(?P<cuerpo>.*)$")


def sin_acentos(t):
    return "".join(c for c in unicodedata.normalize("NFD", t) if unicodedata.category(c) != "Mn")


def clonar():
    if os.path.isdir(os.path.join(WIKI, ".git")):
        subprocess.run(["git", "-C", WIKI, "pull", "--depth", "1"], capture_output=True, timeout=180)
    else:
        subprocess.run(["git", "clone", "--depth", "1", URL_WIKI, WIKI], capture_output=True, timeout=300, check=True)


def normalizar_nombre(txt):
    """Quita el U+2060 (word joiner) que FMHY usa para romper el autolink."""
    return txt.replace("\u2060", "").strip()


def parsear_pagina(ruta):
    pagina = os.path.basename(ruta)[:-3]
    seccion = subseccion = pagina
    notas = []
    with open(ruta, encoding="utf-8") as f:
        for linea in f:
            linea = linea.rstrip("\n")
            if linea.startswith("# ") and not linea.startswith("## "):
                m = RE_SECCION.match(linea)
                seccion = normalizar_nombre(m.group("nombre")) if m else linea[2:].strip()
                subseccion = seccion
                continue
            if linea.startswith("## "):
                m = RE_SUBSECCION.match(linea)
                subseccion = normalizar_nombre(m.group("nombre")) if m else linea[3:].strip()
                continue
            m = RE_ENTRADA.match(linea)
            if not m:
                continue
            cuerpo = m.group("cuerpo").strip()
            if not cuerpo:
                continue
            enlaces = [(normalizar_nombre(a), b) for a, b in RE_LINK.findall(cuerpo)]
            if not enlaces:
                continue
            # El primer enlace con texto en negrita es el nombre; si no, el primero.
            negrita = re.search(r"\*\*\[([^\]]+)\]\(([^)]+)\)\*\*", cuerpo)
            if negrita:
                nombre, url = normalizar_nombre(negrita.group(1)), negrita.group(2)
            else:
                nombre, url = enlaces[0]
            # La descripción va tras " - " y antes del primer " / " que separa extras.
            resto = cuerpo[cuerpo.index("]") + 1:] if "]" in cuerpo else cuerpo
            descripcion = ""
            if " - " in resto:
                descripcion = resto.split(" - ", 1)[1]
            descripcion = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", descripcion)
            marcadores = [m for m in ("⭐", "↪️", "🌐", "►") if m in cuerpo]
            github = [u for _, u in enlaces if "github.com" in u or "gitlab.com" in u or "codeberg.org" in u]
            notas.append({
                "nombre": nombre,
                "url": url,
                "descripcion": descripcion.strip(" */"),
                "pagina": pagina,
                "seccion": seccion,
                "subseccion": subseccion,
                "destacado": "⭐" in marcadores,
                "es_enlace_a_seccion": "↪️" in marcadores,
                "github": github,
                "texto_plano": sin_acentos(
                    " ".join([nombre, descripcion, seccion, subseccion, " ".join(u for _, u in enlaces)])
                ).lower(),
            })
    return notas


def construir():
    clonar()
    todo = []
    for f in sorted(os.listdir(WIKI)):
        if f.endswith(".md"):
            todo.extend(parsear_pagina(os.path.join(WIKI, f)))
    with open(INDICE, "w", encoding="utf-8") as f:
        json.dump(todo, f, ensure_ascii=False, indent=1)
    destacadas = sum(1 for t in todo if t["destacado"])
    con_github = sum(1 for t in todo if t["github"])
    print(f"índice: {len(todo)} entradas · {destacadas} destacadas · {con_github} con repo · {INDICE}")
    paginas = {}
    for t in todo:
        paginas[t["pagina"]] = paginas.get(t["pagina"], 0) + 1
    for p, n in sorted(paginas.items(), key=lambda x: -x[1]):
        print(f"    {n:5d}  {p}")
    return todo


def cargar():
    if not os.path.exists(INDICE):
        return construir()
    with open(INDICE, encoding="utf-8") as f:
        return json.load(f)


def buscar(terminos, limite=25):
    indice = cargar()
    terminos = [sin_acentos(t).lower() for t in terminos]
    puntuados = []
    for e in indice:
        nombre = sin_acentos(e["nombre"]).lower()
        texto = e["texto_plano"]
        puntos = 0
        for t in terminos:
            if t in nombre:
                puntos += 6
            if t in texto:
                puntos += 2
            if t in sin_acentos(e["descripcion"]).lower():
                puntos += 2
            if t in sin_acentos(e["subseccion"]).lower():
                puntos += 1
        if puntos:
            if e["destacado"]:
                puntos += 1
            if e["github"]:
                puntos += 1
            if e["es_enlace_a_seccion"]:
                puntos -= 2
            puntuados.append((puntos, e))
    puntuados.sort(key=lambda x: (-x[0], x[1]["nombre"].lower()))
    for puntos, e in puntuados[:limite]:
        g = " · " + e["github"][0] if e["github"] else ""
        est = "⭐ " if e["destacado"] else "   "
        print(f"  {puntos:3d}  {est}{e['nombre'][:38]:38s} [{e['pagina']}/{e['subseccion'][:22]}]{g}")
        if e["descripcion"]:
            print(f"        {e['descripcion'][:150]}")
        print(f"        {e['url']}")
    return puntuados


if __name__ == "__main__":
    orden = sys.argv[1] if len(sys.argv) > 1 else "indice"
    if orden == "indice":
        construir()
    elif orden == "buscar":
        buscar(sys.argv[2:])
    else:
        raise SystemExit("uso: fmhy_buscar.py indice | buscar TERMINO [TERMINO...]")
