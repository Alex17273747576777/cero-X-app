# Licencia y procedencia de cero-X

**GPL-3.0 desde el 2026-11-11.** Antes: Apache-2.0. Este documento existe porque
un relicenciamiento sin la procedencia auditada es una afirmación, no un hecho.

## Quién puede decidirlo

Los cuatro identificadores de la lista de contribuidores del repositorio
(`Alex17273747576777`, `cero-X`, `La Bandita`, y una entrada duplicada de
`cero-X`) son del mismo autor: 44 + 13 + 6 + 1 commits. No hay terceros cuyo
consentimiento haga falta para el código propio.

Lo que **no** se puede hacer es relicenciar el código ajeno: el linaje Apache-2.0
y MIT conserva sus términos. Apache-2.0 → GPL-3.0 es la dirección permitida (el
conjunto resultante es GPL-3.0 y los avisos Apache se conservan en `NOTICE`). La
dirección contraria no existe, así que este cambio es de ida.

## Por qué

Lo que se quiere fusionar a partir de ahora es GPL-3.0:

| Repo | Licencia | Lenguaje | Con Apache-2.0 | Con GPL-3.0 |
|---|---|---|---|---|
| `alienware377/RootlessViPER4Android` | GPL-3.0 | C | ❌ | ✅ |
| `EchoMusicApp/Echo-Music` | GPL-3.0 | Kotlin | ❌ | ✅ |
| `N-Zik-Group/N-Zik` | GPL-3.0 | Kotlin | ❌ | ✅ |
| `recloudstream/cloudstream` | GPL-3.0 | Kotlin | ❌ | ✅ |
| `deniscerri/ytdlnis` | GPL-3.0 | Kotlin | ❌ | ✅ |
| `fmhyplay/android` | MIT | Java | ✅ | ✅ |
| `nulldio/32steps` | MIT | Kotlin | ✅ | ✅ |
| `HellzYeahh/earshot` | MIT | Kotlin | ✅ | ✅ |
| `siumiu1968/Aulama_TV` | MIT | Kotlin | ✅ | ✅ |
| `Ghostpanter/ghost-files` | MIT | Kotlin | ✅ | ✅ |
| `varunsalian/debrify` | AGPL-3.0 | **Dart/Flutter** | ❌ | ❌ lenguaje |
| `namidaco/namida` | **EULA propietaria** | **Dart/Flutter** | ❌ | ❌ licencia y lenguaje |

Dos hechos que ninguna licencia arregla:

1. **Flutter no se fusiona con Kotlin.** debrify y namida son Dart; no hay forma
   de meter su código en una app Jetpack Compose. Lo máximo es reimplementar un
   comportamiento observado, con código propio.
2. **Namida no es software libre.** Su `LICENSE` es una EULA (`source available`,
   uso personal, prohibido redistribuir, sublicenciar, reempaquetar, rebautizar y
   usar partes del código para otra cosa que aprender o contribuir a Namida).
   `namidaco/namida-snapshots`, el repo que se citaba, son **4 KB con builds
   beta**: ni siquiera es código.

## Procedencia auditada el 2026-11-11

`scripts/clone_all_repos.sh` clona ocho repositorios de referencia. Se comprobó
cada licencia por la API de GitHub y se buscó su rastro en el código del repo:

| Referencia | Licencia | ¿Qué hay de ella en cero-X? |
|---|---|---|
| `PineappleTwilight/houri` | Apache-2.0 | Base del lector: `modules/houri`, 18 ficheros |
| `mihonapp/mihon` | Apache-2.0 | El contrato `eu.kanade.tachiyomi.source`, 6 ficheros |
| `Foxlations/Foxlations` | Apache-2.0 | Referencia de traducción |
| `ifritraen/kisara` | Apache-2.0 | `modules/kisara`, módulos de lectura y color |
| `unseensnick/Reikai` | Apache-2.0 | Referencia de UI |
| `KotatsuApp/kotatsu` | **GPL-3.0** | **Sólo formato**: `BackupFormatDetector.kt` detecta backups SQLite por sus primeros bytes y los atribuye. No se copió código |
| `itsmechinmoy/echo-nightly` | sin declarar (builds) | Menciones en comentarios; el fuente real es `EchoMusicApp/Echo-Music`, GPL-3.0 |
| `namidaco/namida` | **EULA propietaria** | Comentarios que decían «Basado en Namida» sobre esqueletos de 30 líneas sin nada de Namida. Se están quitando |

El caso de Kotatsu merece la precisión: mientras cero-X se declaró Apache-2.0,
tener código GPL-3.0 dentro habría sido un incumplimiento. Se comprobó que no lo
hay —las menciones son atribuciones de formato y entradas de catálogo en JSON—, y
con la app bajo GPL-3.0 la pregunta deja de ser crítica. Se deja escrita porque
antes sí lo era.

## Lo que obliga la GPL-3.0 aquí

- **Ofrecer el código fuente correspondiente** al distribuir el APK. El repo es
  público y `LicenseCompliance` convierte eso en una obligación comprobada
  (`source-available`), no en una suposición.
- **Conservar la licencia**: el texto viaja en el APK (`assets/LICENSE`), copiado
  por la tarea `bundleLicenseAssets`, que falla el build si falta.
- **Conservar el `NOTICE`** del linaje Apache/MIT, también empaquetado.
- **Indicar los cambios** en los ficheros modificados: los commits de este repo lo
  hacen fichero a fichero; falta el barrido de cabeceras SPDX, que está pendiente.

## Pendiente, dicho claramente

- El barrido de cabeceras SPDX (`SPDX-License-Identifier: GPL-3.0-only`) en los
  168 ficheros Kotlin no está hecho.
- Los comentarios «Basado en Namida» y el tema de interfaz «namida Music & Video»
  se quitan en la limpieza de esqueletos, no en este commit.
- Que la app funcione en un teléfono sigue sin estar demostrado: el APK de la
  1.0.1 está firmado y verificado byte a byte, y nadie lo ha abierto en el V60.
