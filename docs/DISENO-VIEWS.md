# Rediseño estructural de Views y barras

Corte: 2026-11-12. Esta fase corrige recursos, contraste y navegación. No afirma una revisión visual en dispositivo.

## Tema

- `Theme.ceroX` usa `Theme.Material3.DayNight` con ActionBar nativa para Activities Views.
- `values/colors.xml` define superficie clara; `values-night/colors.xml` define superficie oscura. Texto primario/secundario, barras y contenedores usan atributos del tema, no valores oscuros fijos.
- `values/bools.xml` y variante nocturna cambian iconos de barras del sistema.
- `Theme.ceroX.Compose` desactiva ActionBar sólo en Main, Ajustes, Integraciones y Stress, que ya dibujan navegación Compose.
- Lector conserva pantalla completa sin ActionBar. Splash conserva su fondo de marca.

## Salidas reales

- Catálogo, Kisara, FragmentHost, Estado, Idiomas, IA y Tracker recuperan ActionBar/Volver nativos.
- Integraciones añade icono Volver a su TopAppBar; Ajustes y Stress ya lo tenían.
- Idiomas e IA conservan barra nativa porque no tenían TopAppBar propia; no se les aplicó el tema Compose para evitar dejarlos sin salida.

## Catálogo

Encabezado e instrucciones, búsqueda horizontal, márgenes convertidos de píxeles a dp, separador, estado de fuentes/errores y subtítulo al elegir capítulos. Volver de capítulos restaura resultados en lugar de cerrar la pantalla. El recorrido de negocio no cambió.

## Limpieza

Los colores `#333333`, `#666666`, `#888888` y `#DDDDDD` de items se sustituyeron por `colorSurfaceContainerHighest` o `colorOnSurfaceVariant`.

## Pruebas y límite

`DisenoViewsTest` exige base con ActionBar, tema Compose explícito, variantes nocturnas, ausencia de colores fijos en layouts, salidas en ambas tecnologías y dimensiones dp del catálogo. Compilación de recursos/Kotlin y suite JVM comprueban estructura, no apariencia. Falta revisar tamaños, contraste renderizado, foco, lector de pantalla y rotación en Android físico/LG V60.

Durante esta fase una recuperación volvió a eliminar JDK/SDK y permisos ejecutables. Se restauraron scripts y se ejecutó `scripts/preparar-entorno-android.sh`; evidencia `work/ci/entorno-recuperacion-02.log`, de nuevo `ENTORNO_OK` antes de compilar.
