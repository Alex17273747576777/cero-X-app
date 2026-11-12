# Directorios: alcance y auditoría

Corte: 2026-11-12. Destino: **Fuentes → Directorios**. No hay sincronización al arrancar: la actualización es manual y la caché JSON se guarda en `AlmacenExterno/directorios`, con escritura temporal y renombrado.

## Contrato común

- Son enlaces de descubrimiento; cero-X no aloja el contenido de los destinos.
- «Listado» no significa «seguro», «legal en tu región», «compatible» ni «verificado por cero-X».
- No se ejecuta JavaScript, DEX, APK, parsers de terceros ni instaladores desde estos datos.
- Sólo se abren enlaces HTTPS con host, sin credenciales y sin puertos distintos de 443. Las respuestas se limitan a 4 MiB y las cachés a 8 MiB por recurso.
- Contenido marcado 18+/NSFW se oculta por defecto. Un directorio puede tener metadatos incompletos: el filtro no sustituye criterio parental ni una revisión.
- Las descripciones, valoraciones y avisos pertenecen a cada origen. Se enlaza la ficha para conservar atribución y contexto.

## Orígenes y decisiones

| Origen | Formato comprobado | Integración y límite |
|---|---|---|
| Brave Search | Motor web existente | Con consulta, abre búsqueda Brave real; no inventa resultados locales |
| FMHY | Markdown de `fmhy/edit`: `reading`, `video`, `gaming`, `mobile` | Primer enlace principal por línea, categoría por documento y enlace al documento |
| JustIndex | HTML de tarjetas y secciones | Nombre, host, ficha, sección y 18+ separado; no se empaquetan iconos |
| Miyomi | JSON `api/apps`, `api/extensions`, `api/guides` | Aplicaciones, repositorios de extensiones y guías; plataformas, etiquetas y compatibilidad declarada. El sitio es AGPL-3.0; cada app/extensión conserva su propia licencia |
| Wotaku | Markdown VitePress `websites`, `software`, `faq` | Enlaces y categoría por documento; su aviso general de contenido adulto queda registrado |
| EverythingMoe | HTML de secciones `anime`, `manga`, `novel`, `apps`, `database`, `utils` | Se enlaza su ficha/reseña, no se presenta como streaming dentro de cero-X |
| AniPirate | HTML `/directory`; repositorio público sólo contiene README/logo | Parser SSR y ficha; cliente directo recibió 403. No se evade Cloudflare ni se usa un proxy no autorizado |
| Appteka Rebuild | Ficha `appteka.store/app/1d6r334032` | Sólo enlace. La ficha enlaza al proyecto Appteka original, no prueba código fuente de la variante `com.fouadmods.appteka`; APK no descargado ni auditado |
| FMHY Android | `github.com/omeg4-dev/fmhy-android`, GPL-3.0 | Repositorio externo; no instalación ni código incorporado |
| Komi Store | `github.com/komi-store/komi-store`, Apache-2.0 | Repositorio externo; no instalación ni código incorporado |

No se ha encontrado en esta revisión una autorización general para copiar y redistribuir los datos de JustIndex o EverythingMoe. Por eso no se empaqueta una instantánea de sus catálogos: sólo se obtiene bajo acción del usuario y se enlaza la fuente.

## Prueba real opt-in

Comando:

```bash
source entorno/local.sh
cd android
CEROX_LIVE_DIRECTORIES=1 ./gradlew \
  -I ../tools/low-memory.gradle \
  :app:testDebugUnitTest --tests com.ceroX.app.modules.directories.DirectoriosRedRealTest
```

Resultado `directorios-red-02`: Miyomi 220; JustIndex 73; EverythingMoe 432; Wotaku 469; FMHY 3.714; AniPirate 0 con `HTTP 403`. La prueba exige datos en los cinco primeros y datos o 403 explícito en AniPirate. No es una prueba Android física ni una certificación de los destinos.

## Fallos corregidos por la prueba real

- Miyomi devuelve listas `null`: el modelo las acepta y las normaliza a vacías.
- Rutas corregidas: Wotaku `faq.md`; FMHY `gaming.md` y `mobile.md`.
- `android.net.Uri` se sustituyó por `java.net.URI` para parsers JVM repetibles.
- Selectores JustIndex compatibles con jsoup y títulos dentro de enlaces.

## Pendiente

AniPirate podrá sincronizar cuando ofrezca una respuesta directa parseable, sin eludir controles. Las tiendas/proyectos externos sólo podrían convertirse en instalación tras otra fase de auditoría de código, releases, firma, hashes y permisos. El motor adaptativo consumirá estos metadatos como señales explicables, no como una puntuación de seguridad.
