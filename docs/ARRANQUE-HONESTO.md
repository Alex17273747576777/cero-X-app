# Arranque honesto · 1.0.1

**El ANR tenía dos causas, y las dos estaban en el código, no en el teléfono.**

Lo que se veía al instalar la 1.0.0-beta: diálogo «cero-X sigue sin funcionar» y,
si se esperaba, una pantalla con seis botones abajo y nada en medio.

## Causa 1 · 36 módulos en el hilo principal

`CeroXApplication.onCreate` llamaba a `initializeAll()` y `startAll()` en el hilo
principal. Siete módulos cargan la librería nativa de ONNX **al construirse**
(`OrtEnvironment.getEnvironment()` como inicializador de campo), y los que fallan
se reintentan con `Thread.sleep` de hasta 5 s, en ese mismo hilo. En cuanto un
módulo fallaba en un aparato concreto, la suma pasaba del timeout de Android: ANR
garantizado, no aleatorio.

Arreglo:

- El arranque completo vive en el hilo `cerox-arranque-modulos`.
- Los siete `OrtEnvironment` pasan a `by lazy`: construir un módulo ya no carga
  nada nativo.
- Cinco módulos pesados (colorizador, OCR, neural-ai, ai-hub, ai-training) **no
  se intentan al abrir**: quedan perezosos y el panel los inicia al pedirlos. La
  lista vive en `ReglasArranque.PESADOS`, pura y probada. `KisaraUpscaler` no está
  ahí porque es una clase que usa el colorizador, no un módulo registrado; su
  entorno ONNX también se volvió perezoso.

## Causa 2 · una barra que no navegaba

`CeroXApp` guardaba el destino elegido en `seleccionado` y **nadie leía ese
estado**: el contenido era un contenedor de Fragments nacido vacío. Seis botones
que prometen navegación y no la dan.

Arreglo: `seleccionado` decide el contenido. «Panel» monta `PanelDeModulos`, que
lee `lifecycleManager.resumen()` en el instante y enseña por módulo: nombre,
versión, estado en palabras («listo», «funcionando», «falló», «pendiente: se
inicia al pedirlo») y reintentos. Los perezosos tienen botón «Iniciar». Los que
fallan no desaparecen de la lista: dicen que fallaron.

## Lo que esto NO es

- **No es una prueba en teléfono.** Es un arreglo razonado sobre el código más las
  pruebas de JVM del CI. Que arranque en el V60 ThinQ 5G con GSI **no está
  confirmado hasta que se instale y se abra**.
- El CI de este repo (`pruebas.yml`) compila y corre las pruebas, pero **no
  construye el APK**: empaquetar y firmar exige claves que no están en el repo.
  Ver `docs/FIRMA-NUEVA.md`.
- Los módulos perezosos siguen sin modelo: iniciarlos desde el panel pedirá los
  ONNX, y esa descarga no existe todavía. El botón dice la verdad sobre el estado
  del módulo, no promete que el modelo aparezca.
