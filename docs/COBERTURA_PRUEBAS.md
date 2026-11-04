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

No se excluyen clases de producción para inflar el porcentaje. La primera línea base no fija umbrales de cobertura porque el dato agregado incluye Compose generado, adaptadores de plataforma y otros módulos con distinta densidad de lógica.

## Primera línea base

Medición del workflow CI de la PR #59, generada el 4 de noviembre de 2026:

| Métrica global | Cubierto | Total | Porcentaje |
| --- | ---: | ---: | ---: |
| Clases | 56 | 464 | 12,1 % |
| Métodos | 95 | 1.960 | 4,8 % |
| Ramas | 274 | 4.653 | 5,9 % |
| Líneas | 671 | 8.635 | 7,8 % |
| Instrucciones | 5.490 | 105.047 | 5,2 % |

`core:domain` registra 81 de 93 líneas cubiertas (87,1 %). Los resultados de UI Compose, audio, persistencia y casos de error muestran que falta ampliar pruebas unitarias de lógica y repositorios. Las pruebas instrumentadas y sus resultados deben revisarse por separado porque no aparecen en estas cifras.

## Próximos pasos

1. Añadir pruebas unitarias a reglas de dominio, repositorios y lógica pura de respaldo/escaneo.
2. Medir por módulo después de cada grupo de pruebas; no usar porcentajes globales para ocultar áreas sin cobertura.
3. Proponer umbrales graduales solo tras revisar la segunda medición y separar, de forma justificada y transparente, código generado o no ejecutable en JVM.
