# Compatibilidad universal · qué hacen de verdad AnymeX y Kototoro

*Medido el 8 de noviembre de 2026 contra la API de GitHub*

---

## Las cifras de los dos referentes

| | AnymeX-Preview | Kototoro |
|---|---|---|
| rama por defecto | `beta` | `devel` |
| lenguaje | Shell | Kotlin |
| estrellas | 54 | 677 |
| licencia | **ninguna declarada** | Apache-2.0 |
| tamaño del repo | 402 MB | 235 MB |
| archivos totales | 152 | 6.095 |

**AnymeX no tiene sistema de extensiones propio.** 152 archivos, cero carpetas de extensión, cero de plugin. Su valor está en otra parte: es un *preview* de empaquetado y distribución, y sus dos únicos archivos con «parser» en el nombre son `scripts/tel_parser.sed` y `scripts/tel_commit_parser.sed` — expresiones `sed`, no parsers de contenido. Tomarlo como modelo de compatibilidad de extensiones sería un error de lectura.

**Kototoro sí es el modelo.** Y es el modelo porque no inventó un sistema: **fusionó tres que ya existían**.

---

## La arquitectura real de Kototoro

Los números, por palabra clave:

| palabra clave | archivos |
|---|---:|
| `parser` | 399 |
| `extension` | 167 |
| `backup` | 135 |
| `plugin` | 24 |

Los espacios de nombres que conviven en un solo APK lo explican todo:

```
org.skepsun.kototoro.*        ← el proyecto propio
org.koitharu.kotatsu.*        ← Kotatsu
com.lagradost.cloudstream3.*  ← Cloudstream
```

Eso es la clave. Kototoro no reescribió Mihon ni Kotatsu: **importó sus espacios de nombres**. Un `ContentRepository` de Kotatsu corre dentro de Kototoro sin traducción, porque es literalmente el mismo código.

### Los tres motores que sostiene

1. **Extensiones de Mihon/Tachiyomi** — el formato APK de extensión que usa el 90 % del ecosistema. Una extensión de Mihon trae un `Source` que implementa `getPopularManga`, `getSearchManga`, `getPageList`. Si cero-X implementa esa interfaz, hereda miles de extensiones ya escritas.

2. **Parsers de Kotatsu** — `org.koitharu.kotatsu.parsers`. Un parser declara su `MangaParserSource` y sus selectores. Segunda familia, compatible por el mismo principio.

3. **Plugins de Cloudstream** — `com.lagradost.cloudstream3.plugins.Plugin`. Estos son distintos: un plugin de Cloudstream es un **APK que trae bytecode** y se carga con un `ClassLoader`. Es el más potente y el más peligroso de los tres.

### El detalle que casi nadie copia

`core/exceptions/IncompatiblePluginException.kt` y `backups/data/BackupIndexFactory.kt`.

Kototoro **no asume** que un plugin va a cargar. Declara una excepción explícita para el caso de que el plugin pida una versión de API que la app no tiene. Y en las copias de seguridad, `BackupIndexFactory` construye un índice antes de restaurar, en vez de escribir a ciegas. Restaurar un backup de otra app sin verificar el formato es cómo se corrompe una biblioteca de años.

Esas dos piezas —la excepción explícita y el índice previo— son las que separan un sistema de extensiones serio de uno que se cae.

---

## Qué implica esto para cero-X

cero-X **ya tiene** `modules/extension_factory/ExtensionFactoryModule.kt` (8.127 bytes). Está en el camino correcto. Para llegar a compatibilidad universal real hacen falta cuatro cosas, en este orden:

| # | pieza | por qué primero |
|---|---|---|
| 1 | **Contrato de extensión versionado** | sin un número de versión de API, la segunda extensión rompe la primera |
| 2 | **Cargador aislado por `ClassLoader`** | es lo que hace Cloudstream; sin aislamiento, un plugin tumba la app |
| 3 | **`IncompatiblePluginException` + detección previa** | el patrón de Kototoro: fallar claro, no fallar raro |
| 4 | **Restauración con índice y verificación** | el patrón de `BackupIndexFactory`: leer antes de escribir |

Y una decisión de diseño que hay que tomar antes de escribir código:

> **¿cero-X va a firmar las extensiones que carga, o va a aceptar extensiones de terceros sin firmar?**

Un lector que instala APKs de extensión de cualquier origen es potente y es un vector de ataque: el plugin trae bytecode con los permisos de la app. La respuesta honesta no es «sí, todas» ni «no, ninguna», sino **una lista de repositorios de confianza firmada, más una advertencia explícita cuando el origen no está en la lista**. Eso es lo que se puede defender sin engaño futurista.

---

## Lo que NO voy a afirmar

- Que «compatibilidad universal» signifique cargar absolutamente todo. No existe: cada familia de extensiones tiene su propia interfaz.
- Que AnymeX sirva de modelo de extensiones. No las tiene.
- Que meter ONNX dentro del APK haga la app más capaz. La hace más pesada. Los modelos van descargados aparte, con su hash verificado.
