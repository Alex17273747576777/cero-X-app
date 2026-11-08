# Lo que se construyó el 2026-11-08

Seis módulos nuevos, **205 pruebas en total, 0 fallos**. Lo que sigue dice qué
hace cada uno y, sobre todo, **qué no hace**.

## rendimiento — 18 pruebas

Cinco perfiles: ahorro, equilibrio, rendimiento, calidad, adaptativo.

Cada perfil declara **qué pierde**. Un perfil que solo dice lo que gana no es un
perfil, es una etiqueta. «Calidad» dice en voz alta que `esrgan_x4` tarda
**24,6 s por imagen**.

El adaptativo, en orden de prioridad:

1. Térmico severo → ahorro. Un móvil a 45 °C va a bajar Hz solo; mejor antes de
   que se note como tirones.
2. Batería ≤ 15 % sin cargar → ahorro.
3. Cargando + térmico bien + RAM sana + wifi → rendimiento. Cargando es el único
   momento en que gastar batería no cuesta nada.
4. RAM < 512 MB o `lowRamDevice` → ahorro.
5. El resto → equilibrio.

**Nunca elige CALIDAD por su cuenta.** 24,6 s por imagen no los decide la app.

FPS y Hz con nombre propio: rendimiento fija 120 fps y **no** deja bajar los Hz a
media lectura, porque los FPS estables exigen no cambiar la tasa.

**Lo que no hace:** no toca gobernadores de CPU, ni Hz del sistema, ni ajustes de
otras apps. Eso afecta a todo el teléfono y no es de la app.

## privilegios — 13 pruebas

Instalado, corriendo y autorizado son **tres escalones**, no uno. La versión
anterior de cero-X devolvía éxito en los tres métodos de instalación sin hacer
nada; esta no puede, porque `usable` exige los tres.

**Lo que no se puede comprobar, y se dice:** sin un binder client de Shizuku,
`authorized` no se puede saber. Se queda en `false` con el motivo escrito en el
`healthCheck`, en vez de asumir `true` y hacer que `usable` mienta.

`AxManager` y `OmniShizuku` están en `UNVERIFIED_NAMES`: declarados como **no
verificados** en vez de inventarles un nombre de paquete para que la lista
parezca completa.

## licencia — 15 pruebas

**Apache-2.0**, y esto corrigió un error mío. Escribí que el linaje era GPL-3.0;
la API de GitHub dice que `mihonapp/mihon`, `komikku-app/komikku` y
`PineappleTwilight/houri` son los tres Apache-2.0.

Consecuencia práctica: Apache-2.0 **no** obliga a publicar el código modificado.
Sí obliga a que el aviso y el `NOTICE` lleguen a quien recibe la app, y por eso
se empaquetan en `assets/` y el build **falla** si faltan.

El `NOTICE` deja escrito que `anime4x.onnx` no declara licencia en origen.

## descargador — 25 pruebas

Cola de hasta **100 000 recursos**, con tres frenos: el número, 2 TiB de total, y
un techo por tipo de archivo. Un `.exe` desconocido cae en `OTRO` (64 MiB), no
hereda los 8 GiB de un vídeo.

Un fallo real encontrado por las pruebas: `epub` estaba en `MANGA` y en `BOOK`,
así que `classify` devolvía lo que dijera **el orden del enum**. Se resolvió en un
mapa a la vista, y hay una prueba que falla si alguna extensión vuelve a estar en
dos tipos.

Concurrencia: total 1-2 según perfil, más un freno **por host**. El fallo medido
de las 7 descargas simultáneas no puede volver, y hay una prueba que lo impide.

## transcripción — 32 pruebas

El hueco se declara en el código: `ON_DEVICE.available = false` con el motivo
(`manga-ocr` lee, no traduce). No hay forma de que la app presuma traducción.

Variantes con reglas de verdad: el dominicano prohíbe «vosotros», «ordenador» y
«móvil»; el rioplatense usa voseo. Glosario donde el término **más largo gana**.

Ajuste de tiempos que **nunca mueve el inicio**: el inicio es lo que sincroniza
con la boca. Si la traducción no cabe, el segmento se marca `comprimido` en vez
de pisar al siguiente en silencio.

Privacidad: `StylePreference` no guarda texto, ni rutas, ni voz. Una prueba por
reflexión falla si alguien añade un campo con esos nombres.

## instalación — 16 pruebas

Primer plano, segundo plano y silencioso son **tres cosas distintas**.

Sin root ni Shizuku, Android exige que alguien confirme. El plan lo dice con
`needsUserTouch = true`, `honestDescription()` no puede decir «sin tocar la
pantalla», y hay una prueba que lo comprueba. Todo plan verifica el sha256 antes
y confirma que el paquete quedó instalado después.

---

## Los huecos que siguen abiertos

1. **No hay Activity de lector.** Bloquea once issues de terceros.
2. **No hay tracker vivo (AniList/MAL).**
3. **No hay traducción en el APK.** Solo OCR.
4. **No hay inpainting LaMa.**
5. **Motor de extensiones JS sin decidir.**
6. **Sin verificación en dispositivo**: no hay `/dev/kvm`.

Ninguno de estos seis módulos los arregla. Son contabilidad y cimientos, no el
producto terminado.
