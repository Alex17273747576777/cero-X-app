# Inventario de cascarones y recorridos

Corte: fase de navegación, 2026-11-12. «Retirado» significa que ya no se promete en la interfaz; no que el motor esté terminado.

## Convertidos en esta fase

| Zona | Cascarón anterior | Recorrido actual |
|---|---|---|
| Barra principal | Siete destinos, iconos repetidos y «Ir a modos» duplicado | Inicio, Biblioteca, Fuentes, Web e IA; cinco iconos y acciones concretas |
| Estado de pestaña | Estado no guardado y fragments apilados | `rememberSaveable`; destinos principales sin apilar copias |
| Inicio | Panel genérico | Accesos a leer/fuentes/IA, diagnóstico e inicio real de módulos diferidos |
| Ajustes | Funciones dispersas o inalcanzables | Diseño, Modos, IPTV, apps, idiomas y estado abren pantallas declaradas |
| Catálogo de apps | «Filtro no implementado» | Texto+categoría combinados, contador, vacío y apertura HTTPS del repositorio |
| Diseño | Switch/slider de blur que no renderizaban | Sólo selección de paleta; se aplica en Compose al elegir o volver |
| Modos | Cambiar una cadena para 14 modos, muchos sin pantalla | Seis modos navegables con Intent real; los demás no se ofrecen |
| Panel diferido | `Thread.sleep` para refrescar | Corrutina y nueva lectura del gestor |
| Booru | Botón que sólo explicaba la ausencia de cliente | Safebooru real: búsqueda safe, páginas, miniaturas, ficha/original y favoritos externos; acceso en Ajustes |

Pruebas: `CatalogoFiltroTest`, `ModosNavegablesTest`, `NavegacionPrincipalTest`, más las guardas previas `PantallasTest`, `NavegacionTest` y `WebPantallaTest`.

## Pendientes visibles o previamente visibles

| Elemento | Estado honesto | Siguiente criterio de salida |
|---|---|---|
| Otros Booru y visor avanzado | Safebooru sí está integrado; otros orígenes, descargas, zoom y vídeo no | Integrar cada API tras prueba propia; no ampliar la lista por compatibilidad nominal |
| Kotatsu | Explorador de índice dentro de Fuentes; no ejecución de parsers | Integración compatible o mantener explorador claramente limitado; nunca llamarlo fuente ejecutándose |
| Catálogo de apps | Metadatos estáticos; abrir repositorio no instala ni verifica una release | Separar directorios curados y tiendas, auditar formatos/licencias y no descargar APK implícitamente |
| Proveedor IA | Chat con HTTP real y credencial segura, pero sin proveedor autenticado ejecutado aquí | Prueba opt-in con credencial real; registrar modelo/HTTP y no simular respuesta |
| LG V60 / MangaDex Android | APK de instrumentación compilado; sin dispositivo | Ejecutar DEX→búsqueda→capítulos→páginas→imagen en Android y prueba visual |

## Código no expuesto como opción

- `ModuleDashboardFragment` conserva una búsqueda sin listener y un click que sólo registra Log. Ya no es un destino de la navegación nueva. Debe repararse antes de volver a enlazarse o retirarse en una limpieza posterior.
- Módulos de fábrica de extensiones, entornos, servidores, audio genérico, herramientas de descarga e ingeniería inversa no tienen un recorrido Android completo. No aparecen como modos seleccionables.
- Los métodos que devuelven fallo con motivo no se consideran por sí solos cascarones: el problema es una interfaz que prometa lo contrario.
- Las pantallas Views heredadas (`CatalogoActivity`, IPTV, Kisara y catálogo) mantienen el tema del sistema. Su rediseño visual y accesibilidad completa sigue pendiente; esta fase rediseña la navegación y las acciones, no certifica un rediseño visual de toda la app.

## Regla aprendida

No basta con sustituir un falso éxito por un Toast de «no implementado»: el control sigue siendo un cascarón visible. La corrección es conectar el recorrido o retirarlo de la interfaz y registrar el trabajo pendiente.
