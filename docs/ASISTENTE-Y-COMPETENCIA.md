# Asistente real y objetivo todo-en-uno

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
