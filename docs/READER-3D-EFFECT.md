# Efecto de paso de página en EPUB

En los ajustes del lector, **Animaciones de página** activa el volteo 3D o el deslizamiento. El volteo usa una captura temporal de la página para dibujar una malla que sigue el gesto. Readium conserva el control de la navegación y la posición de lectura.

Con el interruptor apagado, el lector pasa la página mediante un corte directo. Omite la captura y el cálculo de la malla, lo que reduce el trabajo de CPU y GPU. Si la escala de animación de Android es cero, el interruptor se muestra apagado y se aplica el corte directo, incluso si el estilo guardado era 3D. Al restablecer la escala, se recupera la preferencia guardada.

## Medición

Ejecuta la comparación en un dispositivo físico de gama media con la escala de animación activa.

```bash
./gradlew -PenableBenchmarks=true -Pandroid.testInstrumentationRunnerArguments.class=pe.lee.benchmark.PageTurnBenchmark :benchmark:connectedBenchmarkAndroidTest
```

Los casos `curl` y `cut` usan el mismo EPUB de prueba y un arranque cálido. Compara el tiempo de inicio y los percentiles de duración de fotograma, especialmente p95 y p99. Usa el trazado de Perfetto para aislar el gesto del arranque y la apertura del EPUB. La duración de fotograma permite evaluar fluidez, pero no equivale a una lectura directa de FPS.
