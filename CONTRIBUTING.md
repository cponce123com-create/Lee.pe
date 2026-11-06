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

## Entorno de Scripts y Automatización

Ejecuta los comandos desde la raíz con Python 3.10 o superior, Bash, JDK 21
y el SDK Android configurado. Los scripts Python actuales usan la biblioteca
estándar. Si en el futuro se incorpora un `requirements.txt`, instala sus
dependencias en un entorno virtual local.

```bash
python3 -m venv .venv
source .venv/bin/activate
if [ -f requirements.txt ]; then python -m pip install -r requirements.txt; fi

python -m unittest discover -s scripts -p 'test_*.py'
python scripts/verify_exception_resources.py
python scripts/verify_sharecard_locale_parity.py
python scripts/coverage_summary.py --root .

# Tras compilar y con ANDROID_HOME configurado:
python scripts/verify_debug_apk.py app/build/outputs/apk/debug/app-debug.apk --variant debug

# Con un emulador o dispositivo de pruebas conectado:
adb devices
bash scripts/run_instrumentation.sh
```

El informe de cobertura lee los XML de Kover ya generados. Para comprobar la
firma release, configura la huella esperada y sigue [Firma de APK](docs/FIRMA_APK.md).
El script de instrumentación usa `adb`, limpia el logcat del dispositivo de prueba
y genera diagnósticos en `build/device-diagnostics/`.

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

Los comandos de análisis estático, cobertura, documentación, seguridad y benchmarks están en [QUALITY.md](docs/QUALITY.md).
