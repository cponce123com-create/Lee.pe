# Compilar y probar Lee.pe

Verificado el 3 de noviembre de 2026: `BUILD SUCCESSFUL` en 14 m 9 s en frío y 1 m 33 s en incremental, con 50 pruebas unitarias en verde.

## Requisitos

| Herramienta | Versión usada | Notas |
|---|---|---|
| JDK | 21 (Temurin 21.0.13+9) | AGP 9 exige 17+; `compileOptions` sigue en 17 |
| Android SDK | Platform 36 + Build-tools 36.0.0 | `compileSdk = 36`, `minSdk = 24` |
| Gradle | 9.5.0 (wrapper) | No instalar a mano: usar `gradlew` |

`local.properties` (no versionado) debe apuntar al SDK:

```properties
sdk.dir=C:/ruta/a/Android/Sdk
```

## Comandos

```bash
# Suite completa (unitarias + APK de depuración)
./gradlew --no-daemon testDebugUnitTest :core:domain:test assembleDebug

# Solo pruebas unitarias rápidas (sin empaquetar)
./gradlew testDebugUnitTest :core:domain:test

# Lint (requiere baseline actualizado, ver Fase 0)
./gradlew lintDebug

# Pruebas instrumentadas (necesita emulador o dispositivo)
./gradlew connectedDebugAndroidTest
```

> `testDebugUnitTest` **no** ejecuta `:core:domain`, que es un módulo Kotlin/JVM puro. Hay que pedir `:core:domain:test` explícitamente; si no, sus pruebas (normalización del progreso de lectura) se omiten en silencio.

## Entornos con restricciones (CI, contenedores, sandbox)

Si el home del usuario no es escribible, Android Gradle Plugin falla al crear `~/.android`:

```
java.nio.file.AccessDeniedException: /home/usuario/.android
```

Solución: redirigir el directorio de usuario de Android **y** el `user.home` del JVM a una ruta escribible:

```bash
export ANDROID_USER_HOME=/ruta/escribible/.android
export GRADLE_OPTS="-Duser.home=/ruta/escribible"
```

Además, si hay un proxy corporativo con inspección TLS (por ejemplo Kaspersky), el JDK no confía en su CA raíz aunque Windows sí: importar el certificado en `$JAVA_HOME/lib/security/cacerts` con `keytool -importcert -alias proxy`.

## Release

La versión se toma de `version.properties` (`versionCode`, `versionName`). La firma
se configura por entorno, nunca en el repositorio:

```bash
export LEE_PE_KEYSTORE=/ruta/a/release.jks
export LEE_PE_KEYSTORE_PASSWORD=...
export LEE_PE_KEY_ALIAS=...
export LEE_PE_KEY_PASSWORD=...

./gradlew bundleRelease     # AAB firmado (Play)
./gradlew assembleRelease   # APK firmado (pruebas de humo)
```

Sin esas variables el release se genera **sin firmar**: sirve para validar R8 y
medir tamaño en CI, pero no para publicar. El workflow `release.yml` compila el
AAB en tags `v*` o a mano (`workflow_dispatch`).

Tamaños medidos el 3 de noviembre de 2026: APK de release **6,4 MB**, AAB **11,8 MB**
frente a **31,8 MB** del APK de depuración.

## Resultado esperado

```
BUILD SUCCESSFUL
50 tests, 0 failures, 0 errors
app/build/outputs/apk/debug/app-debug.apk   (~31 MB, sin R8)
```

El APK de depuración ronda los 30-32 MB porque no pasa por R8 ni por compresión de recursos de release. El tamaño de release se mide en la Fase 5 del plan.

## Diagnóstico del CI (4 de noviembre de 2026)

La compilación y las pruebas instrumentadas se ejecutan en jobs independientes.
API 26 y 35 tienen un límite de 35 minutos cada una y hasta 600 segundos para
arrancar el emulador. `scripts/run_instrumentation.sh` conserva el código de fallo
y recoge logcat, estado de actividades y captura antes de cerrar el emulador.
Los artefactos se llaman `instrumentation-api-26` y `instrumentation-api-35`.
El APK puede estar disponible aunque falle un job instrumentado: descarga no
significa validación completa; revisa todos los checks del commit.

En tags `v*`, release.yml genera notas por categorías y publica APK/AAB solo si
existe el keystore configurado. Una ejecución manual compila sin publicar una release.
Sin firma se conserva el AAB de CI, pero no se publica como instalable.

Kover, detekt, ktlint y Macrobenchmark siguen pendientes de integración y validación
con el toolchain actual. No hay porcentajes de cobertura verificados todavía.
