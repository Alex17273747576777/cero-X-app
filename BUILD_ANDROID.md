# Guía de Compilación — cero-X App Android

## Paso 1: Instalar Android SDK

```bash
# Instalar Android Studio o las herramientas de línea de comandos
# https://developer.android.com/studio

# Configurar ANDROID_HOME
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

## Paso 2: Instalar componentes del SDK

```bash
sdkmanager "platforms;android-34"
sdkmanager "build-tools;34.0.0"
sdkmanager "platform-tools"
```

## Paso 3: Compilar cero-X

```bash
cd /home/user/cero-X-app/android

# Dar permisos al wrapper
chmod +x gradlew

# Compilar APK de debug
./gradlew assembleDebug

# El APK se genera en:
# app/build/outputs/apk/debug/app-debug.apk
```

## Paso 4: Instalar en dispositivo

```bash
# Conectar dispositivo Android y habilitar depuración USB
./gradlew installDebug
```

## Estructura del APK

- **minSdkVersion**: 26 (Android 8.0)
- **targetSdkVersion**: 34 (Android 14)
- **Architecturas**: armeabi-v7a, arm64-v8a, x86, x86_64
- **Tamaño estimado**: ~15-25 MB (sin modelos IA)

## Solución de Problemas

| Problema | Solución |
|----------|----------|
| `compileSdk` no encontrado | Instalar `platforms;android-34` |
| Dependencias no resueltas | Verificar conexión a internet y repositorios |
| Error de firma | Usar `assembleDebug` (firma de debug automática) |
| OutOfMemory | Aumentar `org.gradle.jvmargs` en gradle.properties |