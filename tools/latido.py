#!/usr/bin/env python3
"""Latido de cero-X: autoverificación de la memoria de trabajo y los pendientes.

Imprime HEARTBEAT_OK sólo si no queda nada obsoleto, vencido ni sin atender.
Si encuentra anomalías las clasifica por gravedad, dice cómo se arregla cada una
y termina con código 1. HEARTBEAT_OK no se afirma: se comprueba.

Este proyecto no tiene un almacén de memorias consultable. Lo que de verdad
guarda el estado es el workspace: los docs con su fecha de corte, Git con el
árbol y los commits, y work/ci/ con la evidencia. El latido revisa eso, porque
revisar «la memoria» sin un sitio donde mirarla sería exactamente el cascarón
que la regla 5 de AGENTS.md prohíbe.

No sustituye a tools/comprobar_entorno.py (presupuesto de snapshot y toolchain);
lo invoca con --entorno.

Uso:
    python3 tools/latido.py              # latido rápido, sin Gradle
    python3 tools/latido.py --entorno    # añade la auditoría de entorno
    python3 tools/latido.py --detallado  # lista también lo que pasó
"""
from __future__ import annotations

import datetime as dt
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT.parent
CI = WORK / "ci"
DOCS = ROOT / "docs"

# Los docs que declaran su propio corte en las primeras líneas.
FECHA_DECLARADA = re.compile(r"\b(\d{4}-\d{2}-\d{2})\b")
RUTA_EVIDENCIA = re.compile(r"\bwork/ci/[A-Za-z0-9._+-]+")
REGISTRO_RESPALDO = re.compile(r"^head=([0-9a-f]{7,40})[ \t]*$", re.MULTILINE)
DOC_RESPALDOS = "docs/RESPALDOS.md"
JAVA = Path("/usr/lib/jvm/java-21-openjdk-amd64/bin/java")
SDK = Path(os.environ.get("ANDROID_HOME", Path.home() / ".local/share/cerox-android-sdk"))
LIMITE_LOG_CRUDO = 1024 * 1024

BLOQUEO = "BLOQUEO"
AVISO = "AVISO"


def git(*args: str, conservar_columnas: bool = False) -> str:
    """Ejecuta git en el repositorio y devuelve la salida, vacía si falla.

    `conservar_columnas` existe por una razón concreta: --porcelain alinea el
    estado en dos columnas seguidas de un espacio, y un .strip() se come el
    espacio inicial de la PRIMERA línea, que entonces se parsea desplazada un
    carácter y recorta la ruta. La primera versión de este latido hacía eso y
    mostraba «M ocs/INVENTARIO…» en vez de «M docs/INVENTARIO…»: un verificador
    que corrompe lo que verifica es peor que no tenerlo.
    """
    resultado = subprocess.run(
        ["git", *args], cwd=ROOT, capture_output=True, text=True, check=False,
    )
    if resultado.returncode != 0:
        return ""
    return resultado.stdout.rstrip("\n") if conservar_columnas else resultado.stdout.strip()


def docs_versionados() -> list[Path]:
    """Docs y reglas que declaran estado: los que citan evidencia o pendientes."""
    rutas = [ROOT / "AGENTS.md"]
    rutas += sorted(DOCS.glob("*.md")) if DOCS.is_dir() else []
    rutas += sorted((ROOT / ".agents").rglob("SKILL.md")) if (ROOT / ".agents").is_dir() else []
    return [r for r in rutas if r.is_file()]


def comprobar_arbol(hallazgos: list[tuple[str, str, str]]) -> None:
    """Árbol sucio: trabajo sin commitear es estado que se puede perder."""
    porcelana = git("status", "--porcelain", conservar_columnas=True)
    if not porcelana:
        return
    modos: list[str] = []
    contenido: list[str] = []
    for linea in porcelana.splitlines():
        estado, ruta = linea[:2].strip(), linea[3:]
        if estado == "M" and es_solo_modo(ruta):
            modos.append(ruta)
        else:
            contenido.append(f"{estado or '?'} {ruta}")
    if contenido:
        hallazgos.append((
            BLOQUEO,
            f"Árbol sucio: {len(contenido)} cambio(s) sin commitear",
            "Revisar git diff --check y commitear la fase verde, o retirar lo que no"
            f" sirva. Cambios: {', '.join(contenido[:6])}"
            + ("…" if len(contenido) > 6 else ""),
        ))
    if modos:
        # Se informa siempre, haya o no otros cambios: perder el bit de ejecución
        # es un problema distinto y con otro arreglo, y silenciarlo cuando el
        # árbol ya está sucio dejaría sin explicar por qué gradlew no arranca.
        # Es la secuela conocida de una recuperación de workspace (regla 11).
        hallazgos.append((
            AVISO,
            f"{len(modos)} archivo(s) perdieron el bit de ejecución",
            "chmod +x " + " ".join(modos[:4]) + ("…" if len(modos) > 4 else "")
            + "; no commitear modos.",
        ))


def es_solo_modo(ruta: str) -> bool:
    """True si la única diferencia del archivo es el modo.

    Un cambio de modo puro da `0 añadidos / 0 borrados` en --numstat, así que hay
    que mirar esas dos cifras: comprobar que la salida esté vacía no sirve,
    porque nunca lo está. Primera versión de esta función lo hacía mal y
    contaba los bits de ejecución perdidos como trabajo sin commitear.
    """
    if "mode change" not in git("diff", "--summary", "--", ruta):
        return False
    columnas = git("diff", "--numstat", "--", ruta).split("\t")
    return len(columnas) >= 2 and columnas[0] == "0" and columnas[1] == "0"


def comprobar_evidencia(hallazgos: list[tuple[str, str, str]]) -> None:
    """Toda ruta work/ci/ citada en los docs tiene que existir."""
    rotas: list[str] = []
    for doc in docs_versionados():
        texto = doc.read_text(encoding="utf-8", errors="replace")
        for citada in set(RUTA_EVIDENCIA.findall(texto)):
            if not (WORK.parent / citada).exists():
                rotas.append(f"{doc.name} → {citada}")
    if rotas:
        hallazgos.append((
            BLOQUEO,
            f"{len(rotas)} referencia(s) a evidencia que no existe",
            "Restaurar el archivo (o su nombre, si se comprimió) o corregir la cita."
            " Un doc que apunta a evidencia ausente afirma algo que ya no se puede"
            f" comprobar: {', '.join(sorted(rotas)[:5])}"
            + ("…" if len(rotas) > 5 else ""),
        ))


def comprobar_fechas(hallazgos: list[tuple[str, str, str]]) -> None:
    """Un doc modificado después de su fecha de corte declarada está obsoleto."""
    obsoletos: list[str] = []
    for doc in docs_versionados():
        cabecera = "\n".join(doc.read_text(encoding="utf-8", errors="replace").splitlines()[:12])
        declaradas = FECHA_DECLARADA.findall(cabecera)
        if not declaradas:
            continue
        declarada = max(declaradas)
        ultimo_commit = git("log", "-1", "--format=%ad", "--date=short", "--",
                            str(doc.relative_to(ROOT)))
        if ultimo_commit and ultimo_commit > declarada:
            obsoletos.append(f"{doc.name} declara {declarada} y se modificó el {ultimo_commit}")
    if obsoletos:
        hallazgos.append((
            BLOQUEO,
            f"{len(obsoletos)} doc(s) con fecha de corte obsoleta",
            "Actualizar la fecha del doc o revertir el cambio que lo dejó atrás: la"
            " fecha de corte es lo que dice hasta dónde se comprobó el estado."
            " " + "; ".join(obsoletos[:3]),
        ))


def comprobar_pendientes(hallazgos: list[tuple[str, str, str]]) -> None:
    """Ningún pendiente sin criterio de salida ni comando concreto."""
    externos = DOCS / "PENDIENTES-EXTERNOS.md"
    if not externos.is_file():
        hallazgos.append((
            BLOQUEO,
            "Falta docs/PENDIENTES-EXTERNOS.md",
            "Ahí se declaran los bloqueos por aparato y credenciales; sin ese archivo"
            " los bloqueos dejan de ser visibles.",
        ))
        return
    texto = externos.read_text(encoding="utf-8", errors="replace")
    secciones = [s for s in re.split(r"^## ", texto, flags=re.M)[1:] if s.strip()]
    sin_comando = []
    for seccion in secciones:
        titulo = seccion.splitlines()[0].strip()
        if "```" not in seccion:
            sin_comando.append(titulo)
    if sin_comando:
        hallazgos.append((
            BLOQUEO,
            f"{len(sin_comando)} pendiente(s) sin comando para cerrarlo",
            "Cada pendiente externo necesita el comando exacto que lo cierra cuando"
            f" haya recurso: {', '.join(sin_comando)}",
        ))
    # Que nadie borre el registro honesto de los bloqueos.
    for clave, que in (
        ("adb devices", "el bloqueo por falta de dispositivo"),
        ("CEROX_AI_API_KEY", "el bloqueo por falta de credencial de IA"),
    ):
        if clave not in texto:
            hallazgos.append((
                BLOQUEO,
                f"PENDIENTES-EXTERNOS.md ya no declara {que}",
                f"Restaurar la declaración con su comprobación ({clave}). Un bloqueo"
                " que desaparece del doc sin haberse cerrado se convierte en un éxito"
                " falso.",
            ))

    inventario = DOCS / "INVENTARIO-CASCARONES.md"
    if inventario.is_file():
        filas = 0
        incompletas = 0
        texto_inv = inventario.read_text(encoding="utf-8", errors="replace")
        bloque = texto_inv.split("## Pendientes", 1)
        if len(bloque) == 2:
            for linea in bloque[1].splitlines():
                if not linea.startswith("|") or set(linea) <= set("|-: "):
                    continue
                celdas = [c.strip() for c in linea.strip("|").split("|")]
                if len(celdas) < 2 or celdas[0].lower() in {"elemento", "zona"}:
                    continue
                filas += 1
                if len(celdas) < 3 or not all(celdas[:3]):
                    incompletas += 1
        if filas and incompletas:
            hallazgos.append((
                BLOQUEO,
                f"{incompletas} de {filas} fila(s) de pendientes sin estado o sin salida",
                "Cada fila del inventario lleva elemento, estado honesto y siguiente"
                " criterio de salida. Una fila sin salida es un cascarón documentado.",
            ))


def comprobar_identidad(hallazgos: list[tuple[str, str, str]]) -> None:
    """Sin identidad Git el próximo commit falla; .git/config no persiste."""
    if git("config", "user.email") and git("config", "user.name"):
        return
    autor = git("log", "-1", "--format=%an <%ae>")
    hallazgos.append((
        AVISO,
        "Git sin identidad local (user.name/user.email)",
        "El próximo commit fallará. Recuperarla del historial y fijarla local:"
        f" git config user.name/user.email (último autor conocido: {autor or 'ninguno'}).",
    ))


def comprobar_ultimo_commit(hallazgos: list[tuple[str, str, str]]) -> None:
    """Un commit que toca producción sin actualizar docs pierde estado (regla 2)."""
    archivos = git("show", "--name-only", "--format=", "HEAD").splitlines()
    produccion = [a for a in archivos if a.startswith("android/app/src/main")]
    docs = [a for a in archivos if a.startswith("docs/") or a == "AGENTS.md"]
    if produccion and not docs:
        hallazgos.append((
            AVISO,
            "El último commit tocó producción sin actualizar docs",
            f"Regla 2: actualizar docs/FASES.md con cifras, bloqueos y siguiente"
            f" acción. Archivos de producción: {len(produccion)}.",
        ))


def comprobar_toolchain(hallazgos: list[tuple[str, str, str]]) -> None:
    """Sin JDK/SDK no hay fase Gradle posible; tras un reinicio es lo esperable."""
    faltan = []
    if not JAVA.is_file():
        faltan.append("JDK 21")
    if not (SDK / "platform-tools/adb").is_file():
        faltan.append("Android SDK")
    if not os.access(ROOT / "android/gradlew", os.X_OK):
        faltan.append("android/gradlew ejecutable")
    if faltan:
        hallazgos.append((
            AVISO,
            "Toolchain ausente: " + ", ".join(faltan),
            "bash scripts/preparar-entorno-android.sh (no instala emulador ni"
            " modelos). Esperable tras un reinicio del sandbox: hasta que no esté,"
            " ninguna fase Gradle es posible y no se puede afirmar ningún cierre.",
        ))


def comprobar_logs(hallazgos: list[tuple[str, str, str]]) -> None:
    """Los logs de Gradle crudos crecen sin control; la evidencia va filtrada."""
    if not CI.is_dir():
        return
    gordos = [p for p in CI.glob("*.log") if p.stat().st_size > LIMITE_LOG_CRUDO]
    if gordos:
        total = sum(p.stat().st_size for p in gordos) / 1024 / 1024
        hallazgos.append((
            AVISO,
            f"{len(gordos)} log(s) crudos de más de 1 MiB en work/ci ({total:.1f} MiB)",
            "grep -vE '^\\s+at ' y gzip -9; borrar el crudo. Un log de 49,9 MB ya se"
            " comió el presupuesto del workspace una vez.",
        ))


def comprobar_respaldo(hallazgos: list[tuple[str, str, str]]) -> None:
    """Commits posteriores al último respaldo registrado en docs/RESPALDOS.md.

    El repositorio no tiene remoto (`git remote` da 0 entradas), así que el
    archivo subido a Drive es la única copia que existe fuera del sandbox. Sin
    esta comprobación respaldar es algo que hay que acordarse de hacer, y lo que
    depende de la memoria se olvida: este proyecto ya pierde el toolchain entero
    en cada reinicio.

    Se excluyen los commits cuya única ruta tocada es el propio doc del registro.
    Sin esa exclusión la comprobación sería circular: anotar un respaldo modifica
    el doc, eso genera un commit posterior al respaldo, y el latido avisaría de
    un trabajo pendiente que es exactamente el aviso que acaba de escribir.
    """
    doc = DOCS / "RESPALDOS.md"
    if not doc.is_file():
        return
    cabezas = REGISTRO_RESPALDO.findall(doc.read_text(encoding="utf-8", errors="replace"))
    if not cabezas:
        hallazgos.append((
            AVISO,
            f"{DOC_RESPALDOS} no registra ningún respaldo",
            "Añadir una entrada head=<hash> dentro del bloque RESPALDO_REGISTRO. Sin"
            " registro no se sabe qué commit está copiado fuera del sandbox.",
        ))
        return
    ultima = cabezas[-1]
    # rev-parse --verify --quiet imprime el hash si existe y nada si no; devuelve
    # cadena vacía en ambos casos de fallo, así que sirve para distinguir «el
    # registro apunta a un commit que no está» de «no hay commits nuevos».
    if not git("rev-parse", "--verify", "--quiet", f"{ultima}^{{commit}}"):
        hallazgos.append((
            BLOQUEO,
            f"el head del último respaldo registrado ({ultima}) no existe en el historial",
            "El registro apunta a un commit ausente: o se reescribió el historial o la"
            " entrada está mal copiada. Comprobar con git cat-file -t <hash>.",
        ))
        return
    sin_respaldar: list[str] = []
    for linea in git("log", "--format=%h", f"{ultima}..HEAD").splitlines():
        corto = linea.strip()
        if not corto:
            continue
        rutas = {
            r.strip()
            for r in git("show", "--pretty=format:", "--name-only", corto).splitlines()
            if r.strip()
        }
        if rutas - {DOC_RESPALDOS}:
            sin_respaldar.append(corto)
    if sin_respaldar:
        hallazgos.append((
            AVISO,
            f"{len(sin_respaldar)} commit(s) sin respaldo fuera del sandbox:"
            f" {', '.join(sin_respaldar[:5])}" + ("…" if len(sin_respaldar) > 5 else ""),
            f"Seguir docs/RESPALDOS.md y añadir la entrada al registro; el último respaldo"
            f" cubre {ultima[:7]}.",
        ))


def comprobar_entorno(hallazgos: list[tuple[str, str, str]]) -> None:
    resultado = subprocess.run(
        [sys.executable, str(ROOT / "tools/comprobar_entorno.py")],
        cwd=ROOT, capture_output=True, text=True, check=False,
    )
    if resultado.returncode != 0:
        detalles = [l for l in resultado.stderr.splitlines() if l.startswith("ERROR")]
        hallazgos.append((
            BLOQUEO,
            "comprobar_entorno.py no da ENTORNO_OK",
            " ".join(detalles) or "Revisar presupuesto de snapshot y herramientas.",
        ))
    else:
        for linea in resultado.stdout.splitlines():
            if linea.startswith("WORKSPACE_PERSISTENTE"):
                print(f"  {linea}")


def main(argv: list[str]) -> int:
    hallazgos: list[tuple[str, str, str]] = []
    print("LATIDO cero-X", dt.datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S %Z"))
    print(f"  HEAD {git('log', '-1', '--format=%h %s') or 'sin commits'}")

    comprobar_arbol(hallazgos)
    comprobar_evidencia(hallazgos)
    comprobar_fechas(hallazgos)
    comprobar_pendientes(hallazgos)
    comprobar_identidad(hallazgos)
    comprobar_ultimo_commit(hallazgos)
    comprobar_toolchain(hallazgos)
    comprobar_logs(hallazgos)
    comprobar_respaldo(hallazgos)
    if "--entorno" in argv:
        comprobar_entorno(hallazgos)

    bloqueos = [h for h in hallazgos if h[0] == BLOQUEO]
    avisos = [h for h in hallazgos if h[0] == AVISO]

    if not hallazgos:
        if "--detallado" in argv:
            print("  sin anomalías: árbol, evidencia, fechas, pendientes, identidad,")
            print("  último commit, toolchain, logs y respaldo revisados")
        print("HEARTBEAT_OK")
        return 0

    for gravedad, grupo in ((BLOQUEO, bloqueos), (AVISO, avisos)):
        if not grupo:
            continue
        print(f"\n{gravedad} ({len(grupo)})")
        for _, que, arreglo in grupo:
            print(f"  - {que}")
            print(f"    → {arreglo}")
    print(
        f"\nHEARTBEAT_FALLIDO bloqueos={len(bloqueos)} avisos={len(avisos)}"
        " · atender cada anomalía antes de abrir fase nueva",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
