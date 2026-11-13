---
name: cerox-latido
description: Usar al empezar una sesión de cero-X, antes de commitear y antes de dar una fase por cerrada. Verifica que docs, evidencia, pendientes, árbol de Git y entorno siguen coherentes entre sí. Imprime HEARTBEAT_OK sólo si no queda nada obsoleto, vencido ni sin atender.
---

# Latido de cero-X

```bash
python3 tools/latido.py              # rápido, sin Gradle
python3 tools/latido.py --entorno    # añade tools/comprobar_entorno.py
python3 tools/latido.py --detallado  # lista también lo que pasó sin anomalías
```

Salida limpia: la última línea es exactamente `HEARTBEAT_OK` y el código es 0.
Con anomalías: las agrupa por gravedad, dice cómo arreglar cada una, escribe
`HEARTBEAT_FALLIDO bloqueos=N avisos=M` en stderr y termina en 1.

## Qué es la «memoria» aquí, y por qué no se revisa otra cosa

Este proyecto **no tiene un almacén de memorias consultable**. Lo que guarda el
estado es el workspace, y el latido revisa eso:

| dónde vive el estado | qué se pierde si no se revisa |
|---|---|
| `docs/*.md` con su fecha de corte | hasta dónde se comprobó realmente cada cosa |
| `work/ci/` con logs, hashes y resúmenes | la evidencia de lo que se afirma |
| `git status` y el último commit | trabajo sin commitear y docs que se quedaron atrás |
| `docs/PENDIENTES-EXTERNOS.md` y `INVENTARIO-CASCARONES.md` | qué sigue bloqueado y con qué comando se cierra |
| toolchain y presupuesto de snapshot | si la fase siguiente es siquiera posible |
| `docs/RESPALDOS.md` con su registro | qué commit está copiado fuera del sandbox; el repo no tiene remoto |

Revisar «la memoria» sin un sitio donde mirarla sería un cascarón: un control que
promete una comprobación y no la hace. La regla 5 de `AGENTS.md` prohíbe eso en la
interfaz; aquí aplica igual.

## Regla de oro

**Nunca escribir `HEARTBEAT_OK` sin haber ejecutado el script.** Es la respuesta
exacta que el usuario espera ver, y por eso mismo es la más fácil de fingir. Si no
se ejecutó, se dice que no se ejecutó.

Lo mismo al revés: un `HEARTBEAT_OK` no certifica la fase. Certifica coherencia de
estado —docs, evidencia, pendientes, árbol— y nada sobre pruebas, aparato ni
validación física. Eso lo cubre el cierre mínimo de la regla 9.

## Cuándo ejecutarlo

1. **Al empezar una sesión**, antes de escribir código: dice si el entorno se
   perdió en el reinicio y si quedó trabajo sin commitear de la sesión anterior.
2. **Antes de commitear**: evita commitear con fechas de corte obsoletas o
   evidencia citada que ya no existe.
3. **Antes de dar una fase por cerrada** y al terminar, junto a
   `tools/comprobar_entorno.py`.
4. **Después de limpiar el entorno**: la limpieza mueve y comprime archivos, y es
   fácil romper una ruta citada en los docs.

## Las dos gravedades

- **BLOQUEO** — atender antes de abrir fase nueva. Es estado que miente o que se
  puede perder: árbol sucio con contenido, evidencia citada que no existe, doc
  modificado después de su fecha de corte, pendiente sin criterio de salida,
  un bloqueo que desapareció del doc sin cerrarse.
- **AVISO** — recuperable y, tras un reinicio del sandbox, esperable. No impide
  trabajo de docs o de herramientas, pero **sí impide afirmar cualquier cierre
  Android**: toolchain ausente, bits de ejecución perdidos, identidad Git en
  blanco, logs crudos gordos, el último commit tocó producción sin actualizar
  docs.

## Anomalías conocidas y su arreglo

| anomalía | arreglo | por qué pasa |
|---|---|---|
| `N archivo(s) perdieron el bit de ejecución` | `chmod +x` sobre cada uno; **no commitear modos** | una recuperación de workspace los quita; `git status` lo muestra como `mode change 100755 => 100644` y no es ruido |
| `Toolchain ausente: JDK 21, Android SDK` | `bash scripts/preparar-entorno-android.sh` | el reinicio borra JDK, SDK, `/opt/cerox` y el swap; la primera corrida Gradle después re-descarga y es lenta, no es un fallo |
| `Git sin identidad local` | leer el último autor y fijarlo local | `.git/config` no persiste entre reinicios; sin esto el commit falla |
| `referencia(s) a evidencia que no existe` | restaurar el archivo o su nombre, o corregir la cita | pasa al comprimir un log que los docs citaban sin `.gz` |
| `doc(s) con fecha de corte obsoleta` | actualizar la fecha en el mismo commit | el doc se modificó y su cabecera seguía en la fecha anterior |
| `log(s) crudos de más de 1 MiB` | `grep -vE '^\s+at '` + `gzip -9` y borrar el crudo | un log de Gradle llegó a 49,9 MB por 435.671 líneas de pila D8 |
| `pendiente(s) sin comando para cerrarlo` | escribir el comando exacto y qué comprueba | un bloqueo sin salida es trabajo que nunca se cierra |
| `N commit(s) sin respaldo fuera del sandbox` | hacer el respaldo siguiendo `docs/RESPALDOS.md` y añadir la entrada al registro | el repo no tiene remoto: sin copia externa un workspace perdido se lleva los 112 commits |
| `el head del último respaldo registrado no existe en el historial` | `git cat-file -t <hash>` y corregir la entrada | el registro se copió mal o se reescribió el historial |

## Añadir comprobaciones nuevas

La regla 4 de `AGENTS.md` pide convertir lo aprendido en una regla con su
comprobación automática. Cada vez que un error se cuele, la pregunta es **qué
comprobación lo habría cazado**, y esa comprobación va aquí.

Dos requisitos para añadirla:

- **Que sea verificable de verdad.** Si no se puede comprobar con los archivos y
  Git del workspace, no es una comprobación: es una buena intención.
- **Que no corrompa lo que verifica.** `git status --porcelain` alinea el estado
  en columnas y un `.strip()` se come el espacio inicial de la primera línea, que
  entonces se parsea desplazada un carácter y recorta la ruta. La primera versión
  de este script hacía eso y enseñaba `M ocs/INVENTARIO…`. Un verificador que
  miente es peor que no tenerlo: da la tranquilidad que se supone que debe dar.

## Lo que el latido no hace

- No ejecuta Gradle ni la suite de pruebas: eso es el cierre mínimo (regla 9).
- No valida aparato, credenciales ni red.
- No arregla nada solo: informa y dice cómo. Corregir sigue siendo deliberado,
  porque una corrección automática puede borrar la pista de un error real.
