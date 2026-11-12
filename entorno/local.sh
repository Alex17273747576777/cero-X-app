# shellcheck shell=bash
# Cargar con: source entorno/local.sh (desde la raíz del repositorio).
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/.local/share/cerox-android-sdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/opt/cerox/gradle}"
export CEROX_M2_REPO="${CEROX_M2_REPO:-/opt/cerox/m2}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
