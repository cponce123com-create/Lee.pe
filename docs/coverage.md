# Cobertura verificable

Kover mide las líneas ejecutadas por pruebas JVM. Las pruebas de dispositivo en API 26 y API 35 validan integración y Compose, pero no se suman al porcentaje JVM. Un workflow verde no significa cobertura del 100%.

Ejecutar `./gradlew --continue coverageGoals koverXmlReportAll` para verificar las metas y generar informes por módulo y agregado. Ejecutar `python3 scripts/coverage_summary.py` para ver las cifras. En Actions se publica la tabla en el resumen y los XML/HTML en el artifact `coverage-goals`.

Metas conservadas: domain 60%, data 50%, app 30%, library/notes/sharecard 40%. La verificación sigue siendo informativa en CI mientras se incorpora cobertura; el resultado se muestra explícitamente como pendiente cuando no cumple. No se excluye UI ni repositorios para elevar artificialmente el porcentaje. Solo se excluyen clases generadas de recursos, BuildConfig y Room, también en el agregado.

Las pruebas nuevas cubren exportación bloqueada, timeout y reintento, cancelación sin compartir, fallo del receptor y edición congelada durante captura. Las estadísticas se prueban con sesiones de varios días y cambios de horario de verano de 23 y 25 horas.

Para avanzar al 100% se deben abrir los informes HTML, priorizar ramas de pérdida de datos y errores, agregar pruebas observables y volver a medir. Alcanzar el 100% de líneas no prueba ausencia de bugs ni cobertura de todas las combinaciones.
