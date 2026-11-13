#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Audita las fuentes de referencia del objetivo todo-en-uno en cuatro ejes.

Fase F2 de docs/OBJETIVO-TODO-EN-UNO.md. Los cuatro ejes:

  1. manifiestos  — permisos declarados y componentes exportados (lo que la app pide)
  2. apis         — qué APIs usan de verdad, cruzado contra lo que declaran
  3. endpoints    — a qué hosts hablan
  4. telemetria   — qué SDK de rastreo o de fallos fusionan sus dependencias

Se aplica también a cero-X-app (--incluir-cero-x, activo por defecto), porque el eje 2 es el
que responde la pregunta incómoda: un permiso declarado y no usado es superficie que se regala,
y un uso sin permiso declarado es un fallo que sólo aparece en el aparato de alguien.

Todo es texto sobre los clones de /var/tmp/referencias (los baja
scripts/bajar-referencias.sh). No necesita aparato, no compila nada y no ejecuta código de
terceros: sólo lo lee.

Uso:
    tools/auditar-referencias.py                       # los 4 ejes, tabla por pantalla
    tools/auditar-referencias.py --ejes apis,telemetria
    tools/auditar-referencias.py --tsv-dir DIR         # un TSV por eje + una ficha por app
    tools/auditar-referencias.py --listar              # qué se auditaría, sin auditar

Lo que NO afirma: que un permiso se use bien, que un host sea seguro, ni que la ausencia de
una cadena pruebe la ausencia de telemetría —un SDK puede llegar transitivo y sin dejar su
nombre en un build.gradle. Para eso hace falta el árbol de dependencias resuelto, que sólo
existe si el proyecto compila.
"""
from __future__ import annotations

import argparse
import csv
import re
import sys
from collections import Counter
from pathlib import Path

REFERENCIAS = Path("/var/tmp/referencias")
RAIZ = Path(__file__).resolve().parents[1]
CERO_X = RAIZ / "android/app/src/main"
# Los ejes 1-3 miran src/main (manifiesto, código, endpoints). Pero el eje 4 vive en los
# ficheros de build, que en cero-X están un nivel más arriba (android/app/build.gradle.kts,
# android/gradle/libs.versions.toml). Auditar src/main dejaba el eje 4 ciego para cero-X:
# reportaba «0 ficheros de build» y «ninguna telemetría» sin haber mirado nada. Las
# referencias se clonan con su raíz de repo, que sí trae los .gradle, así que para ellas
# raíz de código y de build coinciden.
CERO_X_BUILD = RAIZ / "android"

EXCLUIR_RUTA = ("/build/", "/.git/", "/node_modules/", "/.gradle/", "/test/", "/androidTest/")
EXTENSIONES_CODIGO = {".kt", ".java"}
EXTENSIONES_GRADLE = {".gradle", ".kts", ".toml", ".properties"}

# Permisos que un usuario notaría.
SENSIBLES = {
    "INTERNET", "ACCESS_NETWORK_STATE", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE",
    "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_VISUAL_USER_SELECTED",
    "POST_NOTIFICATIONS", "QUERY_ALL_PACKAGES", "REQUEST_INSTALL_PACKAGES",
    "MANAGE_EXTERNAL_STORAGE", "SYSTEM_ALERT_WINDOW", "FOREGROUND_SERVICE", "WAKE_LOCK",
    "RECEIVE_BOOT_COMPLETED", "CAMERA", "RECORD_AUDIO", "PACKAGE_USAGE_STATS",
    "SCHEDULE_EXACT_ALARM", "USE_EXACT_ALARM", "ACCESS_FINE_LOCATION", "BLUETOOTH_CONNECT",
    "REQUEST_DELETE_PACKAGES", "CHANGE_WIFI_STATE", "ACCESS_WIFI_STATE", "VIBRATE",
}

# Eje 2: permiso → patrones de código que lo ejercen. Si el permiso está declarado y ningún
# patrón aparece, es un permiso huérfano; si el patrón aparece y el permiso no, es un uso sin
# declarar que sólo falla en el aparato del usuario.
PERMISO_A_API: dict[str, tuple[str, ...]] = {
    "REQUEST_INSTALL_PACKAGES": (
        r"PackageInstaller", r"ACTION_INSTALL_PACKAGE", r"application/vnd\.android\.package-archive",
        r"canRequestPackageInstalls",
    ),
    "REQUEST_DELETE_PACKAGES": (r"ACTION_DELETE", r"ACTION_UNINSTALL_PACKAGE", r"PackageInstaller"),
    "QUERY_ALL_PACKAGES": (
        r"getInstalledPackages", r"getInstalledApplications", r"queryIntentActivities",
    ),
    "MANAGE_EXTERNAL_STORAGE": (
        r"isExternalStorageManager", r"MANAGE_APP_ALL_FILES_ACCESS", r"ACTION_MANAGE_ALL_FILES",
    ),
    "POST_NOTIFICATIONS": (r"NotificationManagerCompat", r"NotificationChannel", r"\.notify\("),
    "RECEIVE_BOOT_COMPLETED": (r"BOOT_COMPLETED",),
    "SYSTEM_ALERT_WINDOW": (r"TYPE_APPLICATION_OVERLAY", r"canDrawOverlays", r"TYPE_SYSTEM_ALERT"),
    "CAMERA": (r"CameraManager", r"ProcessCameraProvider", r"Camera2", r"MediaRecorder"),
    "RECORD_AUDIO": (r"AudioRecord", r"MediaRecorder", r"AudioSource\.MIC"),
    "READ_MEDIA_IMAGES": (r"MediaStore\.Images", r"PickVisualMedia", r"ACTION_OPEN_DOCUMENT"),
    "READ_MEDIA_VIDEO": (r"MediaStore\.Video", r"PickVisualMedia", r"ACTION_OPEN_DOCUMENT"),
    "READ_EXTERNAL_STORAGE": (r"MediaStore", r"EXTERNAL_CONTENT_URI", r"getExternalStorageDirectory"),
    "WRITE_EXTERNAL_STORAGE": (r"getExternalStorage", r"EXTERNAL_STORAGE", r"MediaStore"),
    "SCHEDULE_EXACT_ALARM": (r"setExact", r"canScheduleExactAlarms", r"setAlarmClock"),
    "USE_EXACT_ALARM": (r"setExact", r"canScheduleExactAlarms", r"setAlarmClock"),
    "FOREGROUND_SERVICE": (r"startForeground", r"foregroundServiceType", r"ForegroundInfo"),
    "WAKE_LOCK": (r"WakeLock", r"newWakeLock", r"PARTIAL_WAKE_LOCK"),
    "PACKAGE_USAGE_STATS": (r"UsageStatsManager", r"PACKAGE_USAGE_STATS", r"queryUsageStats"),
    "ACCESS_FINE_LOCATION": (r"FusedLocationProvider", r"LocationManager", r"getLastKnownLocation"),
    "BLUETOOTH_CONNECT": (r"BluetoothAdapter", r"BluetoothDevice", r"BluetoothGatt"),
    "CHANGE_WIFI_STATE": (r"WifiManager", r"setWifiEnabled"),
    "ACCESS_WIFI_STATE": (r"WifiManager", r"getConnectionInfo"),
    "INTERNET": (r"OkHttpClient", r"HttpURLConnection", r"Retrofit", r"Ktor", r"HttpClient"),
}

# Eje 4: SDK de telemetría, analítica o reporte de fallos. Cada entrada es un patrón que
# sólo casa con el SDK de verdad —su coordenada de módulo o su paquete— y no con una
# subcadena inocente. Medido el 2026-11-13: con subcadenas sueltas, «adjust» casaba con
# android:windowSoftInputMode="adjustResize" en las ONCE apps (el SDK real es com.adjust y
# no está en ninguna) y «umeng» casaba con «Diumenge», que es domingo en catalán, dentro de
# un strings.xml de Kototoro. Dos falsos positivos que habrían acabado en un doc.
TELEMETRIA: dict[str, tuple[str, str]] = {
    "firebase-analytics": (r"com\.google\.firebase:firebase-analytics|firebase\.analytics",
                           "analítica de Google"),
    "firebase-crashlytics": (r"com\.google\.firebase:firebase-crashlytics|firebase\.crashlytics",
                             "reporte de fallos de Google"),
    "firebase-perf": (r"com\.google\.firebase:firebase-perf", "rendimiento de Google"),
    "firebase-messaging": (r"com\.google\.firebase:firebase-messaging", "push de Google"),
    "google-services": (r"com\.google\.gms\.google-services|google-services\.json",
                        "plugin que cablea los servicios de Google"),
    "acra": (r"ch\.acra|org\.acra|acra-dialog|acra-http|ACRA_URI",
             "reporte de fallos abierto (ACRA)"),
    "sentry": (r"io\.sentry|sentry-android", "reporte de fallos y rendimiento"),
    "bugsnag": (r"com\.bugsnag", "reporte de fallos"),
    "posthog": (r"com\.posthog|posthog-android", "analítica de producto"),
    "mixpanel": (r"com\.mixpanel", "analítica de producto"),
    "amplitude": (r"com\.amplitude", "analítica de producto"),
    "appsflyer": (r"com\.appsflyer", "atribución de instalaciones"),
    "adjust": (r"com\.adjust|adjust-sdk|AdjustSdk", "atribución de instalaciones"),
    "countly": (r"ly\.count\.android", "analítica"),
    "umeng": (r"com\.umeng|umeng-analytics", "analítica (mercado chino)"),
    "applovin": (r"com\.applovin", "publicidad"),
    "ironsource": (r"com\.ironsource", "publicidad"),
    "unity-ads": (r"com\.unity3d\.ads", "publicidad"),
    "admob": (r"play-services-ads|com\.google\.android\.gms\.ads", "publicidad de Google"),
}
# Qué se mira: sólo ficheros que deciden qué entra en el APK. Fuera quedan res/ e i18n/
# (traducciones, donde «Diumenge» hacía de SDK chino) y .idea/ (configuración del IDE, donde
# Kototoro mencionaba «Firebase Crashlytics» sin tenerlo).
FICHEROS_BUILD = {".gradle", ".kts", ".toml", ".properties"}
RUTA_RUIDO_BUILD = ("/res/", "/i18n/", "/.idea/", "/fastlane/", "/docs/", "/metadata/")
# Aplicar el plugin no es declararlo en el catálogo de versiones: libs.versions.toml lista
# lo disponible, y sólo el build.gradle dice lo que entra.
APLICACION_PLUGIN = re.compile(
    r"apply\(libs\.plugins\.(?:google\.services|firebase)|"
    r"id\(\s*[\"']com\.google\.(?:gms\.google-services|firebase\.crashlytics)|"
    r"apply plugin: ['\"]com\.google\.gms\.google-services")

COMPONENTES = ("activity", "service", "receiver", "provider")
URL = re.compile(r"https?://([A-Za-z0-9._-]+)")
# Hosts que son ruido: esquemas, documentación, ejemplos y la propia infraestructura de Android.
HOSTS_RUIDO = {
    "schemas.android.com", "www.w3.org", "w3.org", "ns.adobe.com", "apache.org",
    "www.apache.org", "json.org", "gradle.org", "docs.gradle.org", "kotlinlang.org",
    "developer.android.com", "developers.google.com", "github.com", "raw.githubusercontent.com",
    "api.github.com", "shields.io", "img.shields.io", "badgen.net", "example.com",
    "localhost", "play.google.com", "fonts.google.com", "fonts.gstatic.com",
    "maven.google.com", "repo.maven.apache.org", "jitpack.io", "plugins.gradle.org",
    "discord.gg", "discord.com", "t.me", "telegram.org", "twitter.com", "x.com",
    "example.org", "example.com", "goo.gle", "stackoverflow.com", "registry.npmjs.org",
    "registry.npmmirror.com", "npmjs.com", "nodejs.org", "mozilla.org", "opensource.org",
    "spdx.org", "creativecommons.org", "gnu.org", "google.com", "www.google.com",
    "android.com", "www.android.com", "material.io", "jetbrains.com", "kotlinlang.com",
    "facebook.com", "reddit.com", "weblate.org", "hosted.weblate.org", "crowdin.com",
    "translate.google.com", "ko-fi.com", "buymeacoffee.com", "patreon.com", "paypal.com",
}


def recorrer(raiz: Path, extensiones: set[str]) -> list[Path]:
    """Ficheros con esas extensiones, fuera de build, .git y tests."""
    out: list[Path] = []
    for p in raiz.rglob("*"):
        if not p.is_file() or p.suffix not in extensiones:
            continue
        s = str(p)
        if any(x in s for x in EXCLUIR_RUTA):
            continue
        out.append(p)
    return out


def leer(p: Path) -> str:
    try:
        return p.read_text(errors="replace")
    except OSError:
        return ""


def manifiestos(raiz: Path) -> list[Path]:
    return [p for p in raiz.rglob("AndroidManifest.xml")
            if "/build/" not in str(p) and "/.git/" not in str(p)]


def eje_manifiestos(raiz: Path) -> dict:
    permisos: set[str] = set()
    exportados = componentes = 0
    for m in manifiestos(raiz):
        texto = leer(m)
        permisos |= set(re.findall(r'uses-permission[^>]*android:name="([^"]+)"', texto))
        # también la forma <uses-permission-sdk-23>
        permisos |= set(re.findall(r'uses-permission-sdk-23[^>]*android:name="([^"]+)"', texto))
        for etiqueta in COMPONENTES:
            for bloque in re.findall(rf"<{etiqueta}\b[^>]*>", texto, re.S):
                componentes += 1
                if 'android:exported="true"' in bloque:
                    exportados += 1
    cortos = {p.split(".")[-1] for p in permisos}
    sensibles = sorted(x for x in cortos if x in SENSIBLES)
    return {
        "manifiestos": len(manifiestos(raiz)),
        "permisos": len(cortos),
        "componentes": componentes,
        "exportados": exportados,
        "sensibles": sensibles,
        "cortos": cortos,
    }


def eje_apis(raiz: Path, permisos_cortos: set[str]) -> dict:
    """Cruza lo declarado contra lo que el código ejercita de verdad.

    Busca en tres sitios y los reporta por separado, porque no valen lo mismo:
    código (el permiso se ejercita), recursos XML (un servicio declarado foreground,
    un array de permisos a pedir) y manifiesto (sólo la declaración). Medido el
    2026-11-13: sin esta separación, `image/*` en una cabecera HTTP `Accept` y
    `foregroundServiceType` en el propio manifiesto contaban como «uso» y tapaban
    permisos que nadie ejercita.
    """
    fuentes = recorrer(raiz, EXTENSIONES_CODIGO)
    recursos = [p for p in recorrer(raiz, {".xml"})
                if p.name != "AndroidManifest.xml" and "/res/" in str(p)]
    manifs = manifiestos(raiz)
    texto_codigo = "\n".join(leer(p) for p in fuentes)
    texto_recursos = "\n".join(leer(p) for p in recursos)
    texto_manifiesto = "\n".join(leer(p) for p in manifs)

    def ejercido(texto: str, perm: str) -> bool:
        return any(re.search(pat, texto) for pat in PERMISO_A_API.get(perm, ()))

    en_codigo = sorted(p for p in permisos_cortos if ejercido(texto_codigo, p))
    en_recursos = sorted(p for p in permisos_cortos
                         if p not in en_codigo and ejercido(texto_recursos, p))
    # lo que el código ejercita sin que el manifiesto lo declare
    usados_codigo = {perm for perm in PERMISO_A_API if ejercido(texto_codigo, perm)}
    sin_declarar = sorted(usados_codigo - permisos_cortos)
    huerfanos = sorted(permisos_cortos & set(PERMISO_A_API) - set(en_codigo) - set(en_recursos))
    return {
        "fuentes": len(fuentes),
        "recursos": len(recursos),
        "permisos_con_uso": en_codigo,
        "uso_solo_en_recursos": en_recursos,
        "huerfanos": huerfanos,
        "sin_declarar": sin_declarar,
    }


# Rutas de traducción/strings de usuario. Una URL aquí suele ser un valor por defecto
# editable (el FlareSolverr 192.168.1.202 de AniZen sale 22 veces, una por idioma) o un
# ejemplo de documentación, no un host al que la app llame. Medido el 2026-11-13: sin
# excluirlos, AniZen contaba 92 hosts y houri 109 con ruido de i18n y comentarios.
RUTA_TRADUCCION = ("/i18n", "moko-resources", "/values", "strings.xml", "/translations/",
                   "/fastlane/", "/metadata/", "/docs/")


def eje_endpoints(raiz: Path) -> dict:
    hosts: Counter[str] = Counter()
    ficheros = [p for p in recorrer(raiz, EXTENSIONES_CODIGO | EXTENSIONES_GRADLE | {".xml", ".json"})
                if not any(x in str(p) for x in RUTA_TRADUCCION)]
    for p in ficheros:
        for h in URL.findall(leer(p)):
            h = h.lower().strip(".")
            if h in HOSTS_RUIDO or len(h) < 4:
                continue
            # TLD reservados (RFC 2606) y no enrutables: fixtures de prueba y ejemplos de
            # comentarios, no hosts reales. Kototoro tenía 61 entre fixture.invalid y
            # example.test; houri tenía book.internal(24) de un comentario del visor.
            if h.endswith((".invalid", ".test", ".example", ".localhost", ".internal")):
                continue
            hosts[h] += 1
    propios = sorted((h for h in hosts if "github" in h or "gitlab" in h), key=lambda x: -hosts[x])
    return {
        "hosts": len(hosts),
        "top": hosts.most_common(12),
        "propios": propios[:5],
        "ficheros": len(ficheros),
    }


def eje_telemetria(raiz: Path) -> dict:
    """Qué SDK de rastreo entra en el APK, distinguiendo declarado de aplicado.

    Tres niveles, porque no significan lo mismo:
      catalogo    — aparece en un fichero .toml de versiones: está disponible, no necesariamente entra
      aplicado    — un build.gradle aplica el plugin o declara la dependencia: entra
      condicional — la aplicación está dentro de un bloque `if`, o sea que hay una variante sin ella
    """
    ficheros = [p for p in recorrer(raiz, FICHEROS_BUILD | {".xml"})
                if not any(x in str(p) for x in RUTA_RUIDO_BUILD)]
    catalogo: set[str] = set()
    aplicado: set[str] = set()
    condicional: set[str] = set()
    donde: list[tuple[str, str, str]] = []
    for p in ficheros:
        texto = leer(p)
        bajo = texto.lower()
        lineas = texto.splitlines()
        for nombre, (patron, descripcion) in TELEMETRIA.items():
            if not re.search(patron, bajo):
                continue
            es_catalogo = p.suffix == ".toml"
            (catalogo if es_catalogo else aplicado).add(nombre)
            n = next((i + 1 for i, l in enumerate(lineas)
                      if re.search(patron, l.lower())), "?")
            donde.append((nombre, descripcion, f"{p.relative_to(raiz)}:{n}"))
            # ¿la aplicación queda dentro de un condicional? Mirar las 8 líneas anteriores
            # en un fichero de build es una heurística: se declara como tal y se confirma
            # leyendo el fichero, que es lo que se hizo con houri.
            if not es_catalogo and isinstance(n, int):
                contexto = "\n".join(lineas[max(0, n - 9):n])
                if re.search(r"\bif\s*\(|hasProperty\(|project\.findProperty", contexto):
                    condicional.add(nombre)
    # un SDK sólo en el catálogo no entra en el APK
    solo_catalogo = sorted(catalogo - aplicado)
    return {
        "sdk": sorted(aplicado),
        "solo_catalogo": solo_catalogo,
        "condicional": sorted(condicional),
        "detalle": sorted(set(donde)),
        "ficheros_build": len([p for p in ficheros if p.suffix in FICHEROS_BUILD]),
    }


def auditar(raiz: Path, nombre: str, ejes: set[str], raiz_build: Path | None = None) -> dict:
    ficha: dict = {"nombre": nombre, "ruta": str(raiz)}
    # Los permisos declarados se calculan SIEMPRE, se pida o no el eje de manifiestos:
    # el eje 2 no significa nada sin ellos. Con `--ejes apis` a secas la herramienta
    # comparaba contra un conjunto vacío y declaraba «sin declarar» permisos que sí lo
    # están, incluido INTERNET. Los ejes tienen que ser independientes de verdad.
    declarados = eje_manifiestos(raiz)
    if "manifiestos" in ejes:
        ficha["manifiestos"] = declarados
    if "apis" in ejes:
        ficha["apis"] = eje_apis(raiz, declarados["cortos"])
    if "endpoints" in ejes:
        ficha["endpoints"] = eje_endpoints(raiz)
    if "telemetria" in ejes:
        # El eje 4 se ejecuta sobre la raíz de build, que en cero-X es android/ y en las
        # referencias coincide con la raíz del clon. Ver el comentario de CERO_X_BUILD.
        rb = raiz_build or raiz
        ficha["telemetria"] = eje_telemetria(rb)
        ficha["telemetria"]["ruta_build"] = str(rb)
    return ficha


def escribir_tsv(destino: Path, filas: list[dict], columnas: list[str]) -> None:
    destino.parent.mkdir(parents=True, exist_ok=True)
    with destino.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=columnas, delimiter="\t")
        w.writeheader()
        for f in filas:
            w.writerow({c: f.get(c, "") for c in columnas})


def escribir_ficha(destino: Path, f: dict) -> None:
    destino.parent.mkdir(parents=True, exist_ok=True)
    lineas = [f"# Ficha de auditoría — {f['nombre']}", "", f"Ruta auditada: `{f['ruta']}`", ""]
    if "manifiestos" in f:
        m = f["manifiestos"]
        lineas += ["## Eje 1 — manifiesto", "",
                   f"- manifiestos: {m['manifiestos']}",
                   f"- permisos distintos: {m['permisos']}",
                   f"- componentes: {m['componentes']} ({m['exportados']} exportados)",
                   f"- sensibles ({len(m['sensibles'])}): {', '.join(m['sensibles']) or 'ninguno'}", ""]
    if "apis" in f:
        a = f["apis"]
        lineas += ["## Eje 2 — APIs usadas frente a permisos declarados", "",
                   f"- fuentes analizadas: {a['fuentes']}",
                   f"- permisos ejercidos en código: {', '.join(a['permisos_con_uso']) or 'ninguno'}",
                   f"- ejercidos sólo en recursos XML (no en código): "
                   f"{', '.join(a['uso_solo_en_recursos']) or 'ninguno'}",
                   f"- **permisos huérfanos** (declarados, sin uso detectado): "
                   f"{', '.join(a['huerfanos']) or 'ninguno'}",
                   f"- **usos sin permiso declarado**: {', '.join(a['sin_declarar']) or 'ninguno'}",
                   "",
                   "Que no aparezca la cadena no prueba que el permiso sobre: puede ejercerlo una",
                   "dependencia o un recurso XML. Y al revés: un uso sin declarar puede venir de un",
                   "manifiesto fusionado de otra biblioteca. Ambos se confirman leyendo, no grepando.", ""]
    if "endpoints" in f:
        e = f["endpoints"]
        lineas += ["## Eje 3 — endpoints", "",
                   f"- hosts distintos fuera del ruido: {e['hosts']}",
                   f"- de su propia infraestructura: {', '.join(e['propios']) or 'ninguno'}", "",
                   "| host | menciones |", "|---|---:|"]
        lineas += [f"| `{h}` | {n} |" for h, n in e["top"]]
        lineas.append("")
    if "telemetria" in f:
        t = f["telemetria"]
        lineas += ["## Eje 4 — telemetría en dependencias", "",
                   f"- ficheros de build analizados: {t['ficheros_build']}",
                   f"- **SDK que entran en el APK**: {', '.join(t['sdk']) or 'ninguno'}",
                   f"- sólo declarados en el catálogo de versiones (no entran): "
                   f"{', '.join(t['solo_catalogo']) or 'ninguno'}",
                   f"- aplicados dentro de un condicional (hay variante sin ellos): "
                   f"{', '.join(t['condicional']) or 'ninguno'}", ""]
        if t["detalle"]:
            lineas += ["| SDK | qué es | dónde |", "|---|---|---|"]
            lineas += [f"| `{k}` | {d} | `{w}` |" for k, d, w in t["detalle"][:25]]
        lineas += ["",
                   "Cero coincidencias **no** prueba ausencia: un SDK puede llegar transitivo sin",
                   "dejar su nombre en un fichero de build. Probarlo exige el árbol de dependencias",
                   "resuelto, que sólo existe si el proyecto compila.", ""]
    destino.write_text("\n".join(lineas), encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--referencias", default=str(REFERENCIAS))
    ap.add_argument("--ejes", default="manifiestos,apis,endpoints,telemetria")
    ap.add_argument("--tsv-dir", help="escribir un TSV por eje y una ficha por app en esta carpeta")
    ap.add_argument("--listar", action="store_true", help="qué se auditaría, sin auditar")
    ap.add_argument("--sin-cero-x", action="store_true", help="no auditar cero-X-app")
    args = ap.parse_args()

    ejes = {e.strip() for e in args.ejes.split(",") if e.strip()}
    conocidos = {"manifiestos", "apis", "endpoints", "telemetria"}
    if not ejes <= conocidos:
        print(f"ERROR ejes desconocidos: {sorted(ejes - conocidos)}", file=sys.stderr)
        return 2

    raiz = Path(args.referencias)
    objetivos: list[tuple[str, Path, Path]] = []
    if raiz.is_dir():
        for d in sorted(x for x in raiz.iterdir() if x.is_dir()):
            # El clon de AnantPlay/android se llama "android", que como nombre de ficha no
            # dice nada. El alias sale del TSV de referencias, que trae el owner/repo real.
            nombre = d.name
            tsv = Path.home() / "work/ci/frankie/referencias.tsv"
            if nombre == "android" and tsv.is_file():
                for linea in tsv.read_text().splitlines()[1:]:
                    celda = linea.split("\t")
                    if len(celda) >= 1 and celda[0].endswith("/android"):
                        nombre = celda[0].split("/")[0]
                        break
            # referencia: la raíz del clon trae los .gradle, así que build == código
            objetivos.append((nombre, d, d))
    else:
        print(f"AVISO: no existe {raiz}; ejecuta scripts/bajar-referencias.sh", file=sys.stderr)
    if not args.sin_cero_x:
        if CERO_X.is_dir():
            objetivos.append((">>> cero-X-app", CERO_X, CERO_X_BUILD))
        else:
            print(f"ERROR: falta {CERO_X}", file=sys.stderr)
            return 1

    if args.listar:
        print(f"  {len(objetivos)} objetivos · ejes: {', '.join(sorted(ejes))}")
        for n, r, _ in objetivos:
            print(f"    {n:<26}{r}")
        return 0
    if not objetivos:
        print("ERROR sin nada que auditar", file=sys.stderr)
        return 1

    fichas = [auditar(r, n, ejes, rb) for n, r, rb in objetivos]

    # ---- tablas por pantalla ----
    if "manifiestos" in ejes:
        print(f"\n  EJE 1 · {'fuente':<24}{'manif':>6}{'perm':>6}{'comp':>6}{'export':>8}{'sensibles':>11}")
        for f in fichas:
            m = f["manifiestos"]
            print(f"          {f['nombre']:<24}{m['manifiestos']:>6}{m['permisos']:>6}"
                  f"{m['componentes']:>6}{m['exportados']:>8}{len(m['sensibles']):>11}")
    if "apis" in ejes:
        print(f"\n  EJE 2 · {'fuente':<24}{'huérfanos':>10}{'sin declarar':>14}")
        for f in fichas:
            a = f["apis"]
            print(f"          {f['nombre']:<24}{len(a['huerfanos']):>10}{len(a['sin_declarar']):>14}")
            if a["huerfanos"]:
                print(f"              huérfanos: {', '.join(a['huerfanos'])}")
            if a["sin_declarar"]:
                print(f"              sin declarar: {', '.join(a['sin_declarar'])}")
    if "endpoints" in ejes:
        print(f"\n  EJE 3 · {'fuente':<24}{'hosts':>7}  tres primeros")
        for f in fichas:
            e = f["endpoints"]
            top = ", ".join(f"{h}({n})" for h, n in e["top"][:3])
            print(f"          {f['nombre']:<24}{e['hosts']:>7}  {top}")
    if "telemetria" in ejes:
        print(f"\n  EJE 4 · {'fuente':<24}telemetría en dependencias")
        for f in fichas:
            t = f["telemetria"]
            cond = f" · condicional: {', '.join(t['condicional'])}" if t["condicional"] else ""
            solo = f" · sólo catálogo: {', '.join(t['solo_catalogo'])}" if t["solo_catalogo"] else ""
            print(f"          {f['nombre']:<24}{', '.join(t['sdk']) or 'ninguno'}{cond}{solo}")

    if args.tsv_dir:
        out = Path(args.tsv_dir)
        if "manifiestos" in ejes:
            escribir_tsv(out / "f2-manifiestos.tsv", [
                {"fuente": f["nombre"], **{k: v for k, v in f["manifiestos"].items()
                                           if k not in ("sensibles", "cortos")},
                 "sensibles": ";".join(f["manifiestos"]["sensibles"])}
                for f in fichas],
                ["fuente", "manifiestos", "permisos", "componentes", "exportados", "sensibles"])
        if "apis" in ejes:
            escribir_tsv(out / "f2-apis.tsv", [
                {"fuente": f["nombre"], "fuentes_codigo": f["apis"]["fuentes"],
                 "con_uso": ";".join(f["apis"]["permisos_con_uso"]),
                 "solo_recursos": ";".join(f["apis"]["uso_solo_en_recursos"]),
                 "huerfanos": ";".join(f["apis"]["huerfanos"]),
                 "sin_declarar": ";".join(f["apis"]["sin_declarar"])}
                for f in fichas],
                ["fuente", "fuentes_codigo", "con_uso", "solo_recursos", "huerfanos",
                 "sin_declarar"])
        if "endpoints" in ejes:
            escribir_tsv(out / "f2-endpoints.tsv", [
                {"fuente": f["nombre"], "hosts": f["endpoints"]["hosts"],
                 "propios": ";".join(f["endpoints"]["propios"]),
                 "top": ";".join(f"{h}:{n}" for h, n in f["endpoints"]["top"])}
                for f in fichas], ["fuente", "hosts", "propios", "top"])
        if "telemetria" in ejes:
            escribir_tsv(out / "f2-telemetria.tsv", [
                {"fuente": f["nombre"], "ficheros_build": f["telemetria"]["ficheros_build"],
                 "sdk_aplicados": ";".join(f["telemetria"]["sdk"]),
                 "solo_catalogo": ";".join(f["telemetria"]["solo_catalogo"]),
                 "condicional": ";".join(f["telemetria"]["condicional"]),
                 "detalle": ";".join(f"{k}@{w}" for k, _, w in f["telemetria"]["detalle"])}
                for f in fichas],
                ["fuente", "ficheros_build", "sdk_aplicados", "solo_catalogo", "condicional",
                 "detalle"])
        for f in fichas:
            nombre = f["nombre"].lstrip("> ").strip()
            escribir_ficha(out / "fichas" / f"{nombre}.md", f)
        print(f"\n  evidencia: {len(fichas)} fichas y hasta 4 TSV en {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
