# Asistente real y objetivo todo-en-uno

Corte: 2026-11-14.

## Referencias comparadas

Se revisaron los árboles y README públicos de
[Watchtower](https://github.com/ferelking242/watchtower) y
[Kototoro](https://github.com/Kototoro-app/Kototoro) el 12 de noviembre de 2026.
Son referencias de producto, no código copiado ni funciones atribuidas a cero-X.

| Área | Referencia | Situación de cero-X | Siguiente brecha concreta |
|---|---|---|---|
| Chat e IA | Ninguna de las dos define el objetivo de cero-X | Cliente OpenAI-compatible real y modelos ONNX de imagen | Herramientas sobre lector/web sólo cuando existan y con confirmación |
| Fuentes | Watchtower: QuickJS; Kototoro: varios ecosistemas | QuickJS y cargadores parciales | Probar instalación, búsqueda, detalles y páginas de extremo a extremo |
| Lector | Kototoro unifica manga, novela y vídeo | Lector y pipeline con OCR/color/escalado | Importación local completa, continuidad, estadísticas y mejor navegación |
| Descargas | Watchtower incluye cola y ensamblado HLS | DownloadService y DownloadManager según el módulo | Cola visible, pausa/reanudación, reintentos y verificación del archivo |
| Migración | Watchtower importa bibliotecas | Backup/importación parcial | Restauración real con previsualización y cuenta de elementos importados |
| Sincronización | Kototoro: WebDAV y trackers | Trackers implementados; WebDAV no se declara disponible | Sincronización probada de biblioteca y progreso |
| Plataformas | Ambos amplían más allá de Android | Núcleo común parcial; APK Android publicado | No anunciar escritorio/iOS hasta tener build y prueba |
| Interfaz | Kototoro prioriza Compose | Compose + Fragments reales | Unificar sin borrar funciones ni inventar capturas |

«Superar» no se marca como una casilla: se medirá por recorridos completos en el
V60, fuentes que devuelven contenido, descargas íntegras, estabilidad, memoria y
tiempo de respuesta. Tener más nombres de módulos no cuenta como ventaja.

## Las cuatro fuentes Dart, censadas por API (cabo ⑤, entrada de F3)

El cabo ⑤ de `PENDIENTES-EXTERNOS.md` son las cuatro fuentes Flutter/Dart, que no se
portan ni se compilan aquí (no hay toolchain de Flutter y cero-X es Kotlin): **se leen**.
Censadas por la API de GitHub el 2026-11-14 por la estructura de su `lib/`, no por la
descripción del dueño ni el README. Cada una enseña una capacidad distinta; son
referencias de producto y de arquitectura, no código copiado.

| fuente | ★ / MB | capacidades por su `lib/` | qué enseña a cero-X |
|---|---|---|---|
| `kodjodevf/mangayomi` | 4.480 / 496 | `eval` (motor de extensiones dart-eval), `modules`, `repositories`, `services`, FFI a Go y Rust, Isar, 5 sistemas (android/ios/linux/macos/windows) | un motor de extensiones propio y almacenamiento local; el contraste con el QuickJS/JS de cero-X es decisión de F4 |
| `RyanYuuki/AnymeX` | 1.283 / 134 | `ai`, `controllers`, `database`, `screens`, `services`, seguimiento multi-servicio (AniList, MAL, Simkl), también web | unificación de trackers en una sola capa y un módulo de IA integrado; cero-X tiene trackers partidos por app y `ai-hub` aparte |
| `ferelking242/watchtower` | 4 / 291 | `cli`, `local_indexer`, `remote`, `eval`, `modules`, despliegue propio; música, series y novelas además de manga y anime | la capacidad de **servidor autoalojado** y el índice local; cero-X es sólo APK de Android, sin remoto |
| `Foxlations/Foxlations` | 3 / 3 | `l10n`, `eval`, `presentation`, `routes`, `theme`, `widgets` (ligero: sin `modules` ni `database`) | traducción y localización como eje, y una capa de presentación limpia; referencia de interfaz, no de alcance |

Lectura honesta: ninguna se va a portar (son Flutter y cero-X es Kotlin/Compose), así que
el valor es de **arquitectura y capacidad**, no de código. Lo que F3 convierte en brecha
concreta, en orden de utilidad para el todo-en-uno:

1. **Servidor autoalojado e índice local** (watchtower lo tiene; cero-X, no). Decidir si
   entra en el alcance del todo-en-uno o se descarta: es la diferencia entre una app y un
   hub. Sin aparato no se prueba, pero la arquitectura `remote/` + `local_indexer/` se lee.
2. **Trackers unificados** (AnymeX fusiona AniList/MAL/Simkl en una capa; cero-X los tiene
   partidos por app —`houri`, `kisara`, `sync`, `kai`). Candidato a refactor en F5.
3. **Motor de extensiones** (mangayomi y watchtower usan dart-eval en `eval/`; cero-X usa
   QuickJS/JS). No es portable, pero el diseño de `eval/` se lee para endurecer
   `extension_factory`, que F2 marcó con 4 fuentes y 0 pruebas.

El resto de las fuentes (las 8 Kotlin/Java ya clonadas, incluida `komi-store`) se censa en
los pasos siguientes de F3 hasta completar la matriz de brechas capacidad por capacidad.
Este paso cierra la lectura del cabo ⑤: las cuatro Dart están censadas y su valor, anotado.

## Las fuentes Kotlin/Java, censadas por sus módulos Gradle (F3 paso 2, 2026-11-14)

Las 12 referencias clonadas se censaron por la estructura de su `settings.gradle` —los módulos
Gradle, no el README—. El conteo de módulos es la señal más dura de arquitectura: cuánta capacidad
vive en una unidad que se compila, se prueba y se sustituye por separado. Es medido, no inferido.

| fuente | módulos Gradle | patrón |
|---|---:|---|
| `komi-store` | 41 | KMP de arquitectura limpia: `core{data,domain,presentation}` y un triplete `feature*{data,domain,presentation}` por capacidad; `coredata-aidl` (interproceso) |
| `houri` | 23 | el lector más partido: `coreconcurrency`, `corenavigation`, `coreviewmodel`, `llamatik-native`, `telemetry` aislado |
| `AniZen` | 17 | derivado de Mihon: `data`/`domain`/`presentation-*`, `source-api`, `i18n` por variante, `telemetry`, `macrobenchmark` |
| `Reikai` | 17 | ídem + `coremetro`, módulos de iconos, `baseline-profile` |
| `kisara` | 16 | ídem que AniZen, pero parado: CI roja y 0 commits en 30 días |
| `Tadami-Aniyomi-fork` | 14 | rama de Aniyomi: `source-api`/`source-local`, `i18n-aniyomi`, `private-gemini-bridge` (IA), `macrobenchmark` |
| `ShinKu` | 12 | derivado de Mihon sin módulo `telemetry` (la condiciona a que exista `google-services.json`) |
| `Kototoro` | 10 | KMP a escritorio e iOS: `cloudstream-desktop`, `desktop-app`, `ios-shared`, `mihon-*-compat` |
| `Miyorare` | 1 | un solo módulo |
| `DeepSync` | 1 | un solo módulo |
| `Kiroku` | 1 | un solo módulo (UPL; telemetría activa de serie) |
| `AnantPlay` | 0 | Java con `build.sh`, sin módulos Gradle |
| **cero-X** | **1** | 244 fuentes `.kt` en `main` repartidas en 48 paquetes `modules/*` —paquetes, no módulos Gradle: todo compila en el único `:app` (F2: 1 manifiesto, 16 componentes) |

La brecha estructural, que F2 señaló y F3 concreta: **cero-X es un solo módulo Gradle; las
referencias que mejor funcionan parten la app en 12-41 módulos con arquitectura limpia** (`data`/
`domain`/`presentation` por capacidad). No es cosmética: es lo que permite probar, aislar y
sustituir una capacidad sin arrastrar a las otras, y el prerrequisito para que F4 dé pruebas a los
cuatro módulos que hoy no las tienen. Brechas concretas que salen de los nombres —y que F3 paso 3
confirma leyendo el código, no el nombre—:

1. **Arquitectura limpia por capacidad** (komi-store, houri, los derivados de Mihon): separar
   `data`/`domain`/`presentation`. cero-X tiene la lógica partida en paquetes `modules/*` pero todo
   compila en `:app`. Modularización progresiva en F5, empezando por las capacidades que F4 dote de
   pruebas: un módulo sin pruebas no se puede extraer sin romperlo.
2. **`source-api`/`source-local` como módulo propio** (todos los lectores): el contrato de fuentes
   aislado y testeable. En cero-X vive en `modules/source_api` dentro de `:app`; endurecer
   `extension_factory` (4 fuentes, 0 pruebas según F2) es el paso previo a poder extraerlo.
3. **IA en su propio módulo** (`private-gemini-bridge` de Tadami, `llamatik-native` de houri):
   cero-X tiene `ai`/`aihub` en `:app`; la referencia es cómo aíslan el proveedor para conmutarlo o
   quitarlo sin tocar el resto.
4. **Interproceso por AIDL** (`coredata-aidl` de komi-store): relevante para la instalación y gestión
   de APK que cero-X resuelve pidiendo `QUERY_ALL_PACKAGES` (hallazgo ② de F2, restringido por
   Play); komi-store lo hace por AIDL, que es la vía que cero-X debería evaluar.
5. **Multiplataforma real** (Kototoro a escritorio/iOS, komi-store KMP): cero-X es sólo APK de
   Android. No es una brecha a cerrar ahora —el objetivo es Android—, pero se anota para no anunciar
   escritorio/iOS sin build, que es lo que ya dice la tabla de Referencias comparadas.

Honestidad: los nombres de módulo dicen intención, no comportamiento verificado; cada brecha lleva a
un archivo que se lee en F3 paso 3 antes de afirmarla. Lo que sí es medido y no depende de nombres:
el conteo de módulos de `settings.gradle` (de 0 a 41) y el de cero-X (1).

## Asistente de la 1.0.7

`AIHubModule` ejecuta `POST /v1/chat/completions` contra el endpoint configurado.
La pantalla de IA ofrece presets de OpenAI, OpenRouter, Groq y Ollama en loopback,
pero **un preset no demuestra que el proveedor o el modelo estén disponibles**.

- HTTPS obligatorio; HTTP sólo para `localhost`, `127.0.0.1` o `::1`.
- Clave cifrada con `EncryptedSharedPreferences` y Android Keystore, sin fallback
  en claro. La capa de red no registra la clave.
- Historial limitado a 20 mensajes y únicamente en memoria.
- Red en `Dispatchers.IO`, 15 s de conexión y 90 s de lectura.
- Errores HTTP y JSON incompleto se muestran como error, nunca como respuesta.
- No hay streaming, MCP, herramientas, voz, adjuntos ni inferencia LLM local en
  este incremento. Los modelos ONNX incluidos hacen OCR/color/escalado, no chat.

Las pruebas usan un transporte falso para verificar protocolo y errores. Esto no
es una afirmación de que un proveedor externo haya contestado: hace falta una API
key del usuario y una prueba en dispositivo.

`KaiModule` queda como entrada histórica honesta, sin lista ficticia de 30
proveedores ni estados de cifrado/MCP/herramientas inexistentes. El asistente
utilizable es `ai-hub`.
