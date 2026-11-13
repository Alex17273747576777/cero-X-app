#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 La Bandita
# SPDX-License-Identifier: GPL-3.0-only
"""
Buscador de appteka.store.

El método: appteka no tiene API pública (404 en /api/*), pero es una app Next.js que
sirve los resultados de búsqueda ya renderizados en el servidor, dentro del payload
RSC (`self.__next_f.push([1,"..."])`). O sea que no hace falta rascar el DOM ni
recorrer su sitemap de 274.268 apps: se le pide a SU buscador y se deserializa el
JSON que viene en la página.

Cada resultado tiene esta forma:

    {"app_id":"7ccr301469","size":13712806,"label":"Equalizer",
     "package":"com.jazibkhan.equalizer","ver_name":"6.3.5.8","ver_code":999,
     "downloads":752,"author":{"id":335990,"name":"..."}, ...}

Los trozos RSC vienen escapados como cadena JavaScript, así que se decodifican con
json.loads('"'+trozo+'"'), que es lo que respeta el UTF-8 (decodificar con
unicode_escape rompe los acentos y el cirílico).

Uso:
    python3 appteka_buscar.py equalizer iptv m3u
    python3 appteka_buscar.py --json equalizer     # vuelca resultados a JSON
"""
import json
import os
import re
import sys
import urllib.parse
import urllib.request

AQUI = os.path.dirname(os.path.abspath(__file__))
CHUNK = re.compile(r'self\.__next_f\.push\(\[1,"(.*?)"\]\)', re.S)
UA = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) cero-X-investigacion/1.0"}


def descargar(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8", "replace")


def payload(html):
    trozos = CHUNK.findall(html)
    out = []
    for t in trozos:
        try:
            out.append(json.loads('"' + t + '"'))
        except json.JSONDecodeError:
            out.append(t.encode("utf-8", "replace").decode("unicode_escape", "replace"))
    return "".join(out)


def objetos_con(texto, clave):
    """Extrae cada objeto JSON que empieza por {"clave": recorriendo llaves."""
    res = []
    for m in re.finditer(r'\{"' + clave + r'":', texto):
        i = m.start()
        prof = 0
        en_cadena = False
        escape = False
        for j in range(i, len(texto)):
            c = texto[j]
            if en_cadena:
                if escape:
                    escape = False
                elif c == "\\":
                    escape = True
                elif c == '"':
                    en_cadena = False
                continue
            if c == '"':
                en_cadena = True
            elif c == "{":
                prof += 1
            elif c == "}":
                prof -= 1
                if prof == 0:
                    try:
                        res.append(json.loads(texto[i:j + 1]))
                    except json.JSONDecodeError:
                        pass
                    break
    return res


def buscar(termino, pagina=1):
    q = urllib.parse.urlencode({"q": termino})
    if pagina > 1:
        q += "&page=%d" % pagina
    html = descargar("https://appteka.store/search?" + q)
    return objetos_con(payload(html), "app_id")


def limpiar(o):
    autor = (o.get("author") or {}).get("name") if isinstance(o.get("author"), dict) else None
    cat = o.get("category")
    if isinstance(cat, dict):
        cat = (cat.get("name") or {}).get("en") if isinstance(cat.get("name"), dict) else cat.get("name")
    return {
        "app_id": o.get("app_id"),
        "nombre": o.get("label"),
        "paquete": o.get("package"),
        "version": o.get("ver_name"),
        "mb": round((o.get("size") or 0) / 1048576, 2),
        "descargas": o.get("downloads"),
        "autor": autor,
        "categoria": cat,
        "url": "https://appteka.store/app/" + str(o.get("app_id")),
    }


def main(argv):
    a_json = "--json" in argv
    terminos = [a for a in argv if not a.startswith("--")]
    if not terminos:
        raise SystemExit(__doc__)
    todo = []
    for t in terminos:
        try:
            crudos = buscar(t)
        except Exception as e:
            print("  ERROR buscando %r: %s" % (t, e))
            continue
        vistos = set()
        limpios = []
        for o in crudos:
            r = limpiar(o)
            if r["app_id"] in vistos or not r["nombre"]:
                continue
            vistos.add(r["app_id"])
            limpios.append(r)
        limpios.sort(key=lambda r: -(r["descargas"] or 0))
        print("\n════ appteka · %r · %d resultados ════" % (t, len(limpios)))
        for r in limpios[:18]:
            print("  %-34s %-40s v%-12s %7.2f MB · %8s descargas · %s"
                  % (r["nombre"][:34], (r["paquete"] or "")[:40], str(r["version"])[:12],
                     r["mb"], r["descargas"], (r["autor"] or "")[:22]))
        for r in limpios:
            r["consulta"] = t
        todo.extend(limpios)
    if a_json:
        ruta = os.path.join(AQUI, "appteka-resultados.json")
        with open(ruta, "w", encoding="utf-8") as f:
            json.dump(todo, f, ensure_ascii=False, indent=1)
        print("\nguardado:", ruta, "·", len(todo), "registros")


if __name__ == "__main__":
    main(sys.argv[1:])
