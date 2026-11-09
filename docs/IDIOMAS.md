# Idiomas, registro y traducción

## La corrección de partida

El README decía que la app era en español dominicano. Eso era **una mala lectura
de la regla**: el español dominicano es obligatorio para *lo que publica La
Bandita* —el contenido que escribimos—, no para las apps que hacemos. Aplicarlo a
la interfaz obligaba a alguien de Yakarta a leer «Descargas» en dominicano.

La app se comparte con toda La Bandita, no solo la hispana. Por eso la interfaz
tiene los idiomas que **existen de verdad** en los repositorios de extensiones.

## Los números, medidos el 2026-11-08/09

| cosa | número | de dónde sale |
|---|---:|---|
| idiomas de contenido en Keiyoushi | **20** | 1556 extensiones: en 455 · all 153 · ja 141 · pt 128 · es 112 · id 96 · vi 84 · tr 80 · fr 63 · zh 63 · ar 62 · th 48 · ru 31 · it 19 · ko 9 · uk 7 · ca 2 · bg 1 · cs 1 · de 1 |
| locales de interfaz de Mihon | 70 | `mihonapp/mihon/i18n/.../moko-resources` |
| idiomas de interfaz de cero-X | **40** | los 20 de contenido + los que soportan los proveedores |
| idiomas con textos escritos a mano | **7** | es, en, pt, id, ja, zh, ar — 56 cadenas cada uno |
| idiomas a los que se puede traducir en el dispositivo | 35 | los que soportan Google/Microsoft/LibreTranslate |
| proveedores de traducción | 6 | local, LibreTranslate, Google, DeepL, Microsoft, Youdao |

Comprobado dentro del APK, no en el fuente:

```
$ aapt2 dump resources app-debug.apk | grep -A8 string/titulo_idiomas
  () "Idiomas"   (ja) "言語"   (id) "Bahasa"   (zh) "语言"
  (en) "Languages"   (ar) "اللغات"   (pt) "Idiomas"
```

## Modo neutro y modo con modismos

No son dos idiomas: son dos **registros**.

| registro | qué hace | ejemplo |
|---|---|---|
| **Neutro** (de fábrica) | no aplica ninguna tabla regional; se entiende en todo el ámbito hispano | «Toma el autobús y paga con el móvil» |
| **Con modismos** | aplica la variante elegida | es-DO: «Coge la guagua y paga con el celular» |

El registro se aplica **antes** de traducir: si se aplicara después, la tabla
dominicana intentaría cambiar palabras inglesas.

### El fallo que destapó la prueba

La tabla decía `autobús → guagua`. Al sustituir palabra por palabra, «el autobús»
se convertía en **«el guagua»**: concordancia rota. La prueba
`rioplatense_y_dominicana_no_son_lo_mismo` lo cazó esperando «la guagua».

Arreglo: `Variante.Definicion` tiene `sintagmas`, que se aplican **antes** que las
palabras sueltas, porque la unidad de sustitución tiene que ser el sintagma entero.

## Privacidad de la traducción

| proveedor | saca el texto del dispositivo | pide clave | es el de fábrica |
|---|---|---|---|
| **local** | **no** | no | **sí** |
| LibreTranslate | sí (servidor público) | no | no |
| Google / DeepL / Microsoft / Youdao | sí | sí (se guarda cifrada con el Keystore) | no |

El aviso de la pantalla cambia con el proveedor y está probado: ningún proveedor
que mande texto fuera puede mostrar el aviso de «nada sale del dispositivo».

## Lo que no es verdad todavía

- Los 7 idiomas escritos a mano son 56 cadenas. Los otros 33 idiomas de la lista
  se muestran **en inglés** y la pantalla lo dice antes de elegirlos; no hay
  traducción inventada.
- La traducción en el dispositivo escribe un JSON en el almacenamiento externo de
  la app y `TextosTraducidos` lo resuelve. **No** cambia los textos del propio
  Android (los botones de las cajas de diálogo del sistema).
- No se ha hecho ni una llamada real a Google, DeepL ni Microsoft: no hay claves.
  Lo que está probado es la forma de la petición y el lector de cada respuesta,
  con capturas de la documentación.
