# Dónde vive cada cosa (actualizado 2026-11-08)

La regla de esta sesión: **el workspace local no guarda documentos**. Solo viven
aquí los 6 archivos de credenciales y tokens, 14 KB en total. Todo lo demás va a
GitHub o a un conector.

## Mapa

| Qué | Dónde | Fuente de verdad |
|---|---|---|
| Código de la app | `Alex17273747576777/cero-X-app` (rama `main`) | **GitHub** |
| Issues, estado de cada uno | Linear → proyecto «cero-X: mis 47 issues de GitHub» | **Linear** (el detalle) |
| Estado honesto (6 huecos, 14 mentiras) | Notion → página «cero-X — Estado honesto» | Notion (lectura), repo (texto) |
| Resumen de los 47 issues | `entorno/issues/ESTADO-DE-MIS-47-ISSUES.md` | **GitHub** |
| Bloque de README para miyomi.app | `entorno/issues/kisara-readme-miyomi.md` | **GitHub** |
| Huellas de las APK | `apk/SHA256SUMS.txt` + `apk/como-verificar.sh` | **GitHub** |
| Documentación de herramientas | `entorno-de-herramientas-de-trabajo` | **GitHub** |

## Por qué el detalle vive en Linear y no en un archivo

Un archivo markdown con 45 filas es una foto: se queda viejo en cuanto cambia un
estado. Un issue de Linear tiene estado propio, y el estado es lo que se consulta.
El markdown del repo queda como versión de lectura rápida para quien no use Linear.

## Estado medido de la cuenta

- Issues abiertos en GitHub: `total_count: 42` de terceros + 5 propios.
- De los 42, la API solo entrega **40** en `/search/issues`, con
  `incomplete_results: false`. Los 2 que faltan están declarados como **hueco de
  medición**, no como resueltos.
- 45 contables: **16 hechos** (9 de terceros implementados con commit + 5 propios
  cerrados + 2 más entre ambos conteos), 3 en curso, 11 retenidos por el hueco de
  la Activity de lector, 5 en backlog, 11 fuera de alcance con motivo, 1 duplicado.

## Lo que este mapa no arregla

Tener el estado ordenado no hace que la app funcione. Sigue sin haber Activity de
lector, sin tracker vivo, sin traducción en el APK y sin verificación en
dispositivo. Ordenar la documentación es contabilidad, no progreso del producto.

## La trampa que costó un turno (2026-11-08)

El build del APK reventó el presupuesto del workspace:
**1259,4 MB en 10 833 archivos**, cuando el límite son 128 MB / 10 000 archivos.

Causa exacta: **Gradle escribe sus cachés en `~/.gradle`**, y en este entorno
`~` es `/home/user`, que es justo lo que se guarda. Los nombres excluidos del
snapshot incluyen `.cache`, `.local`, `.npm`, `build`, `dist`… pero **`.gradle`
no está en esa lista**. 1,3 GB de caché entraron en el snapshot y se perdieron
10 827 archivos.

### La regla, ahora explícita

Nada que escriba **más de 32 MiB** va a `/home/user`. Para el build:

```sh
export GRADLE_USER_HOME=/opt/gradle-home
export ANDROID_USER_HOME=/opt/android-home
```

Y comprobación obligatoria después de cada build:

```sh
du -sh /home/user            # 14K
find /home/user -type f | wc -l   # 6
```

`WorkspaceBudgetTest` vigila el repositorio, pero **no puede vigilar `~/.gradle`**:
eso depende de cómo se invoca Gradle, no del código. Por eso la comprobación es
manual y está aquí escrita.
