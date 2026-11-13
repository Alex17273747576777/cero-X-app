#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 La Bandita
# SPDX-License-Identifier: GPL-3.0-only
# Arranca el emulador Android sin aceleración por hardware (/dev/kvm no existe).
#
# LO QUE SE MIDIÓ Y LO QUE PASÓ (2026-11-08)
# ------------------------------------------
# El entorno no tiene /dev/kvm, así que QEMU cae a TCG (emulación de CPU por
# software). Se probó dos veces y las dos terminó igual:
#
#   1ª: -cores 2, -gpu swiftshader_indirect → a los ~6 min:
#       "detected a hanging thread 'QEMU2 CPU1 thread'" y Segmentation fault
#   2ª: -cores 1, -gpu off, -memory 900    → igual, mismo mensaje
#
# Y en las dos, antes de morir, el arranque de Android nunca llegó:
#   adb devices → emulator-5554  (aparece)
#   getprop sys.boot_completed  → vacío durante más de 8 minutos
#
# Conclusión honesta: la VM arranca y ADB la ve, pero **no completa el arranque**
# en 2 vCPU sin KVM. No es «probado en dispositivo» y no se va a decir que sí.
#
# Lo que SÍ se puede hacer con este script en una máquina con KVM es lo de
# siempre: instalar el APK de la arquitectura del emulador y abrir el lector.
set -euo pipefail

export ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}
export ANDROID_SDK_ROOT=$ANDROID_HOME
export ANDROID_AVD_HOME=${ANDROID_AVD_HOME:-/opt/vm/avd}
export ANDROID_EMULATOR_HOME=${ANDROID_EMULATOR_HOME:-/opt/vm}

AVD=${AVD:-cerox}
IMG="system-images;android-30;aosp_atd;x86_64"

if ! "$ANDROID_HOME/emulator/emulator" -list-avds | grep -qx "$AVD"; then
  sudo mkdir -p "$ANDROID_AVD_HOME" && sudo chown -R "$(id -u):$(id -g)" "$(dirname "$ANDROID_AVD_HOME")"
  echo no | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
    -n "$AVD" -k "$IMG" -d pixel_5 --force
fi

ACCEL=auto
if [ ! -e /dev/kvm ]; then
  echo "AVISO: no hay /dev/kvm. Se arranca con -accel off (TCG). Ver la cabecera." >&2
  ACCEL=off
fi

exec "$ANDROID_HOME/emulator/emulator" -avd "$AVD" \
  -no-window -no-audio -no-snapshot -no-boot-anim -no-metrics \
  -gpu "${GPU:-swiftshader_indirect}" -accel "$ACCEL" \
  -memory "${RAM:-1024}" -cores "${CORES:-1}"
