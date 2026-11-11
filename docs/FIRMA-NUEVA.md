# Firma de cero-X

## El certificado anterior se perdió

La 1.0.0-beta (borrada de las releases el 2026-11-11: 475 MB y un ANR al abrir)
estaba firmada con un certificado cuya huella SHA-256 era
`f03505ce861af5ddbf1a2c31fe308a5d7ae822c40689cf5893a8263b81232fb3`
(`CN=cero-X, O=La Bandita, C=DO`, RSA 4096). Ese keystore **no está en el repo ni
en el entorno de trabajo**: se firmó a mano en una máquina y la clave no se guardó
en ninguna parte recuperable.

Consecuencia, dicha de frente: una build firmada con un certificado nuevo **no se
instala encima** de la 1.0.0-beta. En el V60 hay que desinstalar la vieja antes.

## El certificado actual

Generado el 2026-11-11 con `keytool` (PKCS12, RSA 4096, válido hasta 2056-11-03):

- Alias: `cerox`
- Sujeto: `CN=cero-X, OU=Arranque honesto, O=La Bandita, L=Santo Domingo, C=DO`
- Huella SHA-256: la que hay en `entorno/HUELLA-CERTIFICADO.txt`, un solo fichero
  que leen el flujo que construye el APK y el que audita lo publicado. Antes esa
  huella estaba escrita a mano en el flujo de auditoría y valía la del certificado
  perdido: toda build nueva habría salido «no aprobada» sin que se supiera por qué.
- Dónde vive la clave: en los secretos del repo (`CEROX_KEYSTORE_B64`,
  `CEROX_KEYSTORE_PASS`, `CEROX_KEY_ALIAS`, `CEROX_KEY_PASS`). En PKCS12 la
  contraseña del almacén y la de la clave son la misma: `keytool` ignora `-keypass`
  y lo avisa.
- **Copia de seguridad fuera de GitHub:** necesaria. El keystore son 4.245 bytes;
  guardarlo en un gestor de contraseñas o en un disco propio no cuesta nada, y
  perderlo otra vez significa otra vez desinstalar y volver a empezar.

## Cómo se construye un APK ahora

`.github/workflows/apk.yml`, disparado a mano (`workflow_dispatch`):

1. Escribe el keystore desde el secreto en un temporal del runner.
2. `:app:assembleRelease -PsinModelos -PsoloAbi=arm64-v8a` — release con R8, una
   arquitectura (la del V60 ThinQ 5G) y **sin modelos**: los cinco ONNX pesan
   378 MiB y no van dentro del APK. El heap se sube a 6 GiB en la línea de
   comandos: con los 2 GiB de `android/gradle.properties` (medidos en una máquina
   pequeña) R8 murió de `OutOfMemoryError`. Ese fichero no se cambia; el que tiene
   memoria de sobra es el runner.
3. Verifica con el `apksigner` oficial, compara la huella con la del fichero y
   cuenta los `.onnx` del ZIP. Si la huella no coincide o hay un solo modelo
   dentro, el flujo falla en vez de publicar.
4. Sube el APK como artefacto y, si se pide, crea la release con el APK y su
   `sha256sums.txt`.

## Lo que sigue sin estar demostrado

- **Que la app funcione en un teléfono.** El flujo compila, firma y comprueba
  bytes; no hay emulador en CI. El arreglo del arranque de la 1.0.1 está razonado
  sobre el código y cubierto por pruebas de JVM (`ArranqueHonestoTest`,
  `FirmaNuevaTest`). Instalarlo y abrirlo en el V60 es el paso que falta.
- **Que los módulos pesados sirvan para algo al iniciarlos.** El botón «Iniciar»
  del panel dice la verdad sobre el estado del módulo, pero la descarga de los
  ONNX no existe todavía: sin modelos, el colorizador y el OCR no tienen con qué
  trabajar.
