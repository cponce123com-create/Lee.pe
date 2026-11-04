# Contribuir a Lee.pe

## Entorno

Usa JDK 21, Android SDK Platform 36 y Build-tools 36.0.0. Ejecuta siempre
el wrapper `./gradlew`; las versiones se mantienen en `gradle/libs.versions.toml`.
Configura `sdk.dir` en un `local.properties` local, nunca versionado.

## Validación

```bash
python scripts/test_verify_debug_apk.py
python scripts/verify_sharecard_locale_parity.py
./gradlew lintDebug testDebugUnitTest :core:domain:test assembleDebug
# Con dispositivo/emulador conectado:
bash scripts/run_instrumentation.sh
```

Los tests JVM del dominio requieren la tarea explícita `:core:domain:test`.
Los tests de migración viven en `feature/library/src/androidTest` y usan
los esquemas exportados de `core/database/schemas`.

## Pull requests

Crea una rama desde main, conserva el alcance del cambio y usa la plantilla
existente al abrir el PR. Explica el problema, el comportamiento resultante,
la validación ejecutada y cualquier limitación. No declares pruebas aprobadas
si no se han ejecutado. Usa prefijos `feat:`, `fix:`, `docs:`, `test:` o `ci:`.

## Estilo y privacidad

Sigue las convenciones Kotlin y Material 3. Conserva las semánticas de accesibilidad,
los textos en recursos y la paridad de idiomas. La app funciona offline: no añadas
permisos de red ni copias de seguridad automáticas. No incluyas libros privados,
contraseñas, keystores o datos personales en commits o registros de pruebas.

## Conducta

Se espera trato respetuoso, críticas al código y no a las personas, y colaboración
sin acoso ni discriminación. Sigue los principios de Contributor Covenant.
No publiques información privada para reportar un incidente.
