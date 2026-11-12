# Los bancos de pruebas: cuáles hay y con qué medidas se sabe

## La pregunta

"Instalar VM y Sandbox". Antes de responder, se midió la máquina donde se
trabaja. Esto es lo que dio, sin redondear:

```
$ ls /dev/kvm                       → no existe
$ grep -cE "vmx|svm" /proc/cpuinfo  → 0
$ grep MemTotal /proc/meminfo       → MemTotal:  2030028 kB  (1982 MB)
$ grep MemAvailable /proc/meminfo   → MemAvailable: 1451004 kB (1417 MB)
$ df -h /dev/root                   → 25 G, 21 G libres
$ df -h /tmp                        → tmpfs, 992 M
$ which qemu-system-x86_64 emulator sdkmanager → ninguno
$ echo $ANDROID_HOME                → vacío
```

Con esos números es imposible arrancar un emulador de Android aquí, en ninguna
configuración: el oficial rechaza x86 sin KVM, y QEMU por TCG necesita más
memoria de la que existe. Escribir "VM instalada" con estas medidas delante sería un anuncio
vacío, que es justo lo que este repo persigue con `ModelHonestyGuard`.

## Los tres bancos que sí hay

**1 · El runner de GitHub Actions.** `ubuntu-26.04`. Ahí corren las pruebas y se
firman los APK: 484 pruebas JVM en dos tareas (45 s y 33 s en el corte
`69f9cd8a`), y `apk.yml` construye, firma con `apksigner` y compara la huella
contra `entorno/HUELLA-CERTIFICADO.txt`. El propio flujo lo dice en su
cabecera: *"NO prueba nada en un dispositivo: aquí no hay emulador"*.

**2 · La JVM, sin aparato.** Las pruebas del repo son JUnit sobre la JVM: leen
fuentes, miden ZIPs, comprueban reglas. No necesitan teléfono ni emulador, y
por eso son el banco que se usa en cada commit. Es también el banco donde se
prueba `Bancos` mismo.

**3 · El LG V60 ThinQ 5G.** El aparato del usuario, arm64-v8a, que es para el
que se corta el APK por defecto (`-PsoloAbi`). Nueve capturas del 11/11 a las
21:07-21:08 con la 1.0.2 dejan constancia de lo que se ejercitó de verdad:
panel con 37/38 módulos listos, QuickJS 0.9.2 respondiendo en 3 ms, 21 sitios
conformes y 3 por migrar, IPTV en "Sin reproducción". Y de lo que no: el
ecualizador no se oyó, no se reprodujo vídeo, no se cargó ninguna fuente.

## El sandbox no es una VM

En el aparato hay dos sandbox reales y ya publicados:

* **El de extensiones**: `modules/extension_sandbox` con QuickJS. Las
  extensiones corren en un motor JavaScript dentro del proceso, no en una
  máquina virtual, y su estado se informa en la sección "MOTOR JAVASCRIPT".
* **El de privilegios**: `shell/ShizukuShell.kt` y `shell/RootShell.kt` sobre
  `dev.rikka.shizuku:api:13.1.5`. El AIDL copiado a mano se borró y se comparó
  contra el artefacto con `javap`; el detalle está en `docs/SHIZUKU-AIDL.md`.
  Su estado se informa en la sección "PRIVILEGIOS".

Lo que faltaba era que la app dijese **en qué banco está ella misma**, con
medidas del aparato. Eso es `modules/diagnostics/Bancos.kt` (decide) más
`BancosAndroid.kt` (mide), y se pinta en la sección "BANCO DE PRUEBAS" de la
pantalla de estado: físico o emulado, núcleos, ABI, RAM, `/dev/kvm`, banderas
`vmx`/`svm`, y qué sandbox hay instalado alrededor (Shizuku, Island, Parallel
Space, perfiles de usuario). Si no hay ninguno, lo dice: no es un fallo.

## La regla

Un banco se declara con medidas. `Bancos.bancoDeclarado(afirmacion, medidas)`
devuelve "no es un banco, es una intención" cuando la lista de medidas viene
vacía, y `BancosDeclaradosTest` comprueba en cada compilación que este
documento lleva las suyas (`/dev/kvm`, `vmx`, `MemTotal`, `1982`) y que ningún
flujo finge arrancar un emulador.
