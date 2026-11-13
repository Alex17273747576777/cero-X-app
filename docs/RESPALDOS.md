# Respaldos fuera del sandbox

Corte: 2026-11-13.

El repositorio tiene remoto desde el 2026-11-13: `origin` apunta a
`github.com/Alex17273747576777/cero-X-app` (**público**) con la URL limpia, sin token
embebido, autenticando por el helper `/home/user/.gh-credential`. **Ese remoto es el
respaldo primario del código.** Los tarballs de Drive quedan como red secundaria y
cubren lo que no está en ningún repo: `work/ci` con su evidencia.

Antes de configurar el remoto este doc afirmaba que el repositorio no tenía ninguno.
Era cierto de la configuración local (`git remote` daba 0 entradas) y falso del
proyecto: `cero-X-app` existía en GitHub desde antes, y el historial del sandbox
**diverge** del suyo. El remoto `main` está en `1fdad35` con 4 commits que el sandbox
no tenía; los 21 commits del sandbox se empujaron a la rama
`fases-verificadas-2026-11-13` y no a `main`, porque `git merge-base --is-ancestor`
dijo que un force-push habría borrado esos 4.

La divergencia quedó como **PR #7** (`/pull/7`), abierto y **sin fusionar**. No es
fusionable tal cual: la API responde `mergeable=false`, `mergeable_state=dirty`,
`rebaseable=false`, y `git merge-tree --write-tree origin/main HEAD` sale 1 con cinco
ficheros en conflicto, medidos sin tocar el árbol:

| fichero | tipo | `main` desde la base | esta rama desde la base |
|---|---|---|---|
| `android/app/src/main/AndroidManifest.xml` | content | +15 −2 | +30 −2 |
| `…/modules/extension_sandbox/InstaladorProjectNox.kt` | **add/add** | +65 −0 | +71 −0 |
| `…/ui/extensiones/IntegracionesActivity.kt` | **add/add** | +270 −0 | +337 −0 |
| `…/ui/main/MainActivity.kt` | content | +17 −0 | +163 −142 |
| `…/src/test/java/com/ceroX/app/ui/estado/NavegacionTest.kt` | content | +25 −0 | +30 −0 |

Los dos **add/add** son el hallazgo: `main` y esta rama crearon esos dos ficheros **por
separado**, cada una con su versión. No es un conflicto de líneas sino dos
implementaciones paralelas del mismo módulo escritas en sesiones distintas, así que
resolverlo exige decidir cuál manda o fusionarlas a mano y volver a cerrar con suite y
los dos APK. Fusionan solos `android/app/build.gradle`, `core/CeroXApplication.kt` y
`ui/ia/PantallaIa.kt`.

Cifras de la divergencia, corregidas: lo que ve el PR es la comparación desde la base
común `0aaad47768ef`, o sea `git diff --stat origin/main...HEAD` → **210 ficheros,
+12.886, −1.853**. El `origin/main HEAD` de dos puntos que se anotó primero (161
ficheros) mezcla lo que hizo la rama con lo que avanzó `main` y por tanto subestima el
trabajo de la rama. Las 3 alertas de Dependabot y el resto están en
`proyectos/cero-X/RESPALDOS.md` del repo `entorno-de-herramientas-de-trabajo`, que
desde el 2026-11-13 registra los seis repos de la cuenta con sus herramientas,
entornos medidos y registros de falla, con CI en verde y licencia GPL-3.0.

**El PR sí dispara CI; la afirmación contraria era una medición prematura.** El
2026-11-13 se escribió aquí que en todo el historial del repo había **0 corridas con
`event=pull_request`** y que la causa no se encontraba. Medido de nuevo con
`/actions/runs?event=pull_request`, hay dos, y las dos son posteriores a esa nota:

| corrida | commit | creada | resultado |
|---|---|---|---|
| `43415076063` | `7bbc2ae` | 11:11:39Z, unos 84 s después del push | success |
| `43418068714` | `e960c81` | 11:33:52Z, unos 19 s después del push | en curso al escribir esto |

El error fue medir un sistema asíncrono una sola vez, inmediatamente después del push,
y escribir la ausencia observada como una propiedad permanente del repositorio. Lo que
no se explica, y se deja dicho como tal en vez de taparlo: el push de `57538ab` a las
10:45Z **no** produjo corrida `pull_request`, y los dos siguientes sí. La comprobación
tras cada push a la rama sigue siendo explícita —mirar `/actions/runs?head_sha=<sha>` y
leer la conclusión—, porque `workflow_dispatch` verifica lo mismo y no depende de que el
disparador por PR se comporte igual cada vez.

`tools/latido.py` cuenta como respaldado un commit que ya esté en alguna rama de algún
remoto (`git branch -r --contains`); el registro de Drive sigue siendo el aviso para lo
que no se empujó. Sin esa rama el latido avisaría de trabajo que Git ya respaldó, y un
aviso falso se deja de leer.

## Qué se respalda y qué no

Dentro: `work/` entero con sus rutas relativas, que incluye `work/cero-X-app/.git`
completo —no es una foto del código, es el historial— y la evidencia de `work/ci/`,
que vive fuera del repo y por eso no la cubre Git.

Fuera, a propósito:

- `/home/user/.cerox-keys/tokens.txt` — credenciales. No salen del sandbox.
- `/home/user/uploads/` — capturas del usuario, no son estado del proyecto.
- Cachés y salidas de build (`android/.gradle`, `android/build`, `android/app/build`) —
  se regeneran con Gradle y abultan cientos de megabytes.
- `/opt/cerox`, JDK, SDK de Android — unos 2,8 GB que rearma
  `scripts/preparar-entorno-android.sh`.
- `/tmp` — no persiste entre turnos de todas formas.

Antes de cada subida se hace un barrido de secretos sobre `work/` (patrones de clave
privada PEM, `AKIA…`, `ghp_…`, `xox…`, `AIza…`) y se revisan `entorno/local.sh` y
`.git/config`. Subir a Drive es un envío externo: se comprueba antes, no después.

## Registro

Bloque legible a máquina; `tools/latido.py` lee la última línea `head=` y avisa si hay
commits posteriores sin respaldar. Añadir una entrada nueva **al final**, nunca
reescribir las anteriores: el registro vale por ser histórico.

```text
RESPALDO_REGISTRO
fecha=2026-11-13T09:52:33Z
head=cd184579d9f957cabd34e42b5a43f80fc891b3db
archivo=cero-X-respaldo-2026-11-13-cd18457.tar.gz
bytes=8963823
sha256=e6653b99febc72c252109debb8e0ebdf73040a0c33055be00a56e79e6a41fffa
md5=db264bdad023132dd8fbcc8696816909
drive_carpeta=1MTFiK3m6_CbBsWuklewEdW3azRAgKZpg
drive_id=1fd1856Yydk-Q-AXOmsP90hMEtbu7zF79
restore_probado=si
FIN_RESPALDO_REGISTRO

RESPALDO_REGISTRO
fecha=2026-11-13T09:57:34Z
head=d85c0bd8f4b8abe66c4cae7a208aa3d157bda5cc
archivo=cero-X-respaldo-2026-11-13-d85c0bd.tar.gz
bytes=8990675
sha256=cf953c251bc18fcd6e26ec5e10c4f3a242cffa246e7c2e081502c0c08e4f6080
md5=0a329b63af148966664f8e42f6336e1a
drive_carpeta=1MTFiK3m6_CbBsWuklewEdW3azRAgKZpg
drive_id=1qNAlhuCjbClSl6_otA1h0lJq1YevEf1A
restore_probado=si
FIN_RESPALDO_REGISTRO

RESPALDO_REGISTRO
fecha=2026-11-13T10:07:44Z
head=7bfde2cf33b503837ad92c8f4f82e0390049eac4
archivo=cero-X-respaldo-2026-11-13-7bfde2c.tar.gz
bytes=9034304
sha256=2f49136cdaafcd82cce4e97ec2cfb19c73fd55c1dd6bea55c6f366abf68bc56b
md5=4da1e2e1a18e178b1b0c02a7a95f91aa
drive_carpeta=1MTFiK3m6_CbBsWuklewEdW3azRAgKZpg
drive_id=1NdZcqtIq6Mip85AJhAKHXfGkgUhp3MZ8
restore_probado=si
FIN_RESPALDO_REGISTRO
```

La segunda entrada existe porque la primera no podía cubrir el commit que añade la
maquinaria de respaldo: en cuanto `tools/latido.py` y este doc se commitearon, el
latido avisó de un commit sin respaldar, y tenía razón. Es la comprobación
funcionando sobre sí misma, no un fallo.

Destino: carpeta «cero-X — respaldos» en Google Drive,
`https://drive.google.com/drive/folders/1MTFiK3m6_CbBsWuklewEdW3azRAgKZpg`.

Medidas de cada subida: `work/ci/respaldo-01-resumen.txt`,
`work/ci/respaldo-02-resumen.txt` y `work/ci/respaldo-03-resumen.txt`.

Los hashes y los head se pegan de la salida de `git rev-parse` y `sha256sum`, nunca
se completan de memoria ni se transcriben a mano. En el tercer ciclo se escribió un
`head=` inventado a partir del hash corto y `git cat-file -t` lo desmintió antes del
commit; de haber entrado, el registro de la única copia externa habría apuntado a un
objeto inexistente y `comprobar_respaldo` lo habría convertido en BLOQUEO.

## Cómo hacer un respaldo nuevo

Un respaldo que no se ha restaurado es una esperanza, no un respaldo. Los cinco pasos
son obligatorios; el 3 es el que de verdad prueba algo.

1. **Empaquetar** desde `/home/user`, con el HEAD en el nombre. Primero se escribe
   `MANIFIESTO-respaldo.txt` en `/home/user` con el HEAD completo, la rama, el número de
   commits, el estado del árbol, el resultado del último cierre Gradle con los hashes de
   los dos APK, qué se incluye, qué se excluyó y por qué, y los pasos de restauración:
   es lo que hace que el archivo se explique solo cuando se abra dentro de un año. Se
   borra del workspace en cuanto termina el empaquetado.

```bash
cd /home/user && HEAD=$(git -C work/cero-X-app rev-parse --short HEAD) \
  && tar czf "respaldos/cero-X-respaldo-$(date -u +%Y-%m-%d)-${HEAD}.tar.gz" \
       --exclude='work/cero-X-app/android/.gradle' \
       --exclude='work/cero-X-app/android/build' \
       --exclude='work/cero-X-app/android/app/build' \
       -C /home/user work MANIFIESTO-respaldo.txt && sha256sum respaldos/*.tar.gz
```

2. **Barrer secretos** y comprobar que `work/` no lleva credenciales.

3. **Restaurar en un sitio aparte y comparar**: `tar xzf` en `/tmp/restore`, luego
   `git fsck --full`, `git rev-parse HEAD` contra el original, `git rev-list --count`,
   y `diff -rq --exclude=.git`. Los cuatro tienen que coincidir. `git fsck` puede
   soltar *dangling tree/commit*: son objetos huérfanos de operaciones antiguas, no
   corrupción; lo que importa es que no aparezca `missing` ni `broken link`.

4. **Subir** con el conector de Drive a la carpeta del registro, y comparar el
   `md5Checksum` y el `size` que devuelve Google contra los locales. Si coinciden, la
   subida está verificada por el destino y no sólo por nuestra intención.

5. **Anotar** la entrada nueva en el bloque de registro de este doc y borrar la copia
   local del archivo. Dejarla dentro del workspace haría que el respaldo siguiente
   incluyera el anterior, y cada copia duplicaría el tamaño de la anterior.

## Cómo restaurar

```bash
mkdir -p /home/user && cd /home/user
tar xzf cero-X-respaldo-<fecha>-<head>.tar.gz     # recrea work/ en su sitio
cd work/cero-X-app
git fsck --full && git log -1 --format='%H %s'    # contra el head del registro
chmod +x android/gradlew scripts/*.sh tools/*.py apk/como-verificar.sh
git config user.name 'Arena Agent' && git config user.email 'arena-agent@localhost'
python3 tools/comprobar_entorno.py                # ENTORNO_OK
bash scripts/preparar-entorno-android.sh          # JDK, SDK, Gradle, swap
python3 tools/latido.py --entorno                 # HEARTBEAT_OK
```

El entorno no está en el archivo: la regla 11 de `AGENTS.md` describe el rearme, y los
bits de ejecución se pierden en el proceso aunque el archivo los conserve.

**Resuelto el 2026-11-13**: la fusión se hizo en `7bbc2ae` (dos padres, `57538ab` y
`1fdad35`), los cinco conflictos se resolvieron leyendo las dos versiones y el cierre
completo dio 88 suites, 614 pruebas, 0 fallos y 14 omitidas con los dos APK. El PR #7
pasó de `mergeable=false`/`dirty` a **`mergeable=true`/`clean`**. `main` sigue sin
tocarse: fusionarlo es decisión del dueño.
