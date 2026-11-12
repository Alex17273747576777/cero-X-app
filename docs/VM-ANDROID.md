# VM Android: instalación y dos intentos medidos

Fecha: 12 de noviembre de 2026 (UTC).

## Instalación comprobada

- AVD `cerox_api31_x86_64`: Android 12, API 31, Google APIs, x86_64.
- QEMU 10.0.13, emulador 37.2.13.0, ADB 34.0.5 y JDK 21.0.13.
- Swap de 3 GiB en el host.
- Instalador repetible: `work/vm/setup-vm.sh`, fuera del repositorio y del APK.

## Intento 1: SwiftShader, TCG y 2048 MiB

ADB llegó al estado `device`; `getprop` confirmó Android 12/API 31/x86_64 y `pm`
listó paquetes del sistema. La captura PNG estaba negra y `sys.boot_completed`
no apareció. QEMU detectó hilos colgados y terminó con **139 (segfault)**.

## Intento 2: GPU de invitado, TCG y 1536 MiB

Se fijó `hw.ramSize=1536`, `hw.gpu.mode=guest` y heap de 256 MiB. ADB volvió a
confirmar Android 12/API 31/x86_64. `bootanim` siguió activo y
`sys.boot_completed` vacío. La captura no produjo bytes tras más de tres minutos
y se canceló. El host llegó a 1918 MiB de RAM usados y 1351 MiB de swap; QEMU
ocupaba unos 1610 MiB RSS. Se detuvo para no saturar el entorno.

## Alcance del resultado

La instalación y el acceso ADB mediante TCG son reales. **No está demostrado un
arranque gráfico completo y estable**. No se instaló ni se probó el APK, ni se
validó WebView en la VM. Esto tampoco demuestra que TCG sea imposible en general.

Los registros y capturas del entorno se conservan en `work/vm/evidence/`, fuera
del APK. La validación visual final sigue pendiente en el LG V60 físico o en un
banco con suficiente memoria.
