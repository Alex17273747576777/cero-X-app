# Web privado: qué hace y qué no

## Motor y tecnología

La pantalla abre **Brave Search por defecto** y permite alternar con DuckDuckGo o
un SearXNG propio con base HTTPS. Google no está en la lista. La portada no inventa
una consulta al abrirse.

El navegador es un **Android WebView**, no una compilación del navegador Brave ni
un fork de `brave-browser`. Brave Search es el servicio de búsqueda; no se afirma
que el motor de renderizado sea Brave ni que se hayan copiado sus funciones.

## Bloqueo y anti-cartel

- Las peticiones pasan por `shouldInterceptRequest` y se comparan por sufijo de
  dominio contra las reglas incluidas y las del usuario.
- La lista propia se lee de `web/bloqueo.txt` dentro del almacenamiento externo
  propio de la app. Si no existe se crea una plantilla comentada. Un dominio por
  línea; `#` comenta. No se descarga ninguna lista remota sin pedirlo.
- «Ocultar carteles anti-bloqueador» está apagado por defecto. Su JavaScript
  oculta los selectores enumerados en `AntiMuro.kt`; no desactiva la detección del
  sitio, no evita paywalls y no garantiza que todos los carteles desaparezcan.

## Cookies y datos

El botón de cookies rota entre bloquear todas, bloquear terceros (defecto) y
permitir todas. «Borrar cookies y datos» limpia cookies, caché, historial del
WebView, formulario, SSL y WebStorage. No borra la lista de bloqueo del usuario.

No hay historial propio ni sincronización. DNT y Sec-GPC se envían como señales,
pero un servidor puede ignorarlas. Esto no es una VPN, Tor ni anonimato total:
la red sigue viendo las conexiones y cada sitio recibe los datos necesarios para
responder. Sólo se aceptan URL HTTPS en la barra; HTTP se rechaza en vez de
fingir que es privado.

## Descargas y FMHY

Los enlaces descargables se entregan al **DownloadManager de Android**, con su
notificación y su archivo en la carpeta pública de descargas. No se reimplementó
Ghost Downloader ni se incluyó código de ese proyecto. La app no instala lo
descargado.

El botón FMHY abre `https://fmhy.net/`. Es un catálogo externo, no contenido
verificado o incluido por cero-X; cada enlace y cada descarga son responsabilidad
de quien los abre.

## Pruebas

Las decisiones de URL, motores, cookies, lista de bloqueo y selectores se prueban
en JVM. La pantalla tiene pruebas de IDs, navegación, recursos y cableado. Eso no
demuestra compatibilidad de todos los sitios ni comportamiento de WebView en un
aparato: la prueba visual y de descargas debe hacerse en el emulador o en el V60.
