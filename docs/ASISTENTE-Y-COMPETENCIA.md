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
