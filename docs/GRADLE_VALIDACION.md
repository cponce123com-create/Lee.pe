# Gradle: descarga y validación

La versión del wrapper sigue siendo Gradle 9.5.0. No se modifican Kotlin, AGP, Compose ni Room.

## Entornos con proxy

Java no utiliza automáticamente `HTTPS_PROXY`. Si curl descarga la distribución pero el wrapper devuelve `Network is unreachable`, configurar el proxy autorizado en `~/.gradle/gradle.properties`, fuera del repositorio:

```properties
systemProp.https.proxyHost=HOST_DEL_PROXY
systemProp.https.proxyPort=PUERTO_DEL_PROXY
systemProp.http.proxyHost=HOST_DEL_PROXY
systemProp.http.proxyPort=PUERTO_DEL_PROXY
```

Sustituir los valores por los del entorno. No copiar esta configuración a otros equipos ni versionar credenciales. No desactivar la validación TLS. Eliminar o actualizar estas propiedades si cambia el proxy.

Verificar primero `./gradlew --version`. En el entorno de trabajo se verificó correctamente Gradle 9.5.0 después de configurar el proxy el 3 de noviembre de 2026. Esto verifica el arranque del wrapper, no la compilación completa de Android.

## Pruebas después de modularizar

`LeeDatabaseMigrationTest` reside en `feature:library`, donde está el componente interno `DeletedBookCleanup` que comprueba. La cadena `LeeDatabase.ALL_MIGRATIONS` se expone como una copia nueva para permitir constructores de bases de datos de integración sin exponer un array mutable compartido. No se modifica el esquema ni las migraciones.

CI compila los APK de pruebas antes de arrancar el emulador:

```bash
./gradlew --no-daemon lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest
./gradlew --no-daemon :feature:library:connectedDebugAndroidTest
```

El pipeline ejecuta además las pruebas instrumentadas de los demás módulos. Consultar los informes de la ejecución correspondiente antes de considerar aprobadas las pruebas.
