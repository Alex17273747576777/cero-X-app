#!/usr/bin/env python3
"""Reconstruye/verifica URLs de releases Project Nox sin descargar ni ejecutar APKs."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

COMMIT = "55b9127ee947795cb76944e3e55f019066c72cba"
RAIZ = Path(__file__).resolve().parents[1]
RAW = f"https://raw.githubusercontent.com/Awerkori/manga-extensoes/{COMMIT}"


def descargar_json(url):
    req = urllib.request.Request(url, headers={
        "User-Agent": "cero-X-auditoria-fuentes",
        "Accept": "application/vnd.github+json, application/json",
    })
    with urllib.request.urlopen(req, timeout=90) as response:
        if response.status != 200:
            raise RuntimeError(f"HTTP {response.status}: {url}")
        return json.load(response)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--salida", type=Path, help="Escribe el mapa fuera del APK; nunca en src/main/assets")
    args = parser.parse_args()
    indice = descargar_json(f"{RAW}/index.min.json")
    hashes = descargar_json(f"{RAW}/release-assets.json")
    urls = {}
    for pagina in range(1, 20):
        releases = descargar_json(
            f"https://api.github.com/repos/Awerkori/manga-extensoes/releases?per_page=100&page={pagina}"
        )
        for release in releases:
            for asset in release.get("assets", []):
                if asset["name"].endswith(".apk"):
                    urls[asset["name"]] = asset["browser_download_url"]
        if len(releases) < 100:
            break
    activos = {item["apk"]: urls.get(item["apk"]) for item in indice}
    faltantes_url = [name for name, url in activos.items() if not url]
    faltantes_hash = [item["pkg"] for item in indice if not hashes.get(item["pkg"], {}).get("apk", {}).get("sha256")]
    if faltantes_url or faltantes_hash:
        raise SystemExit(json.dumps({"faltantes_url": faltantes_url, "faltantes_hash": faltantes_hash}, indent=2))
    serializado = json.dumps(activos, sort_keys=True, separators=(",", ":")) + "\n"
    if args.salida:
        destino = args.salida.resolve()
        if RAIZ / "android/app/src/main" in destino.parents:
            raise SystemExit("El mapa no puede empaquetarse: elige una salida de auditoría externa")
        destino.write_text(serializado)
    print(json.dumps({
        "commit": COMMIT,
        "extensiones": len(indice),
        "urls_verificadas": len(activos),
        "hashes_verificados": len(indice) - len(faltantes_hash),
        "mapa_sha256": hashlib.sha256(serializado.encode()).hexdigest(),
        "ejecutados": 0,
    }, indent=2))


if __name__ == "__main__":
    main()
