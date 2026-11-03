# Métricas de Lee.pe

Registro de las cifras medidas durante la ejecución del plan de mejora. Cada número indica **cómo se obtuvo**; no se incluyen estimaciones sin medir.

Última actualización: 3 de noviembre de 2026 (lote 12).

## Entorno de medición

| Dato | Valor |
|---|---|
| JDK | Temurin 21.0.13+9 |
| Gradle | 9.5.0 (wrapper) |
| Android SDK | Platform 36, Build-tools 36.0.0 |
| Dispositivo | **Ninguno**: las cifras de CPU son JVM de escritorio (tests unitarios), no un teléfono |

> Las cifras de CPU sirven para comparar antes/después y para decidir arquitectura, no para prometer rendimiento en un dispositivo concreto. La validación en gama baja sigue pendiente (Fase 1, matriz manual).

## Build

| Métrica | Valor | Cómo |
|---|---|---|
| Compilación + tests + APK (frío) | 14 m 9 s | `testDebugUnitTest assembleDebug` en limpio |
| Compilación incremental + tests | 2 m 27 s | Mismo comando sin cambios de estructura |
| Pruebas unitarias | **64**, 0 fallos | `testDebugUnitTest` (57) + `:core:domain:test` (7) |
| Advertencias del compilador | **0** | Recompilación completa con `--rerun-tasks` |
| APK de depuración | **31,8 MB** | `app/build/outputs/apk/debug/app-debug.apk` |
| APK de release (R8 + shrink) | **6,4 MB** | `assembleRelease` con keystore de prueba; firma verificada con `apksigner` |
| AAB de release | **11,8 MB** | `bundleRelease` firmado |
| R8 (primera compilación de release) | 18 m 36 s | `:app:assembleRelease` en frío |
| `bundleRelease` incremental | 1 m 18 s | Con el minificado ya en caché |
| Dex | 19 archivos, 41 MB de `classes.dex` **en debug** | `unzip -l`; R8 los reduce a una fracción en release |

La diferencia entre 31,8 MB (debug) y 6,4 MB (release) es el efecto conjunto de R8, `isShrinkResources` y la eliminación de los iconos de `material-icons-extended` que no se usan.

**Firma:** `app/build.gradle.kts` lee `LEE_PE_KEYSTORE`, `LEE_PE_KEYSTORE_PASSWORD`, `LEE_PE_KEY_ALIAS` y `LEE_PE_KEY_PASSWORD` del entorno. Sin esas variables el release se genera **sin firmar**, lo que permite validar R8 en CI sin secretos. Nunca se versiona un keystore; el de prueba de esta auditoría vive en `buildtools/`, fuera del repositorio. La versión sale de `version.properties` (`versionCode=2`, `versionName=0.2.0`).

## Catálogo (P-06)

Medición con `LibraryScaleTest` (JVM de escritorio, 10 000 títulos con acentos y Ñ):

| Operación | 1 000 libros | 10 000 libros |
|---|---|---|
| Construir índice normalizado | 10 ms | **52 ms** (caliente) / 570 ms (primera pasada, con JIT frío) |
| Filtrar con consulta «garcia 9999» | — | **49 ms** |
| Ordenar con `Collator` español | — | **418 ms** |

**Decisiones tomadas a partir de estos datos:**

1. **No se adopta Paging 3 para 1.0.** La memoria del catálogo no es el problema a 10 000 libros (10 000 títulos normalizados ≈ 1-2 MB); el coste es de CPU. `observeAll()` se mantiene.
2. **El pipeline sale del hilo principal.** Índice + filtro + orden pueden sumar ~0,5 s en JVM (probablemente 1-2 s en gama baja); ahora se ejecuta en `Dispatchers.Default` dentro de `computeCatalog()` y la UI muestra el catálogo anterior mientras recalcula.
3. **Umbral de revisión:** si en dispositivo de 4 GB el catálogo tarda más de 300 ms en recalcular tras una lectura, se implementará `room3-paging` con contadores agregados (T3.4, 3 días).

## Memoria y assets

| Métrica | Antes | Después |
|---|---|---|
| `leepe_logo` (splash/lanzador) | 1 371 KB, 1254×1254 | 310 KB, 768×768 WebP sin pérdida |
| `leepe_icon_foreground` | 279 KB, 1254×1254 | 26 KB, 432×432 WebP |
| `leepe_logo_mark` (18 dp) | 279 KB, 1254×1254 → ~6 MB de heap | 3 KB, 96×96 → ~36 KB de heap |
| Presupuesto de bitmap PDF | 4 Mpx fijos (16 MB) | 2 Mpx en gama baja (8 MB) |

## Pendiente de medir

- Arranque en frío y jank reales (necesitan Macrobenchmark y dispositivo; Fase 3, T3.11).
- Memoria pico al leer un EPUB/PDF grande en gama baja.
- Tiempo de restauración de un respaldo con 2 000 libros.
- Tamaño del AAB de release tras R8 (Fase 5).
