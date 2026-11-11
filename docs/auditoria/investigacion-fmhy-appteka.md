# Cómo se buscaron las piezas de entretenimiento, y qué se encontró

Fecha: 11 de noviembre de 2026. Fuentes: `fmhy.net` (su wiki en git) y
`appteka.store`. Las licencias de cada repositorio se comprobaron una a una con la
API de GitHub, no se supusieron por el nombre.

El encargo era no rascar HTML «a ojo»: un método de búsqueda y clonación que se
pueda repetir. Los dos scripts están en `scripts/buscar-fmhy.py` y
`scripts/buscar-appteka.py` y se ejecutan sin dependencias más allá de Python 3.

---

## 1. FMHY: el directorio no está en la web, está en un git

`fmhy.net` es un VitePress: la página pesa 54 KB y la búsqueda se hace en el
cliente contra un índice JSON que genera el propio VitePress. Rascar el HTML no
lleva a ningún sitio, porque el contenido no está en el HTML.

El repositorio `fmhy/FMHY` tampoco sirve: su árbol tiene **dos archivos**
(`README.md` y `fmhy.md`, este último un índice de 2,8 KB con 19 secciones). El
directorio de verdad vive en el wiki, que GitHub sirve como repositorio git
clonable:

```
git clone https://github.com/fmhy/FMHY.wiki.git
```

Son **19 archivos `.md`, 1,9 MB**. Los que importan aquí: `Mobile.md` (155 KB,
1.523 líneas), `Streaming.md` (117 KB), `Music.md` (98 KB), `Adblock.md` (54 KB).

### La gramática de una entrada

```
* ⭐ **[Nombre](url)** - Descripción / [GitHub](…) / [Telegram](…)
```

con secciones `# ► X` y subsecciones `## ▷ Y`. Tres marcadores que cambian cómo se
ordena un resultado:

| Marcador | Significado | Tratamiento |
| --- | --- | --- |
| `⭐` | recomendado por FMHY | sube la puntuación |
| `↪️` | no es una entrada, es un salto a otra sección | se descarta como duplicado |
| `🌐` | índice o comparador, no una app | peso menor |

Un detalle que rompe los enlaces si no se sabe: algunos nombres llevan el carácter
invisible U+2060 (word joiner) para que el markdown no los convierta en enlace
automático. El parser lo elimina.

### Lo que devuelve el índice

`python3 scripts/buscar-fmhy.py indice` → **11.161 entradas**, 1.324 destacadas con
⭐ y **2.438 con repositorio de código fuente**. Por página:

```
1643 Educational · 1555 Misc · 1391 Non-Eng · 1137 Mobile · 980 Gaming
 963 Streaming · 862 Music · 847 Reading · 713 Linux · 329 Adblock
 323 Artificial-Intelligence · 144 Storage · 143 Downloading · 103 Torrenting
```

Y la búsqueda pondera: nombre > descripción > sección > subsección, con bonificación
por ⭐ y por tener repositorio (que es lo que decide si se puede fusionar).

### Namida sí está en FMHY, y no se puede usar

Aparece tres veces (`Mobile/Android Audio Players` con ⭐, `Mobile/YouTube Music`,
`Music/Audio Players`), siempre apuntando a `github.com/namidaco/namida`. La
licencia de ese repositorio es una **EULA propietaria** (5 de octubre de 2023) que
prohíbe el uso comercial, la redistribución, la sublicencia y el cambio de marca.
Que FMHY lo recomiende no cambia eso: FMHY es un directorio, no una licencia.

`namidaco/namida-snapshots` (los binarios) son 4 KB y tampoco declaran licencia.

Así que la pregunta «¿se pueden reemplazar las opciones de Namida?» tiene una
respuesta clara: **sí, y con código que sí se puede fusionar**, porque cero-X es
GPL-3.0 desde `f5100afa`.

---

## 2. appteka: no hay API, pero la búsqueda viene servida en el HTML

`appteka.store` es Next.js. No tiene API pública: `/api/apps`, `/api/search?q=` y
`/apps.json` devuelven 404 con una página de error de 39 KB.

Tiene `sitemap.xml` → seis sitemaps de apps (`sitemap-apps-1..6.xml`) y uno de
usuarios, todos con `lastmod 2026-11-11`. Recorrerlos da **274.268 apps**, que es
demasiado para lo que hace falta y no dice nada de cada una.

El camino útil es otro: `/search?q=<término>` devuelve 200 con los resultados ya
renderizados en el servidor, dentro del payload RSC (`self.__next_f.push([1,"…"])`).
O sea que se le pregunta a **su** buscador y se deserializa el JSON que viene en la
página. Cada resultado tiene esta forma:

```json
{"app_id":"7ccr301469","size":13712806,"label":"Equalizer",
 "package":"com.jazibkhan.equalizer","ver_name":"6.3.5.8","ver_code":999,
 "downloads":752,"author":{"id":335990,"name":"…"}}
```

Dos cosas que cuestan tiempo si no se saben:

1. Los trozos RSC vienen escapados como cadena JavaScript. Se decodifican con
   `json.loads('"' + trozo + '"')`. Hacerlo con `unicode_escape` —lo primero que
   se intenta— **rompe los acentos y el cirílico**: el autor «Макаров» sale como
   «ÐÐ°ÐºÐ°Ñ0Ð²».
2. Los objetos no se pueden sacar con una expresión regular fiable porque llevan
   objetos anidados (`author`, `icon` con SVG dentro). El extractor recorre llaves
   respetando cadenas y escapes.

`scripts/buscar-appteka.py equalizer iptv` devuelve ~96 resultados por consulta,
ordenados por descargas, con paquete, versión, tamaño y autor.

### Lo que confirmó appteka

Que la referencia de comportamiento del ecualizador (`Echo EQ`,
`appteka.store/app/b94r338435`, 12,05 MB, 36 descargas, categoría Music & Audio,
etiquetas «equalizer», «bass boost») es una app comercial cerrada, y que el hueco
que llena en Android lo ocupa casi siempre **RootlessJamesDSP** —que es justo lo que
FMHY recomienda con ⭐ bajo el nombre «Poweramp Equalizer»—. Las dos fuentes, que
no se hablan entre sí, apuntan al mismo sitio.

---

## 3. Candidatos, con la licencia comprobada

Todo lo de abajo se verificó con `GET /repos/{owner}/{repo}` el 11/11/2026. La
columna «fusión» dice si se puede mezclar con cero-X, que es GPL-3.0.

### Ecualizador y efectos de audio

| Repositorio | Licencia | Lenguaje | ★ | Fusión |
| --- | --- | --- | --- | --- |
| `timschneeb/RootlessJamesDSP` | **GPL-3.0** | C | 1.728 | sí |
| `alienware377/RootlessViPER4Android` | **GPL-3.0** | C | 114 | sí (ya es la base elegida) |
| `nulldio/32steps` | **MIT** | Kotlin | 230 | sí (base de los pasos de volumen) |
| `woheller69/audio-analyzer-for-android` | Apache-2.0 | Java | 413 | sí |

`RootlessViPER4Android` lleva dentro `app/src/main/cpp/libjamesdsp/` con sus propias
licencias: es el mismo motor que RootlessJamesDSP. O sea que la base que ya eligió el
usuario y la recomendación de FMHY son familia.

### IPTV y reproductores

| Repositorio | Licencia | Lenguaje | ★ | Fusión |
| --- | --- | --- | --- | --- |
| `oxyroid/M3UAndroid` | **GPL-3.0** | Kotlin | 1.285 | sí — es el neutro M3U/M3U8 + EPG |
| `phstudio2/FreeTV` | **GPL-3.0** | Kotlin | 148 | sí (última actividad 2024) |
| `neffex97/Tiwee` | GPL-3.0 | Dart | 325 | no (Flutter, como debrify) |
| `4gray/iptvnator` | MIT | TypeScript | 7.710 | no es Android (referencia de UX) |
| `fredolx/open-tv` | GPL-2.0 | Rust | 4.160 | no (GPL-2.0 sin «o posterior») |
| `tbeezy/ynotv` | AGPL-3.0 | TypeScript | 454 | no es Android |
| `hasanbeder/M3Unator` | GPL-3.0 | JavaScript | 130 | herramienta, no app |
| `peterpt/IPTV-CHECK` | MIT | Python | 219 | herramienta de validación de listas |
| `iptv-org/awesome-iptv` | **sin licencia** | — | 12.863 | sólo como referencia de formato |

`fmhyplay/android` (MIT, Java, ★370) sigue siendo fusible y es el que aporta bloqueo
de anuncios y WARP; `varunsalian/debrify` (AGPL-3.0 **y** Flutter) no.

El reproductor que va debajo: `androidx.media3`/ExoPlayer, Apache-2.0.

### Vídeo local

| Repositorio | Licencia | ★ | Fusión |
| --- | --- | --- | --- |
| `anilbeesetti/nextplayer` | **GPL-3.0** | 4.719 | sí |
| `marlboro-advance/mpvEx` | Apache-2.0 | 2.763 | sí |
| `mpv-android/mpv-android` | MIT | 3.691 | sí |
| `nova-video-player/aos-AVP` | Apache-2.0 | 4.800 | sí |
| `moneytoo/Player` (Just Player) | Unlicense | 2.806 | sí |

### Reemplazos de las opciones de Namida

| Repositorio | Licencia | Lenguaje | ★ | Qué aporta |
| --- | --- | --- | --- | --- |
| `MetrolistGroup/Metrolist` | **GPL-3.0** | Kotlin | 14.136 | cliente YouTube Music |
| `TeamNewPipe/NewPipe` | **GPL-3.0** | Java | 40.580 | streaming multiplataforma |
| `deniscerri/ytdlnis` | **GPL-3.0** | Kotlin | 11.080 | descarga de audio |
| `RetroMusicPlayer/RetroMusicPlayer` | **GPL-3.0** | Kotlin | 5.401 | reproductor local, la pieza más parecida a Namida |
| `SimoHypers/limusic` | GPL-3.0 | Rust | 1.992 | reproductor local |
| `kawaiiDango/pano-scrobbler` | GPL-3.0 | Kotlin | 2.351 | scrobbling |
| `acszo/Redomi` | GPL-3.0 | Kotlin | 610 | streaming multipuente |
| `brahmkshatriya/echo` | **NOASSERTION** | Kotlin | 3.616 | no fusible hasta leer su `LICENSE.md` |

`EchoMusicApp/Echo-Music` (GPL-3.0, ★2.461) es un cliente de YouTube Music de 420 MB,
no un reproductor local: no sustituye a Namida en lo local, y su tamaño es justo lo
que este proyecto viene quitando.

Conclusión del barrido: **las opciones de Namida se pueden reemplazar con
`RetroMusicPlayer` (GPL-3.0) para lo local y `NewPipe`/`Metrolist`/`ytdlnis` para lo
remoto**, todos fusionables bajo la GPL-3.0 de cero-X. No hace falta acudir a nada de
FMHY sin licencia para cubrir ese hueco.

---

## 4. Los cuatro repositorios de extensiones que se pidieron

El encargo decía que, si se montaba un repositorio de extensiones propio, tenía que
ser un **fork** de estos cuatro. Comprobados uno a uno:

| Repositorio | Licencia | Forma | ★ | Tamaño |
| --- | --- | --- | --- | --- |
| `github.com/Awerkori` | — | **no es un repo: 404**. Es un usuario con 10 repos | — | — |
| `Awerkori/manga-fonte-extensoes` | **AGPL-3.0** | fuentes Kotlin + Gradle (fork de `FelipeGFA/fonte-extensoes`, Apache-2.0) | 9 | 238 MB |
| `Awerkori/anime-fonte-extensoes` | **AGPL-3.0** | fuentes Kotlin + Gradle | 5 | 139 MB |
| `Awerkori/manga-extensoes` | **AGPL-3.0** | distribución compilada (fork de `FelipeGFA/extensoes`) | 4 | 21 MB |
| `Awerkori/anime-extensoes` | **AGPL-3.0** | distribución compilada, 273 APK | 10 | 669 MB |
| `Shebyyy/kotatsu-multi-parsers` | **sin licencia** | 7 JSON de parsers Kotatsu | 19 | 98 KB |
| `salmanbappi/extensions-repo` | **sin licencia** | 99 APK + índice | 52 | 255 MB |
| `NovelSourcery/extensions` | **sin licencia** | 518 APK + índice | 22 | 151 MB |

**Tres de los cuatro no declaran licencia ninguna.** Sin licencia, el derecho de
autor por defecto es todos los derechos reservados: se pueden mirar y se puede hacer
fork en GitHub (los ToS lo permiten), pero **no se puede redistribuir su contenido ni
fusionarlo en cero-X** sin permiso del autor. Fork no es lo mismo que licencia.

Los de Awerkori sí son forkables: AGPL-3.0, y la combinación AGPL-3.0 ↔ GPL-3.0 está
permitida por la GPLv3 §13. Pero la AGPL añade una obligación que la GPL no tiene —el
uso a través de red cuenta como distribución—, así que cualquier cosa derivada de esos
repositorios tiene que vivir en un repositorio propio y separado, no dentro del APK.

### Los dos formatos que hay, y por qué importan

Los repositorios de extensiones vienen en dos formas distintas:

**Distribución** (lo que consume una app): un `repo.json` con el nombre y la huella
de firma, un `index.json`/`index.min.json` con el catálogo, un `index.pb` (binario,
más compacto) y una carpeta `apk/`.

```json
{"meta":{"name":"Project Nox Anime",
         "signingKeyFingerprint":"144f44e865cbfae6bde110489ef7ec142f0b1681f627a093a218650e21db5dad"}}
```

```json
[{"name":"Aniyomi: AnimeOnsen",
  "pkg":"eu.kanade.tachiyomi.animeextension.all.animeonsen",
  "apk":"aniyomi-all.animeonsen-v14.11.apk","lang":"all","code":11,
  "version":"14.11","nsfw":0,
  "sources":[{"name":"AnimeOnsen","lang":"all","id":"8542735178285060053",
              "baseUrl":"https://www.animeonsen.xyz"}]}]
```

**Fuentes** (lo que se compila): un proyecto Gradle con `src/<lang>/<sitio>/` por
extensión y un pipeline que publica los APK en la rama `repo`. Así son
`FelipeGFA/fonte-extensoes` (Apache-2.0, 31.288 archivos) y los dos de Awerkori.

`Shebyyy/kotatsu-multi-parsers` es una tercera cosa: JSON de parsers para Kotatsu,
con campos `contentType`, `nsfw` e `isBroken`.

**La huella de firma del `repo.json` es el dato importante.** Es lo que permite a una
app verificar que las extensiones que instala vienen de quien dice que vienen. En
cero-X eso encaja con lo que ya existe: `entorno/HUELLA-CERTIFICADO.txt` y el
workflow `auditoria-publicaciones.yml` hacen exactamente esa comprobación con los
APK publicados. Un repositorio de extensiones sin huella verificable es un
instalador de APK sin verificar, que es lo que se acaba de borrar de
`ExtensionStore` (tres extensiones inventadas con `sha256` de relleno).

---

## 5. Lo que esto decide

1. **IPTV neutro**: `oxyroid/M3UAndroid` (GPL-3.0, Kotlin) es la referencia de
   comportamiento para M3U/M3U8 + EPG, y `fmhyplay/android` (MIT) para el bloqueo de
   anuncios. Ninguna lista de canales va dentro del APK: las fuentes se instalan
   desde un repositorio externo.
2. **Ecualizador**: la base sigue siendo `alienware377/RootlessViPER4Android`
   (GPL-3.0) + `nulldio/32steps` (MIT), y `timschneeb/RootlessJamesDSP` (GPL-3.0) es
   la misma familia de motor, así que se puede mirar su implementación de los efectos
   sin problema de licencia. `Echo EQ` queda como referencia de comportamiento, que
   es lo que era: no hay nada que fusionar ahí.
3. **Audio**: `RetroMusicPlayer` (GPL-3.0) cubre lo local; nada de Namida.
4. **Repositorio de extensiones**: se puede hacer fork de los de Awerkori
   (AGPL-3.0) y construir sobre el formato de índice, que es un estándar de hecho y
   no tiene dueño. De los otros tres sólo se puede tomar **nota del formato**, no el
   contenido: sin licencia no hay permiso para redistribuir.
