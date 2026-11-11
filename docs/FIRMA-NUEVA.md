# Firma nueva para cero-X

**La clave con la que se firmó la build rota quedó huérfana.**

La 1.0.0-beta (borrada de las releases el 11 de noviembre de 2026 por romper con
un ANR y pesar 475 MB) estaba firmada con un certificado cuya huella SHA-256 es
`f03505ce861af5ddbf1a2c31fe308a5d7ae822c40689cf5893a8263b81232fb3`
(`CN=cero-X, O=La Bandita, C=DO`, RSA 4096). Ese keystore **no está en el repo ni
en el entorno de trabajo actual**: pasó lo mismo que con la clave perdida de
GLTools 1.1.0.

## Decisión tomada

Certificado **nuevo** para cero-X, documentado aquí cuando exista. Consecuencia
directa y dicha de frente: una build firmada con la clave nueva **no se instala
encima** de la 1.0.0-beta; hay que desinstalarla antes en el V60.

## Lo que falta para publicar una build

1. Generar el keystore nuevo y guardar su huella aquí.
2. Un flujo de CI que construya el APK **sin meter los 378 MB de modelos**: los
   ONNX no van dentro del APK; se descargan bajo pedido o no están.
3. Firmar con el keystore nuevo y publicar la release con sus hashes.

Hasta que existan 1 y 2, lo que hay es código arreglado y pruebas verdes, no una
APK instalable. Decirlo es parte del trato.
