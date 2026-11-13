# Respaldos fuera del sandbox

Corte: 2026-11-13.

El repositorio **no tiene ningún remoto de Git** (`git remote` devuelve 0 entradas) y
el sandbox pierde el toolchain en cada reinicio. Mientras no haya remoto, el archivo
de respaldo en Google Drive es la única copia que existe fuera de esta caja: si el
workspace se pierde, se pierden los 112 commits con él.

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
```

Destino: carpeta «cero-X — respaldos» en Google Drive,
`https://drive.google.com/drive/folders/1MTFiK3m6_CbBsWuklewEdW3azRAgKZpg`.

Medidas de la primera subida en `work/ci/respaldo-01-resumen.txt`.

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
