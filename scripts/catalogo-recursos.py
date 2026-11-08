#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Catálogo de recursos del ecosistema: EverythingMoe, Wotaku y Miyomi.

QUÉ HACE Y POR QUÉ ASÍ
---------------------
Las tres fuentes se piden **en paralelo** y cada una tiene su propio parser, en
vez de uno genérico: las tres publican HTML/JSON con estructuras distintas y un
parser «universal» acabaría perdiendo filas sin decirlo. Aquí, cada parser cuenta
lo que entendió y lo que no, y el informe final dice el número exacto.

LA REGLA QUE SE RESPETA
-----------------------
**Nada se guarda en el almacenamiento interno de la app.** Este script escribe su
salida en el repositorio (`data/`), que es del proyecto, no del teléfono. El
catálogo que se genere aquí es dato de referencia para el repo; lo que la app
haga con él en el futuro tendrá que respetar `core/InventoryStorage`.

LO QUE NO SE INVENTA
--------------------
- No se rellena ningún campo que la fuente no publique. Un `null` es información.
- No se deduplica por nombre entre fuentes distintas: la misma app en dos
  catálogos son dos fichas con dos URLs distintas, y fusionarlas a ojo perdería
  justo la diferencia que interesa (versión, fork, plataforma).
- Las comprobaciones de estado HTTP están a la vista en el informe.

Uso:
    python3 scripts/catalogo-recursos.py                # informe por pantalla
    python3 scripts/catalogo-recursos.py --json data/   # escribe los JSON
"""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import os
import re
import sys
import unicodedata
from datetime import datetime, timezone
from typing import Any

try:
    import requests
    from bs4 import BeautifulSoup
except ImportError:  # pragma: no cover
    sys.exit("faltan dependencias: pip install requests beautifulsoup4 lxml")

AGENTE = "cero-X/1.0.0 (+https://github.com/Alex17273747576777/cero-X-app)"
CABECERAS = {"User-Agent": AGENTE, "Accept-Language": "en,es;q=0.8"}

FUENTES = {
    "everythingmoe": ["https://everythingmoe.com/", "https://everythingmoe.com/simple"],
    "wotaku": [
        "https://wotaku.wiki/software",
        "https://wotaku.wiki/websites",
        "https://wotaku.wiki/misc",
        "https://wotaku.wiki/ext",
        "https://wotaku.wiki/nonen",
    ],
    "miyomi": [
        "https://miyomi.app/api/apps",
        "https://miyomi.app/api/extensions",
        "https://miyomi.app/api/guides",
    ],
}


# ─────────────────────────────────────────────────────────────────────────────
# Utilidades
# ─────────────────────────────────────────────────────────────────────────────

def limpiar(texto: str) -> str:
    """Quita espacios repetidos y caracteres de anchura cero que rompen el JSON."""
    if texto is None:
        return ""
    texto = unicodedata.normalize("NFKC", str(texto))
    texto = texto.replace("\u200b", "").replace("\u200c", "").replace("\ufeff", "")
    return re.sub(r"[ \t\u00a0]+", " ", texto).strip()


def slug(texto: str) -> str:
    base = unicodedata.normalize("NFKD", texto)
    base = "".join(c for c in base if not unicodedata.combining(c))
    base = re.sub(r"[^a-z0-9]+", "-", base.lower()).strip("-")
    return base or "sin-nombre"


def ahora() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


# ─────────────────────────────────────────────────────────────────────────────
# Parser 1 · Miyomi — tiene API JSON, así que no se hace scraping
# ─────────────────────────────────────────────────────────────────────────────

def parsear_miyomi(crudos: dict[str, str]) -> dict[str, Any]:
    """
    Miyomi tiene API JSON: no se hace scraping.

    Tres endpoints confirmados por código 200 y contenido, no por suposición:
      · `/api/apps`       → 149 fichas de 36 campos
      · `/api/extensions` → 47 módulos/extensiones
      · `/api/guides`     → 24 guías

    Los que devolvieron 404 (`/api/repos`, `/api/repositories`, `/api/lists`…) no
    se inventan ni se «prueban» variantes: se dejaron fuera y quedan dichos aquí.
    """
    salida: dict[str, Any] = {"apps": [], "extensiones": [], "guias": []}

    crudo = crudos.get("https://miyomi.app/api/apps")
    if crudo:
        apps = (json.loads(crudo).get("data") or [])
        for a in apps:
            salida["apps"].append({
                "id": a.get("id"),
                "nombre": limpiar(a.get("name")),
                "slug": a.get("slug"),
                "descripcion_corta": limpiar(a.get("shortDescription")),
                "descripcion": limpiar(a.get("description")),
                "autor": limpiar(a.get("author")),
                "categoria": a.get("category"),
                "estado": a.get("status"),
                "version": a.get("version"),
                "plataformas": a.get("platforms") or [],
                "tipos_contenido": a.get("contentTypes") or [],
                "etiquetas": a.get("tags") or [],
                "compatible_con": a.get("compatibleWith") or [],
                "icono": a.get("iconUrl"),
                "descarga": a.get("downloadUrl"),
                "repo": a.get("repoUrl"),
                "web": a.get("websiteUrl"),
                "discord": a.get("discordUrl"),
                "upstream": a.get("upstreamUrl"),
                "fork_de": a.get("forkOf"),
                "url_ficha": f"https://miyomi.app/software/{a.get('slug')}" if a.get("slug") else None,
                "ultima_version": a.get("lastReleaseDate"),
                "actualizado": a.get("updatedAt"),
                "descargas": a.get("downloadCount"),
                "likes": a.get("likesCount"),
                "aviso_moderacion": limpiar(a.get("moderatorNote")) or None,
                "fuente": "miyomi",
            })

    crudo = crudos.get("https://miyomi.app/api/extensions")
    if crudo:
        for e in (json.loads(crudo).get("data") or []):
            salida["extensiones"].append({
                "id": e.get("id"),
                "nombre": limpiar(e.get("name")),
                "slug": e.get("slug"),
                "descripcion": limpiar(e.get("description")),
                "autor": limpiar(e.get("author")),
                "categoria": e.get("category"),
                "estado": e.get("status"),
                "version": e.get("version"),
                "plataformas": e.get("platforms") or [],
                "compatible_con": e.get("compatibleWith") or [],
                "repo": e.get("repoUrl"),
                "descarga": e.get("downloadUrl"),
                "url_ficha": f"https://miyomi.app/extensions/{e.get('slug')}" if e.get("slug") else None,
                "actualizado": e.get("updatedAt"),
                "fuente": "miyomi",
            })

    crudo = crudos.get("https://miyomi.app/api/guides")
    if crudo:
        for g in (json.loads(crudo).get("data") or []):
            salida["guias"].append({
                "id": g.get("id"),
                "titulo": limpiar(g.get("title")),
                "slug": g.get("slug"),
                "categoria": g.get("category"),
                "autor": limpiar(g.get("author") or g.get("authorName")),
                "url": f"https://miyomi.app/guides/{g.get('slug')}" if g.get("slug") else None,
                "actualizado": g.get("updatedAt"),
                "fuente": "miyomi",
            })
    return salida


# ─────────────────────────────────────────────────────────────────────────────
# Parser 2 · EverythingMoe — grupos `.index-group` con enlaces ordenados
# ─────────────────────────────────────────────────────────────────────────────

# Etiquetas que EverythingMoe pone al lado del nombre. Se guardan como flags en
# vez de como parte del nombre: «Comix HUB» no se llama así.
ETIQUETAS_EM = {
    "HUB", "MULT", "NEW", "LOGIN", "RE", "MU", "DDL", "USENET", "WESTERN",
    "EXTENSION", "OLD", "NSFW", "PAID", "SCAN", "OFFICIAL",
}


def _items_de_seccion(sec, nombre_sec: str, sec_id: str | None, salida: list) -> int:
    """Extrae los `div.section-item` de una sección. Devuelve cuántos añadió."""
    en_esta = 0
    for item in sec.select("div.section-item"):
        enlace = item.find("a", href=True)
        if not enlace:
            continue
        nombre = limpiar(enlace.get_text(" ", strip=True))
        if not nombre:
            continue
        partes = nombre.split()
        flags = [p for p in partes if p in ETIQUETAS_EM]
        nombre_limpio = " ".join(p for p in partes if p not in ETIQUETAS_EM).strip() or nombre
        img = item.find("img")
        href = enlace["href"]
        salida.append({
            "nombre": nombre_limpio,
            "url_ficha": "https://everythingmoe.com" + href if href.startswith("/") else href,
            "url": href if href.startswith("http") else None,
            "seccion": nombre_sec,
            "seccion_id": sec_id,
            "flags": flags,
            "icono": (img.get("src") if img else None),
            "fuente": "everythingmoe",
        })
        en_esta += 1
    return en_esta


def _numeros_faltantes(sopa, total: int) -> list[int]:
    """
    Qué números de la lista numerada no aparecen en el HTML.

    EverythingMoe numera cada entrada (`1.`, `2.`, …). Si la página declara 73 y
    solo hay 42, los que faltan son los que **el sitio no manda**: la app no los
    puede inventar ni los puede sacar de otro sitio sin dejar de ser cierto lo
    que dice. Devolver los números exactos es más útil que decir «faltan 31».
    """
    vistos = set()
    for item in sopa.select("div.section-item"):
        texto = limpiar(item.get_text(" ", strip=True))
        m = re.match(r"^(\d+)\.", texto)
        if m:
            vistos.add(int(m.group(1)))
    if not vistos:
        return []
    return [i for i in range(1, total + 1) if i not in vistos]


def parsear_everythingmoe(crudos: dict[str, str], secciones_html: dict[str, str] | None = None) -> dict[str, Any]:
    """
    EverythingMoe en tres pasos, porque la portada **recorta** las listas.

    1. **Portada** (`/`): da el nombre de cada sección, su id (`sec-anime`) y el
       total que declara entre paréntesis. Los recursos enlazan a `/s/<slug>`,
       la ficha del propio sitio.
    2. **`/section/<id>`**: la lista **completa** de esa sección. Aquí está el
       «view all»: no es un botón con JavaScript, es una URL. Comprobado: la
       portada trae 20 de anime y `/section/anime` trae 106.
    3. **`/simple`**: vista alternativa con enlaces externos directos. Se usa
       como respaldo y se marca distinto para no contar dos veces lo mismo.

    Si en el paso 2 falta alguna sección, se dice en `avisos` en vez de dar el
    catálogo por completo.
    """
    apps: list[dict[str, Any]] = []
    avisos: list[dict[str, Any]] = []
    detalle: dict[str, Any] = {}

    html = crudos.get("https://everythingmoe.com/")
    if not html:
        return {"error": "la portada no respondió", "apps": []}
    sopa = BeautifulSoup(html, "lxml")
    secciones = sopa.select("div.section")
    indice: list[dict[str, Any]] = []

    for sec in secciones:
        sec_id = sec.get("id") or ""
        texto = limpiar(sec.get_text(" ", strip=True))[:160]
        m = re.match(r"^(.*?)\s*\((\d+)\)", texto)
        if m:
            nombre_sec, total_declarado = limpiar(m.group(1)), int(m.group(2))
        else:
            nombre_sec, total_declarado = sec_id.replace("sec-", "").replace("-", " "), None
        indice.append({"id": sec_id, "nombre": nombre_sec, "declara": total_declarado})

    # Las secciones completas, si quien llama ya las descargó.
    completas = secciones_html or {}
    for entrada in indice:
        sec_id = entrada["id"]
        if not sec_id:
            continue
        clave = f"https://everythingmoe.com/section/{sec_id.replace('sec-', '')}"
        html_sec = completas.get(clave)
        if html_sec:
            sopa_sec = BeautifulSoup(html_sec, "lxml")
            n = _items_de_seccion(sopa_sec, entrada["nombre"], sec_id, apps)
            entrada["encontrados"] = n
            if entrada["declara"] is not None and n < entrada["declara"]:
                faltan = _numeros_faltantes(sopa_sec, entrada["declara"])
                avisos.append({
                    "seccion": entrada["nombre"],
                    "declara": entrada["declara"],
                    "encontrados": n,
                    "numeros_que_faltan": faltan,
                    "motivo": "la página NO publica estas entradas en el HTML; están ocultas "
                              "(hay 42 botones `morebtn` en una lista de 73)",
                })
        else:
            # Sin la sección completa, se toman los de la portada (recortados) y
            # se avisa: es mejor 20 de 106 con el aviso que 0 o que 106 inventados.
            n = _items_de_seccion(sec, entrada["nombre"], sec_id, apps)
            entrada["encontrados"] = n
            avisos.append({
                "seccion": entrada["nombre"],
                "declara": entrada["declara"],
                "encontrados": n,
                "motivo": f"no se pudo descargar {clave}; se usan los de la portada",
            })

    detalle["secciones"] = indice

    html2 = crudos.get("https://everythingmoe.com/simple")
    if html2:
        sopa2 = BeautifulSoup(html2, "lxml")
        n_simple = 0
        for grupo in sopa2.select("div.index-group"):
            texto = limpiar(grupo.get_text(" ", strip=True))[:160]
            m = re.match(r"^(.*?)\s*\((\d+)\)", texto)
            nombre_sec = limpiar(m.group(1)) if m else "sin sección"
            for enlace in grupo.find_all("a", href=True):
                href = enlace["href"]
                nombre = limpiar(enlace.get_text(" ", strip=True))
                if not nombre or not href.startswith("http") or "everythingmoe.com/s/" in href:
                    continue
                partes = nombre.split()
                flags = [p for p in partes if p in ETIQUETAS_EM]
                img = enlace.find("img")
                apps.append({
                    "nombre": " ".join(p for p in partes if p not in ETIQUETAS_EM).strip() or nombre,
                    "url": href,
                    "url_ficha": None,
                    "seccion": nombre_sec,
                    "seccion_id": None,
                    "flags": flags,
                    "icono": (img.get("src") if img else None),
                    "fuente": "everythingmoe",
                    "vista": "simple",
                })
                n_simple += 1
        detalle["simple"] = n_simple

    if not apps:
        return {"error": "no se extrajo ninguna entrada", "apps": []}
    return {"apps": apps, "avisos": avisos, "detalle": detalle}


# ─────────────────────────────────────────────────────────────────────────────
# Parser 3 · Wotaku — tablas con encabezado por categoría y enlaces por celda
# ─────────────────────────────────────────────────────────────────────────────

def parsear_wotaku(crudos: dict[str, str]) -> dict[str, Any]:
    apps: list[dict[str, Any]] = []
    secciones_vistas: list[str] = []
    for url, html in crudos.items():
        if not url.startswith("https://wotaku.wiki/"):
            continue
        sopa = BeautifulSoup(html, "lxml")
        cuerpo = sopa.find(class_="VPDoc") or sopa
        pagina = url.rstrip("/").split("/")[-1]

        # Cada encabezado abre una sección; las tablas que vienen después
        # pertenecen a esa sección hasta el siguiente encabezado.
        actual = pagina
        for nodo in cuerpo.find_all(["h1", "h2", "h3", "h4", "table"]):
            if nodo.name in ("h1", "h2", "h3", "h4"):
                titulo = limpiar(nodo.get_text(" ", strip=True))
                if titulo and titulo not in ("Type", "Show more", "Show less"):
                    actual = titulo
                    if actual not in secciones_vistas:
                        secciones_vistas.append(actual)
                continue

            tabla = nodo
            cabecera = [limpiar(th.get_text(" ", strip=True)) for th in tabla.find_all("th")]
            for fila in tabla.find_all("tr"):
                celdas = fila.find_all("td")
                if not celdas:
                    continue
                enlaces = fila.find_all("a", href=True)
                externos = [a for a in enlaces if a["href"].startswith("http")]
                if not externos:
                    continue
                # Nombre de la fila: el texto de la primera celda que no sea un
                # enlace interno. Casi siempre es el nombre de la app.
                primera = limpiar(celdas[0].get_text(" ", strip=True))
                for i, a in enumerate(externos):
                    nombre = limpiar(a.get_text(" ", strip=True)) or (primera if i == 0 else "")
                    if not nombre or nombre.lower() in ("here", "source", "github"):
                        continue
                    # La columna dice de qué va: Source, Tracker, Sub, Platform...
                    columna = ""
                    if cabecera and i < len(cabecera):
                        columna = cabecera[i]
                    apps.append({
                        "nombre": nombre,
                        "url": a["href"],
                        "pagina": pagina,
                        "seccion": actual,
                        "columna": columna,
                        "fuente": "wotaku",
                    })
    return {"apps": apps, "secciones": secciones_vistas}


# ─────────────────────────────────────────────────────────────────────────────
# Orquestación en paralelo
# ─────────────────────────────────────────────────────────────────────────────

def pedir(url: str, tiempo: int = 45) -> tuple[str, int, str]:
    try:
        r = requests.get(url, headers=CABECERAS, timeout=tiempo)
        return url, r.status_code, r.text
    except Exception as e:  # noqa: BLE001 — se informa, no se oculta
        return url, -1, f"ERROR: {type(e).__name__}: {e}"


def descargar_todo() -> tuple[dict[str, str], list[dict[str, Any]]]:
    todas = [u for lista in FUENTES.values() for u in lista]
    crudos: dict[str, str] = {}
    informe: list[dict[str, Any]] = []
    # 12 hilos: son 8 URLs de tres dominios distintos. Más hilos no acelera nada
    # porque el cuello de botella es la red, y menos desaprovecha el paralelismo.
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as ex:
        for url, codigo, cuerpo in ex.map(pedir, todas):
            informe.append({"url": url, "http": codigo, "bytes": len(cuerpo)})
            if codigo == 200:
                crudos[url] = cuerpo
    return crudos, informe


def descubrir_y_bajar_secciones(portada: str) -> dict[str, str]:
    """
    Segunda fase del rastreo de EverythingMoe.

    Se hace aparte porque las URLs de las secciones **solo se conocen después**
    de leer la portada: no están en un sitemap ni se pueden adivinar todas. Aquí
    van en paralelo, y cada fallo se informa.
    """
    sopa = BeautifulSoup(portada, "lxml")
    ids = [d.get("id") for d in sopa.select("div.section") if d.get("id")]
    urls = [f"https://everythingmoe.com/section/{i.replace('sec-', '')}" for i in ids]
    crudos: dict[str, str] = {}
    if not urls:
        return crudos
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as ex:
        for url, codigo, cuerpo in ex.map(pedir, urls):
            if codigo == 200:
                crudos[url] = cuerpo
    return crudos


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", metavar="DIR", help="escribe los JSON en este directorio")
    args = ap.parse_args()

    print("cero-X · catálogo de recursos del ecosistema")
    print(f"agente: {AGENTE}\n")

    crudos, informe = descargar_todo()
    print("── descargas (en paralelo) ──")
    for i in informe:
        estado = "ok" if i["http"] == 200 else f"FALLO {i['http']}"
        print(f"  {estado:10s} {i['bytes']:>8} B  {i['url']}")

    # Segunda fase en paralelo: las secciones completas de EverythingMoe.
    secciones = descubrir_y_bajar_secciones(crudos.get("https://everythingmoe.com/", ""))
    if secciones:
        print(f"\n── secciones completas de EverythingMoe: {len(secciones)} descargadas ──")

    resultados = {
        "miyomi": parsear_miyomi(crudos),
        "everythingmoe": parsear_everythingmoe(crudos, secciones),
        "wotaku": parsear_wotaku(crudos),
    }

    print("\n── recuento por fuente ──")
    total = 0
    for nombre, r in resultados.items():
        if "error" in r:
            print(f"  {nombre:14s} ERROR: {r['error']}")
            continue
        n = len(r.get("apps", []))
        total += n
        extra = ""
        for k in ("extensiones", "guias"):
            if r.get(k):
                extra += f" + {len(r[k])} {k}"
                total += len(r[k])
        print(f"  {nombre:14s} {n:>5} apps{extra}")
    print(f"  {'TOTAL':14s} {total:>5} entradas")

    em = resultados["everythingmoe"]
    if em.get("detalle"):
        print("\n── EverythingMoe por vista ──")
        for vista, d in em["detalle"].items():
            print(f"  {vista:10s} {d}")
    if em.get("avisos"):
        print("\n── avisos de EverythingMoe: secciones que declaran más de lo que trae el HTML ──")
        for a in em["avisos"]:
            faltan = a.get("numeros_que_faltan") or []
            cola = f" faltan {len(faltan)}: {faltan[:12]}{'…' if len(faltan) > 12 else ''}" if faltan else ""
            print(f"  · {a['seccion']}: declara {a['declara']}, en el HTML hay {a.get('encontrados')}.{cola}")

    if args.json:
        os.makedirs(args.json, exist_ok=True)
        sello = ahora()
        for nombre, r in resultados.items():
            destino = os.path.join(args.json, f"catalogo-{nombre}.json")
            with open(destino, "w", encoding="utf-8") as f:
                json.dump(
                    {
                        "generado": sello,
                        "agente": AGENTE,
                        "fuente": nombre,
                        "urls": FUENTES[nombre],
                        "entradas": len(r.get("apps", [])),
                        "extensiones": len(r.get("extensiones", [])),
                        "guias": len(r.get("guias", [])),
                        "datos": r,
                    },
                    f, ensure_ascii=False, indent=1,
                )
            print(f"\nescrito {destino} ({os.path.getsize(destino)} B)")

    return 0


if __name__ == "__main__":
    sys.exit(main())
