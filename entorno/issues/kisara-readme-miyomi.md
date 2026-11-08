# Bloque para que Kisara aparezca en miyomi.app

Pegar en el README de `ifritraen/kisara`. El formato es el que miyomi.app usa
para el resto de fichas: **nombre**, **tipo**, **una línea de descripción** y
**un párrafo** de presentación. Verificado contra fichas reales del sitio
(Reikai, Mihon, Kototoro, Futon).

---

## Lo que hay que rellenar en el formulario de miyomi.app

| campo | valor |
|---|---|
| **Nombre** | Kisara |
| **Plataforma** | Android |
| **Tipo** | Manga (+ Anime, si los visores de vídeo están conectados) |
| **Fork de** | Mihon |
| **Repositorio** | https://github.com/ifritraen/kisara |
| **Licencia** | Apache-2.0 |

---

## Texto para el README

```markdown
## Kisara

**Manga · Anime** — Fork de Mihon

Lector de manga y anime para Android con IA que corre dentro del teléfono:
colorización, reescalado y recomendaciones sin enviar nada a un servidor.

### Qué tiene de distinto

Kisara trae modelos ONNX empaquetados en el propio APK, no descargados a demanda.
Funcionan sin conexión y sin cuenta:

- **Colorización de páginas** en blanco y negro con `colorize.onnx` (117,26 MiB).
  Una página tarda ~1 490 ms en un procesador x86 de 2 núcleos; en un móvil
  moderno baja bastante, pero no es instantáneo y no se promete que lo sea.
- **Reescalado ×4** con dos modelos según la página: `anime4x.onnx` (4,92 MiB,
  2 673 ms) para línea clara y `esrgan_x4.onnx` (63,95 MiB, 24 587 ms) para el
  resto. La diferencia de tiempo es de 9×, así que elegir bien importa.
- **OCR de manga** con manga-ocr (`encoder_fp16.onnx` 163,89 MiB + 
  `decoder_model_quantized.onnx` 28,26 MiB): lee el texto japonés de la página.
- **Sugerencias calculadas en el teléfono** con TF-IDF sobre autores y etiquetas.
  El historial no sale del aparato. Cada sugerencia lleva su motivo
  («Porque lees naoki urasawa»), no un porcentaje suelto.

### Qué NO tiene todavía

Esta sección existe porque un README sin ella es publicidad:

- La **traducción** del texto reconocido no está conectada: el OCR lee, pero no
  traduce. No hay runtime de traducción en el APK.
- El **inpainting** de bocadillos (borrar el texto original antes de poner la
  traducción) no está: el modelo LaMa no se empaqueta.
- La IA está conectada a un pipeline de páginas, pero **la pantalla del lector
  que lo invoca no está terminada**.

### Construir

```bash
git clone https://github.com/ifritraen/kisara
cd kisara
./gradlew assembleDebug
```

Los modelos van versionados con LFS; si no se descargan, la app arranca pero la
IA está desactivada.

### Verificar una descarga

Cada release publica `SHA256SUMS.txt` con la huella de **cada** APK:

```bash
sha256sum -c SHA256SUMS.txt
```

Si no hay huellas, no se puede saber si el archivo llegó entero — y un APK
corrupto se instala igual y falla después.
```

---

## Por qué este texto y no otro

**No dice «revolucionario», «potenciado por IA» ni «el mejor lector».** Dice
pesos, milisegundos y qué falta. Un README que promete y no cumple consigue que
la gente pruebe la app una vez y la desinstale; uno que dice la verdad consigue
que la pruebe sabiendo qué esperar.

**Incluye la sección «qué no tiene».** No es una debilidad de marketing: es lo
que separa un proyecto que se puede evaluar de uno que hay que probar a ciegas.

**Los números están medidos**, no estimados: los pesos salen de los archivos y
los tiempos de ejecutar cada modelo con ONNX Runtime en un contenedor x86 de 2
núcleos. No son tiempos de móvil y el texto lo dice.

**El apartado de verificación** no es relleno: en el ecosistema se publican
cientos de MiB sin una sola huella con la que comprobar la descarga. Con
`SHA256SUMS.txt` por release, cualquiera puede saberlo en dos segundos.

---

## Nota sobre el comentario de Discord

El issue original invita a un Discord. El enlace
`https://discord.gg/miyomi-1239848981836595281` parece **incompleto o
generado automáticamente**: los enlaces de invitación de Discord tienen 8–10
caracteres y este lleva el nombre del servidor pegado al identificador. Si el
envío a miyomi.app se hace por el formulario de su web, el Discord no hace falta;
si se hace por invitación, el enlace habría que comprobarlo antes de usarlo.
