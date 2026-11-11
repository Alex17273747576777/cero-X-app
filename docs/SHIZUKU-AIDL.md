# El AIDL de Shizuku: por qué ya no hay copias en el repo

## El síntoma

`:app:minifyReleaseWithR8` fallaba al construir el APK:

```
Type moe.shizuku.server.IShizukuApplication$Stub is defined multiple times:
  …/aidl-13.1.5-runtime.jar:moe/shizuku/server/IShizukuApplication$Stub.class
  …/javac/release/…/classes/moe/shizuku/server/IShizukuApplication$Stub.class
```

Dos orígenes para la misma clase: el artefacto `dev.rikka.shizuku:aidl:13.1.5`
(dependencia transitiva de `dev.rikka.shizuku:api:13.1.5`) y cuatro `.aidl`
copiados a mano en `android/app/src/main/aidl/moe/shizuku/server/`. R8 no admite
clases duplicadas y tiene razón: con dos versiones de un contrato binder nadie
sabe cuál se está llamando.

## Antes de borrar nada, se comparó

Descargado `aidl-13.1.5.aar` de Maven Central, extraído `classes.jar` y leídas las
interfaces con `javap`. Los `.aidl` declaran códigos explícitos (`= 7`); el código
generado los publica como `FIRST_CALL_TRANSACTION + N`, que es 1, así que `javap`
enseña `N + 1`. **No hay desfase**: el `newProcess = 7` del repo es el
`TRANSACTION_newProcess = 8` del artefacto.

| método | `.aidl` del repo | artefacto (javap) |
|---|---|---|
| getVersion | 2 | 3 |
| getUid | 3 | 4 |
| checkPermission | 4 | 5 |
| newProcess | 7 | 8 |
| getSELinuxContext | 8 | 9 |
| getSystemProperty | 9 | 10 |
| setSystemProperty | 10 | 11 |
| addUserService | 11 | 12 |
| removeUserService | 12 | 13 |
| requestPermission | 14 | 15 |
| checkSelfPermission | 15 | 16 |
| shouldShowRequestPermissionRationale | 16 | 17 |
| attachApplication | 17 | 18 |
| exit | 100 | 101 |
| attachUserService | 101 | 102 |
| isHidden | 103 | 104 |
| getFlagsForUid | 105 | 106 |
| updateFlagsForUid | 106 | 107 |

`IRemoteProcess` (sin códigos explícitos: `getOutputStream`, `getInputStream`,
`getErrorStream`, `waitFor`, `exitValue`, `destroy`, `alive`, `waitForTimeout`)
queda en 1…8 en el artefacto, que es el mismo orden. `IShizukuServiceConnection`
tiene `connected(IBinder)` y `died()` en los dos sitios. `IShizukuApplication`
declara `showPermissionConfirmation = 10000` en el repo y 10001 en el artefacto.

El artefacto además trae lo que a la copia le faltaba: `dispatchPackageChanged`,
`dispatchPermissionConfirmationResult` en `IShizukuService` (20 métodos contra 18)
y `bindApplication`, `dispatchRequestPermissionResult` en `IShizukuApplication`
(3 contra 1). Es superconjunto, no otra versión.

## Qué se hizo y por qué ese lado de la duplicación

Se borraron los cuatro `.aidl` y se dejó el artefacto oficial, por dos razones:

1. `dev.rikka.shizuku:api:13.1.5` está compilado contra `aidl:13.1.5`. Dejar la
   copia a mano habría obligado a excluir el artefacto y a rezar para que a `api`
   no le faltara ningún método de los que la copia no declaraba.
2. Nada del repo usa `IShizukuServiceConnection` ni `IShizukuApplication` (comprobado
   recorriendo los 168 ficheros Kotlin): sólo `ShizukuShell.kt`, que importa
   `IShizukuService` y `IRemoteProcess`, hace `IShizukuService.Stub.asInterface(binder)`
   y llama a `newProcess`, `waitForTimeout`, `exitValue` y `destroy`. Todo eso está
   en el artefacto.

Inventar un número de transacción es la forma más rápida de hablar con un servicio
que no entiende lo que se le pide; por eso la comparación se hizo con `javap` sobre
el artefacto publicado y no de memoria.

## Lo que sigue sin estar demostrado

Que Shizuku funcione en el V60 con GSI. Esto arregla una duplicación de clases que
impedía construir el APK; no prueba el binder contra un Shizuku real.
