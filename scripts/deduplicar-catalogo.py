#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# SPDX-FileCopyrightText: 2026 La Bandita
# SPDX-License-Identifier: GPL-3.0-only
"""
Deduplicador del catálogo de recursos.

EL PROBLEMA QUE RESUELVE, CON NÚMEROS
-------------------------------------
Medido sobre lo extraído el 8 de noviembre de 2026:

  · EverythingMoe: 798 filas, **9 URLs repetidas** y **21 nombres repetidos**
    (p. ej. «Reddit Sub», «MyAnimeList Forum» aparecen en dos secciones).
  · Wotaku: 156 filas, **7 URLs repetidas** y **8 nombres repetidos**
    (Hayase, AnymeX, Mangayomi, Dartotsu, Seanime, Komga… aparecen en la tabla
    de Android y en la de PC, o como App y como Tracker).
  · Miyomi: 149 filas, **0 repetidas** — su API ya devuelve fichas únicas.
  · **Entre fuentes: 94 nombres coinciden en más de una.**

EL CRITERIO, Y POR QUÉ NO ES «PARECIDO ENTONCES ES LO MISMO»
------------------------------------------------------------
La clave primaria es la **URL normalizada**: mismo recurso = misma URL. Eso no
necesita heurística y no se equivoca.

Cuando dos filas comparten nombre pero **no** URL, no se fusionan a ciegas: se
comparan los hosts. Tres resultados posibles, y cada uno se registra:

  · `misma_url`        → duplicado exacto. Se queda una fila y se anotan las
                         secciones donde aparecía. **Se fusiona.**
  · `mismo_host`       → dos fichas del mismo sitio (portada y subpágina).
                         Se fusiona quedándose con la URL más específica.
  · `hosts_distintos`  → puede ser la misma app en dos hosts (su web y su repo)
                         o dos apps con el mismo nombre. **No se fusiona**:
                         se marca en `revision` para que lo decida una persona.
                         Fusionar aquí es exactamente el falso positivo que no
                         se permite.

`agrupar()` produce un catálogo unificado donde cada fila es un recurso con
todas las fuentes que lo mencionan. `revision` es la lista de lo que no se
fusionó y por qué.

Uso:
    python3 scripts/deduplicar-catalogo.py --dir data
    python3 scripts/deduplicar-catalogo.py --dir data --json data/catalogo-unico.json
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import unicodedata
from typing import Any


# ─────────────────────────────────────────────────────────────────────────────
# Normalización
# ─────────────────────────────────────────────────────────────────────────────

def normalizar_nombre(nombre: str | None) -> str:
    """Minúsculas, sin acentos, sin puntuación: para comparar, no para mostrar."""
    if not nombre:
        return ""
    n = unicodedata.normalize("NFKD", str(nombre).lower())
    n = "".join(c for c in n if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", " ", n).strip()


RUIDO_URL = re.compile(r"^(www\.|m\.|es\.|en\.)")


def normalizar_url(url: str | None) -> str | None:
    """
    Deja la URL comparable: sin esquema, sin `www.`, sin barra final, sin
    parámetros de seguimiento y en minúsculas el host.

    No se quita la ruta: `github.com/a/proyecto` y `github.com/b/proyecto` son
    cosas distintas.
    """
    if not url:
        return None
    u = str(url).strip()
    u = re.sub(r"^https?://", "", u, flags=re.I)
    host, _, resto = u.partition("/")
    host = RUIDO_URL.sub("", host.lower())
    resto = resto.split("#")[0]
    resto = re.sub(r"[?&](utm_[^=&]+|ref|referrer)=[^&]*", "", resto)
    return (host + ("/" + resto if resto else "")).rstrip("/").lower()


def host_de(url: str | None) -> str | None:
    n = normalizar_url(url)
    return n.split("/")[0] if n else None


# ─────────────────────────────────────────────────────────────────────────────
# Carga
# ─────────────────────────────────────────────────────────────────────────────

def cargar(directorio: str) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    """
    Junta las tres fuentes en una lista plana con `fuente` y `url` resueltas.

    La URL de un recurso puede estar en `url`, en `url_ficha` (EverythingMoe
    enlaza a su propia ficha) o en `descarga`/`repo` (Miyomi). Se resuelve aquí,
    una vez, en vez de repetir el `if` en cada comparación.
    """
    filas: list[dict[str, Any]] = []
    meta: dict[str, Any] = {}

    em = os.path.join(directorio, "catalogo-everythingmoe.json")
    wk = os.path.join(directorio, "catalogo-wotaku.json")
    my = os.path.join(directorio, "catalogo-miyomi.json")

    if os.path.exists(em):
        d = json.load(open(em, encoding="utf-8"))
        meta["everythingmoe"] = {"generado": d.get("generado"), "entradas": d.get("entradas")}
        for a in d["datos"]["apps"]:
            filas.append({
                "nombre": a.get("nombre"),
                "url": a.get("url") or a.get("url_ficha"),
                "url_ficha": a.get("url_ficha"),
                "seccion": a.get("seccion"),
                "fuente": "everythingmoe",
                "extra": {"flags": a.get("flags"), "icono": a.get("icono")},
            })

    if os.path.exists(wk):
        d = json.load(open(wk, encoding="utf-8"))
        meta["wotaku"] = {"generado": d.get("generado"), "entradas": d.get("entradas")}
        for a in d["datos"]["apps"]:
            filas.append({
                "nombre": a.get("nombre"),
                "url": a.get("url"),
                "url_ficha": None,
                "seccion": f"{a.get('pagina')}: {a.get('seccion')}"
                           + (f" ({a.get('columna')})" if a.get("columna") else ""),
                "fuente": "wotaku",
                "extra": {"columna": a.get("columna")},
            })

    if os.path.exists(my):
        d = json.load(open(my, encoding="utf-8"))
        meta["miyomi"] = {"generado": d.get("generado"), "entradas": d.get("entradas")}
        for a in d["datos"]["apps"]:
            filas.append({
                "nombre": a.get("nombre"),
                "url": a.get("web") or a.get("repo") or a.get("descarga") or a.get("url_ficha"),
                "url_ficha": a.get("url_ficha"),
                "seccion": a.get("categoria") or "sin categoría",
                "fuente": "miyomi",
                "extra": {
                    "plataformas": a.get("plataformas"),
                    "fork_de": a.get("fork_de"),
                    "repo": a.get("repo"),
                },
            })

    return [f for f in filas if f.get("nombre")], meta


# ─────────────────────────────────────────────────────────────────────────────
# Deduplicación dentro de una fuente
# ─────────────────────────────────────────────────────────────────────────────

def deduplicar_fuente(filas: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """
    Fusiona solo lo que es demostrablemente el mismo recurso.

    Devuelve (únicas, informe). El informe dice cuántas se fusionaron por URL y
    cuántas quedaron marcadas por nombre repetido con hosts distintos.
    """
    por_url: dict[str, dict[str, Any]] = {}
    orden: list[str] = []
    fusionadas = 0
    informe: list[dict[str, Any]] = []

    for f in filas:
        clave = normalizar_url(f.get("url")) or f"nombre:{normalizar_nombre(f.get('nombre'))}"
        if clave in por_url:
            previa = por_url[clave]
            # Se acumulan las secciones: la misma ficha en dos secciones es un
            # dato, no un duplicado que se tira.
            secciones = set(previa.get("secciones") or [previa.get("seccion")])
            if f.get("seccion"):
                secciones.add(f["seccion"])
            previa["secciones"] = sorted(s for s in secciones if s)
            # Si una de las dos tiene ficha y la otra no, se conserva la ficha.
            if not previa.get("url_ficha") and f.get("url_ficha"):
                previa["url_ficha"] = f["url_ficha"]
            previa.setdefault("veces", 1)
            previa["veces"] += 1
            fusionadas += 1
            informe.append({
                "tipo": "misma_url",
                "nombre": f.get("nombre"),
                "clave": clave,
                "conservada": previa.get("seccion"),
                "fusionada_con": f.get("seccion"),
            })
        else:
            f.setdefault("secciones", [f.get("seccion")] if f.get("seccion") else [])
            f["veces"] = 1
            por_url[clave] = f
            orden.append(clave)

    unicas = [por_url[k] for k in orden]

    # Nombres repetidos que sobrevivieron: no se fusionan, se marcan.
    por_nombre: dict[str, list[dict[str, Any]]] = {}
    for f in unicas:
        por_nombre.setdefault(normalizar_nombre(f.get("nombre")), []).append(f)

    for nombre, grupo in por_nombre.items():
        if len(grupo) < 2 or not nombre:
            continue
        hosts = {host_de(f.get("url")) for f in grupo}
        for f in grupo:
            f["revision"] = {
                "motivo": "mismo_host_distinta_url" if len(hosts) == 1 else "nombre_repetido_hosts_distintos",
                "grupo": [{"nombre": g.get("nombre"), "url": g.get("url"), "seccion": g.get("seccion")}
                          for g in grupo],
                "nota": "no se fusiona: mismo nombre no es mismo recurso",
            }
        informe.append({
            "tipo": "mismo_host" if len(hosts) == 1 else "hosts_distintos",
            "nombre": grupo[0].get("nombre"),
            "veces": len(grupo),
            "urls": [g.get("url") for g in grupo],
        })

    return unicas, informe


# ─────────────────────────────────────────────────────────────────────────────
# Un catálogo único entre fuentes
# ─────────────────────────────────────────────────────────────────────────────

def unificar(filas: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """
    Agrupa lo que las tres fuentes mencionan, **sin perder de dónde vino**.

    Una fila del catálogo unificado tiene:
      · `nombre`, `url` (la de la fuente con URL externa más fiable)
      · `fuentes`: lista de `{fuente, seccion, url}`
      · `estado`: `confirmado` (≥2 fuentes con el mismo host) o `unico`

    Cuando dos fuentes dan **hosts distintos** para el mismo nombre, no se elige
    una: se deja `url` con la primera y se añade `ambiguo: true` con las dos. Eso
    es lo contrario de un falso positivo: el dato queda a la vista.
    """
    grupos: dict[str, list[dict[str, Any]]] = {}
    for f in filas:
        grupos.setdefault(normalizar_nombre(f.get("nombre")), []).append(f)

    unificadas: list[dict[str, Any]] = []
    ambiguedades: list[dict[str, Any]] = []

    for nombre, grupo in sorted(grupos.items()):
        if not nombre:
            continue
        fuentes = {}
        for g in grupo:
            fuentes.setdefault(g["fuente"], []).append(g.get("url"))
        hosts = {host_de(g.get("url")) for g in grupo if host_de(g.get("url"))}

        # URL preferida: la primera que no sea del propio catálogo de EverythingMoe,
        # porque una ficha interna no lleva al recurso.
        preferida = next((g.get("url") for g in grupo if g.get("url") and "everythingmoe.com" not in g["url"]), None)
        if preferida is None:
            preferida = grupo[0].get("url")

        fila = {
            "nombre": grupo[0].get("nombre"),
            "nombre_normalizado": nombre,
            "url": preferida,
            "fuentes": [
                {
                    "fuente": g["fuente"],
                    "seccion": g.get("seccion"),
                    "url": g.get("url"),
                    "url_ficha": g.get("url_ficha"),
                }
                for g in grupo
            ],
            "num_fuentes": len({g["fuente"] for g in grupo}),
            "estado": "confirmado" if len({g["fuente"] for g in grupo}) >= 2 else "unico",
            "hosts": sorted(h for h in hosts if h),
        }
        if len(hosts) > 1:
            fila["ambiguo"] = True
            ambiguedades.append({
                "nombre": grupo[0].get("nombre"),
                "hosts": sorted(hosts),
                "urls": [g.get("url") for g in grupo],
                "nota": "mismo nombre en hosts distintos: no se elige una, se muestran",
            })
        unificadas.append(fila)

    return unificadas, ambiguedades


# ─────────────────────────────────────────────────────────────────────────────

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default="data")
    ap.add_argument("--json", help="escribe el catálogo unificado en esta ruta")
    args = ap.parse_args()

    filas, meta = cargar(args.dir)
    if not filas:
        print("no hay datos que deduplicar: ejecuta antes scripts/catalogo-recursos.py")
        return 1

    print("cero-X · deduplicación del catálogo")
    print(f"filas de entrada: {len(filas)}\n")

    por_fuente: dict[str, list[dict[str, Any]]] = {}
    for f in filas:
        por_fuente.setdefault(f["fuente"], []).append(f)

    salida: dict[str, Any] = {"meta": meta, "por_fuente": {}, "unificado": None}
    total_unicas = 0

    for fuente, grupo in por_fuente.items():
        unicas, informe = deduplicar_fuente(grupo)
        total_unicas += len(unicas)
        fusionadas = sum(1 for i in informe if i["tipo"] == "misma_url")
        marcadas = sum(1 for i in informe if i["tipo"] in ("mismo_host", "hosts_distintos"))
        print(f"── {fuente}")
        print(f"   {len(grupo)} filas → {len(unicas)} únicas "
              f"({fusionadas} fusionadas por URL, {marcadas} grupos marcados para revisión)")
        for i in informe:
            if i["tipo"] == "misma_url":
                print(f"     · fusionada: {i['nombre']} (aparecía en «{i['conservada']}» y «{i['fusionada_con']}»)")
            elif i["tipo"] == "mismo_host":
                print(f"     · mismo host, URL distinta: {i['nombre']} ×{i['veces']} → se queda aparte")
            else:
                print(f"     · mismo nombre, hosts distintos: {i['nombre']} → {i['urls']}")
        salida["por_fuente"][fuente] = {
            "entrada": len(grupo),
            "unicas": len(unicas),
            "fusionadas": fusionadas,
            "revision": [i for i in informe if i["tipo"] != "misma_url"],
            "datos": unicas,
        }

    unificadas, ambiguas = unificar(filas)
    confirmadas = sum(1 for u in unificadas if u["estado"] == "confirmado")
    print(f"\n── catálogo unificado")
    print(f"   {len(filas)} filas → {len(unificadas)} recursos "
          f"({confirmadas} confirmados por ≥2 fuentes, {len(unificadas) - confirmadas} de una sola)")
    print(f"   {len(ambiguas)} con el mismo nombre en hosts distintos (no se fusionan)")
    salida["unificado"] = {
        "recursos": len(unificadas),
        "confirmados": confirmadas,
        "ambiguos": len(ambiguas),
        "datos": unificadas,
        "ambiguedades": ambiguas,
    }

    if args.json:
        os.makedirs(os.path.dirname(args.json) or ".", exist_ok=True)
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump(salida, f, ensure_ascii=False, indent=1)
        print(f"\nescrito {args.json} ({os.path.getsize(args.json)} B)")

    return 0


if __name__ == "__main__":
    sys.exit(main())
