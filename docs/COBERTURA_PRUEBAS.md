# Cobertura de pruebas

Lee.pe genera un informe agregado de cobertura para las pruebas unitarias JVM de los módulos Android y de `core:domain` con Kover.

## Generar el informe

Desde la raíz del repositorio:

```sh
./gradlew testDebugUnitTest :core:domain:test koverHtmlReportAll
```

El HTML aparece en `build/reports/kover/html/`. El workflow de Android lo adjunta al artefacto `android-validation-and-coverage`.

## Alcance

Kover mide pruebas que se ejecutan sobre la JVM, incluidas las pruebas unitarias Android. Las pruebas instrumentadas que corren dentro de emuladores API 26 y 35 se ejecutan en CI, pero no forman parte de este informe de cobertura. La cobertura es una métrica de código ejercitado; por sí sola no acredita calidad ni reemplaza pruebas de comportamiento.

La primera integración publica el porcentaje observado sin fijar umbrales. Se definirá un gate por módulo después de inspeccionar la primera línea base y corregir áreas sin pruebas. No se excluyen clases de producción para inflar el porcentaje.
