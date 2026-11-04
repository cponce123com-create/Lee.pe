# Calidad y medición

## Comandos

```bash
./gradlew detekt ktlintCheck
./gradlew ktlintFormat                  # edición explícita; revisar el diff
./gradlew koverHtmlReportAll koverXmlReportAll
./gradlew --continue coverageGoals
./gradlew :core:domain:dokkaGenerate
./gradlew dependencyCheckAggregate
./gradlew -PenableBenchmarks=true :benchmark:connectedBenchmarkAndroidTest
```

Detekt usa su API 2.0 y analiza fuentes de todos los módulos sin resolución de
Android. ktlint produce Checkstyle y SARIF. Si falla el análisis, CI genera un
parche de formato como artefacto para revisión; no lo publica ni convierte el
fallo en éxito. Se respeta la convención de nombres de funciones `@Composable`.
Los hallazgos hacen fallar el job; no existe un baseline generado automáticamente
para esconder deuda previa. `.editorconfig` y el hook optativo ayudan a prevenirla:
`git config core.hooksPath .githooks`.

## Cobertura

Kover 0.9.11 ya estaba integrado; se añaden objetivos de líneas: dominio 60%, datos
50%, features 40%, app 30%. `coverageGoals` falla si no se cumplen. En CI esta
medición es informativa durante la adopción, deja advertencia y resultado en el
resumen, y no impide descargar un APK ya validado. No significa que los objetivos
se hayan alcanzado. Solo se excluyen clases Android/Room generadas; no se excluye
Compose ni lógica de negocio para inflar cifras. Kover mide tests JVM, no cobertura
de las pruebas instrumentadas.

## Seguridad y notificaciones

El job de dependencias corre en main o manualmente y falla con CVSS >= 7.
`NVD_API_KEY` es opcional pero reduce los límites de descarga de la base NVD.
Un error de descarga no equivale a ausencia de vulnerabilidades. Los informes
HTML/JSON/SARIF quedan como artefactos. `SLACK_WEBHOOK` habilita avisos de fallos
del workflow Android en pushes a main; sin ese secreto se informa que no está configurado.

## Macrobenchmark

El módulo se activa solo con `-PenableBenchmarks=true`. Usa un APK `benchmark`
no depurable, con firma de pruebas, profileable y una actividad de preparación
exclusiva de esa variante. Esa actividad y sus 60 libros nunca se empaquetan en
release/debug. Las tres mediciones son arranque frío, desplazamiento y apertura
EPUB. La apertura espera al WebView, no demuestra que se haya renderizado todo el capítulo.
CI usa API 35 y tolera únicamente la advertencia EMULATOR; las cifras no sustituyen
las de un teléfono real. Para medir en dispositivo no pases suppressErrors.

## Estado

La ejecución Android `41924738918` del commit `4de86c6` pasó las pruebas en API
26 y 35 y el job de construcción del APK. La migración 4→5 quedó verificada en
ambas versiones. El análisis estático inicial detectó 564 hallazgos de Detekt;
el formato posterior reduce parte de la deuda, pero no acredita cero hallazgos.
El primer benchmark compiló pero no pudo instalarse por falta de firma en su
APK de pruebas; se añadió la firma debug explícita y debe volver a ejecutarse.

La configuración y las pruebas se entregan para revisión. Los porcentajes, tiempos
y ausencia de hallazgos solo pueden confirmarse con una ejecución exitosa del
commit correspondiente. No se asigna una nota global de calidad sin esa evidencia.
