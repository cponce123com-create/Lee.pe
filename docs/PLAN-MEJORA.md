# Plan de trabajo — Lee.pe 1.0

**Objetivo:** llevar Lee.pe del estado actual (prototipo avanzado) a una **1.0 publicable**: estable, bilingüe, con rendimiento aceptable en gama baja y un proceso de release reproducible.
**Base:** [AUDITORIA-TECNICA.md](AUDITORIA-TECNICA.md), captura histórica del 3 de noviembre. En la fecha de esa captura, `main` estaba en `versionCode=3` / `versionName=0.3.0`; esa referencia no representa la versión actual del repositorio. Este plan conserva cifras y tareas de su periodo como contexto: verifica el código, la rama y las ejecuciones de CI antes de usar cualquier estado como vigente.
**Horizonte:** 11-12 semanas con 1 desarrollador a tiempo completo (≈58 días de esfuerzo); 6-7 semanas con 2 desarrolladores en paralelo.
**Regla de oro:** cada fase termina con un criterio de salida **medible**, no con «ya está hecho».

---

## 0. Estado de ejecución

Actualizado el 4 de noviembre de 2026. La corrección del arranque del PR #38 está fusionada y pasó CI en API 35. Se está reabriendo la auditoría para atender los hallazgos del listado de producción; las modificaciones de esta tanda aún requieren CI antes de marcarse como verificadas.

**Cierre de Fase 0 y Fase 1:** 28 tareas cerradas, 1 parcial (A-04 en el lector EPUB: requiere mover el estado de las hojas a `SavedStateHandle`) y 2 retiradas (A-12, A-13).

| Tarea | Estado | Evidencia |
|---|---|---|
| T0.1 documentar build | **Hecho** | `docs/BUILD.md` con JDK, SDK, variables de sandbox y tiempos |
| T0.2 endurecer CI | **Hecho** | Wrapper validation, sin tareas duplicadas, `:core:domain:test` incluido |
| T0.4 gobernanza | **Parcial** | Plantilla de PR en el repo; proteger `main` y labels requiere la interfaz de GitHub |
| T0.6 limpieza de warnings | **Hecho** | 44 → **0** warnings en recompilación completa |
| A-01 sesiones restringidas | **Parcial** | Se usa `ControllerInfo.isTrusted()` y se protege el binder local TTS; faltan pruebas con controladores Android Auto/Wear/Assistant |
| A-02 progreso TTS | **Hecho** | `readingProgressOr` + pruebas |
| A-03 respaldo v2 | **Hecho** | Colecciones múltiples, vacías incluidas, compatibilidad v1 |
| A-04 rotación | **Parcial** | Biblioteca (búsqueda, filtro, orden, diálogos) y PDF (página, zoom, rotación) sí; falta el estado interno de las hojas del lector EPUB |
| A-05 recycle de bitmaps | **Hecho** | `PdfReaderActivity`, `PdfRendering` |
| A-06 MediaController | **Hecho** | `detach()` en `onDestroy` |
| A-08 caché PDF | **Hecho** | Purga de copias > 7 días al abrir |
| A-09 StatFs | **Hecho** | Comprobación cada 8 MB |
| A-10 escritura por locator | **Hecho** | Máximo una cada 2 s |
| A-11 respaldo O(n) | **Hecho** | Una sola lectura de preferencias |
| A-13 | **Retirado** | El compilador demostró que la comprobación era necesaria |
| A-14 startService | **Hecho** | Solo `bindService`; Media3 promueve a primer plano |
| A-18 WakeLock | **Hecho** | Timeout de 30 min |
| A-24 timeout de tarjeta | **Hecho** | `withTimeout(15 s)` + `finally` |
| A-25 cerrar Publication | **Hecho** | `DisposableEffect(uri)` con guarda TTS |
| A-26 TTS huérfano | **Hecho** | `activeBookUri` + re-adhesión |
| A-29 atrás / predictive back | **Hecho** | `BackHandler` + manifiesto |
| A-34 locator inválido | **Hecho** | Validación contra `readingOrder` |
| A-21 OOM de tarjetas | **Hecho** | Buffer de `PixelMap` + `OutOfMemoryError` informado + prueba |
| A-22 captura frágil | **Hecho** | Frame clock opcional, ya no rompe la pantalla |
| A-37 día congelado | **Hecho** | Recalculo a medianoche mientras la app sigue abierta |
| A-38 outbox | **Hecho** | Reintento al volver a la app; un solo rescaneo por lote |
| P-03/P-04 concurrencia | **Hecho** | Semáforos de 2 en portadas y miniaturas |
| A-28 tiempo de lectura | **Hecho** | Solo cuenta con `!loading && error == null` (EPUB y PDF) |
| A-30 recomposición del lector | **Hecho** | Índice memorizado por publicación |
| A-31 locateProgression | **Hecho** | En IO, con cancelación del salto anterior |
| A-32 TTS vs saltos | **Hecho** | La narración se pausa antes de un salto explícito |
| A-33 borrar anotaciones | **Hecho** | Confirmación con el texto afectado en EPUB y PDF |
| A-39 borrados sin confirmar | **Hecho** | Colección (con recuento) y copia sin conexión, con cadenas ES/EN |
| P-05 memoria PDF | **Hecho** | Presupuesto de 2 Mpx en gama baja (antes 4 Mpx fijos) + 4 pruebas |
| P-07 assets | **Hecho** | WebP sin pérdida: 1,9 MB menos en origen y el icono de marca pasa de 6 MB de heap a 3 KB |
| A-36 escaneo cancelado | **Hecho** | Corre en `viewModelScope` con `ScannerUiState`; sobrevive a la rotación |
| A-40 grid sin claves | **Hecho** | `LazyVerticalGrid` con `key = book.id`, `contentType` y cabeceras de línea completa |
| P-06 paginación | **Decidida con medición** | 10k: índice 52 ms, filtro 49 ms, orden 418 ms en JVM → sin Paging en 1.0, pero el cálculo sale a `Dispatchers.Default` (`docs/METRICAS.md`) |
| A-20 i18n | **Reabierto** | Quedan literales y mensajes de excepción visibles en lector EPUB/PDF/TTS/audio/tarjetas; completar `values-en` y comprobar paridad en CI antes de cerrar |
| A-19 accesibilidad | **Parcial** | 48 dp en colores, `liveRegion`, slider etiquetado, `FlowRow` del TTS, insets reales y tema claro; falta la pasada con TalkBack en dispositivo |
| A-16 release/firma | **Hecho** | `release` con R8 + shrink, firma por variables de entorno, versión desde `version.properties`, workflow `release.yml` |
| A-17 FileProvider | **Hecho** | EPUB incluido con `content://` propio y migración del URI `file://` existente; `NOTICE` con atribuciones |
| A-15 licencia | **Hecho: declaración del repositorio** | `LICENSE` y README declaran Apache-2.0; el inventario de licencias transitivas sigue pendiente |
| A-35 orden con acentos | **Hecho** | `Collator` + pruebas |
| P-01/P-02 flujos y ciclo de vida | **Hecho** | Flujos memorizados y `collectAsStateWithLifecycle` |

**Métricas tras el lote 16:** 64 pruebas, 0 fallos, 0 warnings. Release: APK 6,4 MB, AAB 11,8 MB.

**Cierre histórico del lote 16:** de las 53 tareas, **49 cerradas**, 2 retiradas (A-12, A-13) y 2 bloqueadas por decisión externa: **A-15** (licencia) y la publicación en Play Console. Queda una parcial (A-19: falta la pasada con TalkBack en dispositivo físico).

**Actualización del 4 de noviembre de 2026:** la declaración A-15 queda resuelta por `LICENSE` (Apache-2.0). El cierre numérico anterior es un registro histórico, no el estado actual. Falta revisar las licencias transitivas y la publicación en Play Console. El trabajo posterior se sigue en [AUDIT_FOLLOWUP.md](AUDIT_FOLLOWUP.md).

---

## 1. Punto de partida

| Dimensión | Hoy | Meta 1.0 |
|---|---|---|
| Estabilidad | Riesgos de crash en PDF (bitmap reciclado), audio (controlador liberado), tarjetas (OOM no capturado) | 0 crashes conocidos; crash-free ≥ 99,5 % |
| Seguridad | Servicios exportados sin restricción; binder TTS público | Sesiones restringidas; nada expuesto a terceros |
| Datos | Respaldo pierde colecciones; copias PDF sin purga | Respaldo v2 con ida y vuelta verificada |
| Rendimiento | Flows Room recreados, portadas serializadas, biblioteca completa en RAM | Jank < 1 %; 10 000 libros sin OOM |
| i18n | 151 literales en español; `values-en` cubre biblioteca y tarjetas | 0 literales; ES/EN completos |
| Release | Solo `assembleDebug`, sin firma ni R8 | AAB firmado, `versionCode` automático, rollout |
| Build | Compila; 38 pruebas verdes (35 + 3 de `:core:domain`); APK debug 32,1 MB | AAB release ≤ 20 MB, firma y R8 |
| Proceso | CI verde pero sin validación de wrapper ni matriz de API | CI completa + releases cada 2 semanas |

---

## 2. Principios de ejecución

1. **Estabilizar antes de refactorizar.** Ninguna tarea de arquitectura (Fase 6) entra antes de la 1.0; mezclar refactor con arreglos es la forma más rápida de no terminar ninguna de las dos.
2. **Un PR = un hallazgo.** Como máximo dos si son del mismo archivo. PR de más de ~400 líneas se rechaza.
3. **Todo bug corregido entra con test.** Si no se puede testear (UI, rotación), entra con pasos de reproducción manual en la descripción y en la matriz de validación.
4. **Ninguna migración Room sin test instrumentado.** Room 5 → 6 exige `LeeDatabaseMigrationTest` actualizado en el mismo PR.
5. **Ningún string nuevo fuera de `strings.xml`.** La regla rige desde el primer día; el lint bloqueante de `HardcodedText` se activa al cerrar la Fase 4, cuando ya no quedan literales.
6. **Medir antes y después.** Las tareas de rendimiento (Fase 3) incluyen la cifra de partida; sin número, no se cierra.
7. **El usuario de gama baja es el usuario objetivo.** Toda validación de rendimiento se hace en un dispositivo de 4 GB o emulador equivalente, no en el teléfono del desarrollador.

---

## 3. Estructura del plan

Seis fases secuenciales con solapamiento permitido entre pruebas manuales y la fase siguiente. Cada tarea tiene ID (`T<fase>.<n>`), hallazgo asociado, esfuerzo en días y criterio de aceptación.

```
Semana  1     2     3     4     5     6     7     8     9    10    11    12
F0    ████
F1        ████████████
F2                    ██████████
F3                              ██████████████
F4                                            ████████████
F5                                                        ██████████
F6                                                              ░░░░░░ (continuo)
```

---

## 4. Fase 0 — Fundaciones (semana 1 · 4-5 días)

**Objetivo:** que el proyecto compile y se mida de forma reproducible, y que el proceso de trabajo exista. Sin esto, todo lo demás se construye sobre arena.

| ID | Tarea | Esfuerzo | Criterio de aceptación |
|---|---|---|---|
| T0.1 | Documentar el build ya verificado (JDK 21 + SDK 36 + `./gradlew --no-daemon testDebugUnitTest assembleDebug`) en `docs/BUILD.md`, incluido el *workaround* de `-Duser.home` si el entorno lo requiere | 0,5 d | Cualquier persona reproduce el build con el documento; tiempo registrado (~14 min en frío) |
| T0.2 | Endurecer CI: validación del wrapper, `--stacktrace`, quitar duplicados (`android-build.yml:58,73`), **añadir `test` para `:core:domain`** (hoy sus 3 pruebas no se ejecutan), cache condicionada, `lint` con `checkDependencies` | 1 d | Pipeline < 20 min; 38 pruebas unitarias (no 35); falla si `lint` reporta error |
| T0.3 | Activar lint de higiene con baseline temporal: `HardcodedText`, `SetTextI18n`, `ContentDescription`, `UnusedResources`, `ObsoleteSdkInt` | 0,5 d | `lintDebug` verde con baseline versionado |
| T0.4 | Gobernanza: proteger `main`, plantilla de PR, checklist de revisión, labels (`bug`, `security`, `perf`, `i18n`, `tech-debt`), milestones por fase, `CHANGELOG.md`, commits convencionales | 0,5 d | Repo configurado; primer issue/PR de ejemplo |
| T0.5 | **A-15** `LICENSE` + `NOTICE` con atribuciones (Readium BSD-3, Media3/Compose Apache-2.0) | 0,5 d | Archivos presentes; decisión legal registrada |
| T0.6 | **M-05** limpiar código muerto, imports sin uso, ramas inalcanzables, los **3 `Elvis` muertos** (`EpubTtsSessionActions.kt:125`, `ReaderActivityHost.kt:96,107`) y las **44 advertencias** (iconos a `Icons.AutoMirrored.Filled.*`, `LinearProgressIndicator` con lambda) | 1 d | `./gradlew compileDebugKotlin` sin warnings; 0 deprecaciones propias |
| T0.7 | Baseline de métricas: tamaño APK, arranque en frío, memoria de lectura, cobertura con Kover | 1 d | `docs/METRICAS.md` con cifras de partida |

**Criterio de salida F0:** CI verde reproducible, lint con baseline, licencia, y una tabla de métricas de partida firmada.

---

## 5. Fase 1 — Estabilidad y seguridad (semanas 2-3 · 13-14 días)

**Objetivo:** que nada se cierre, se pierda ni se filtre. Es la fase que convierte el prototipo en algo que se puede dejar en manos de un usuario.

### Seguridad y ciclo de vida de audio/TTS

| ID | Tarea | Hallazgo | Esf. | Criterio de aceptación |
|---|---|---|---|---|
| T1.1 | Restringir controladores: `MediaSession.Callback.onConnect()` + validación en `onGetSession()`; sacar el binder TTS a servicio no exportado o detrás de permiso de firma | A-01 | 1,5 d | App de prueba externa **no** obtiene sesión; controles del sistema y Bluetooth siguen funcionando |
| T1.2 | `detach()` del `MediaController` en `onDestroy`; sondeo ligado a `repeatOnLifecycle(STARTED)`; comprobar `isConnected` | A-06 | 1 d | 20 rotaciones durante reproducción sin excepción ni sondeo en background |
| T1.3 | `startForegroundService` + `startForeground` inmediato; `WakeLock` con timeout y liberación garantizada | A-14, A-18 | 0,5 d | Servicio arranca desde background; `dumpsys power` sin wakelock residual |
| T1.4 | Bandera de arranque en TTS (`AtomicBoolean`) para impedir bindings duplicados por doble toque | A-27 | 0,5 d | 10 toques rápidos → 1 `ServiceConnection`, 0 fugas |

### Recursos del lector

| ID | Tarea | Hallazgo | Esf. | Criterio de aceptación |
|---|---|---|---|---|
| T1.5 | `DisposableEffect` que cierre la `Publication`, retire `InputListener` y cierre el TTS navigator; manejar cancelación durante `openPublication` | A-25 | 1 d | 20 aperturas/rotaciones: memoria y descriptores estables (`dumpsys meminfo`, `/proc/self/fd`) |
| T1.6 | Re-adherir TTS al reabrir el libro (mismo `bookUri`) y decidir comportamiento al salir: parar o avisar | A-26 | 1 d | Rotar y volver: panel de TTS con estado y seguimiento de página; salir muestra aviso o detiene |
| T1.7 | Quitar `recycle()` manual; `LruCache` acotada de páginas PDF con liberación diferida | A-05 | 1 d | Scroll rápido en PDF de 500 páginas y en continuo: sin `Canvas: trying to use a recycled bitmap` |
| T1.8 | Validar `initialLocator` contra `readingOrder`; si no coincide, ignorar y limpiar columna | A-34 | 0,5 d | Locator de otro libro abre en el inicio y se corrige en Room |

### Estado y navegación (el hallazgo con más impacto percibido)

| ID | Tarea | Hallazgo | Esf. | Criterio de aceptación |
|---|---|---|---|---|
| T1.9 | `rememberSaveable`/`SavedStateHandle` en `LibraryScreen`: query, filtro, orden, colección, modo lista y diálogos seguros (`scannerPreview`, `pendingRemoval`, `editingBook`) | A-04 | 1,5 d | Rotar en biblioteca conserva búsqueda, filtro y diálogos |
| T1.10 | Editor y lector: conservar borrador de nota, búsqueda y hoja abierta; `EpubReaderSessionState` fuera de `remember(uri)` o reconstruido desde `SavedStateHandle` | A-04 | 1 d | Rotar con nota a medio escribir no pierde el texto; los resultados de búsqueda sobreviven |
| T1.11 | Visor PDF: conservar página, zoom y modo continuo al rotar; reutilizar el `PdfRenderer` en un ViewModel | A-04 | 1 d | Rotar en PDF no reabre el documento ni vuelve a la página 1 |
| T1.12 | `BackHandler` con prioridad hojas → controles → salir; `enableOnBackInvokedCallback="true"` | A-29 | 0,5 d | Atrás cierra hoja, luego oculta barra, luego sale; predictive back funciona |

### Tarjetas para compartir

| ID | Tarea | Hallazgo | Esf. | Criterio de aceptación |
|---|---|---|---|---|
| T1.13 | Captura con una sola copia de píxeles (`readPixels` a `IntArray` reutilizado), render al tamaño destino (1080×1920) y `OutOfMemoryError` tratado aparte | A-21 | 1 d | Compartir en dispositivo de 4 GB sin crash; PNG nítido (sin upscale del preview) |
| T1.14 | Captura en scope de UI, cancelable al destruirse la composición | A-22 | 0,5 d | Rotar mientras se genera no crash ni deja `GraphicsLayer` retenido |
| T1.15 | `withTimeout(15 s)` + `finally { Idle }` en `startSharing` | A-24 | 0,5 d | Estado nunca queda en «Preparando imagen…» |

### Progreso de lectura

| ID | Tarea | Hallazgo | Esf. | Criterio de aceptación |
|---|---|---|---|---|
| T1.16 | No persistir `progress` cuando `totalProgression` es nulo; reutilizar el valor previo y unificar la lógica lector/servicio | A-02 | 0,5 d | Escuchar 10 min un EPUB sin `page-list` no cambia el % del libro; test unitario con locator sin `totalProgression` |

**Criterio de salida F1:** matriz de estabilidad ejecutada — 4 pantallas (biblioteca, EPUB, PDF, audio) × 10 rotaciones × 3 escenarios (normal, carga, error) sin crashes; StrictMode sin violaciones en debug; informe de `docs/VALIDACION_ANDROID.md` con evidencia.

---

## 6. Fase 2 — Datos, respaldo y almacenamiento (semanas 4-5 · 7-8 días)

**Objetivo:** que el usuario no pierda nada y que el dispositivo no acumule basura.

| ID | Tarea | Hallazgo | Esf. | Criterio de aceptación |
|---|---|---|---|---|
| T2.1 | Respaldo **v2**: exportar `collections` + `bookCollections`; lector compatible con v1 y v2; migración de archivos antiguos | A-03 | 2 d | Round-trip: libro en 2 colecciones se restaura en 2; archivo v1 sigue importándose |
| T2.2 | Un único `DefaultHttpClient`/`AssetRetriever` por proceso en `AppContainer`; cerrar al terminar | A-07 | 0,5 d | 30 aperturas de EPUB: hilos estables |
| T2.3 | Purga al arrancar de `lee-pe-*.pdf` en `cacheDir` y de copias offline huérfanas (sin libro en Room) | A-08 | 0,5 d | Tras matar el proceso a mitad de apertura, el caché no crece entre sesiones |
| T2.4 | Comprobar espacio por bloques (8-16 MB) y contra `OpenableColumns.SIZE` | A-09 | 0,5 d | Copia de 500 MB no bloquea el hilo ni tarda por syscalls |
| T2.5 | `distinctUntilChanged().debounce(2 s)` en `currentLocator`; guardado inmediato en `onPause`/salida | A-10 | 0,5 d | Lectura en scroll: ≤1 escritura cada 2 s; progreso exacto al salir |
| T2.6 | Respaldo sin lecturas O(n) de preferencias (mapa en memoria) | A-11 | 0,5 d | Exportación de 2 000 libros < 3 s |
| T2.7 | Registrar tiempo de lectura solo con `!loading && error == null` (EPUB y PDF) | A-28 | 0,5 d | Abrir un EPUB inexistente 5 min no suma tiempo |
| T2.8 | Cambio de día a medianoche (reprogramar `todayKey`) | A-37 | 0,5 d | Con la app abierta a las 23:59, «hoy» pasa a 0 sin reiniciar |
| T2.9 | Outbox de limpieza drenado en `Application` + `ON_RESUME`, revisión incrementada una sola vez | A-38 | 0,5 d | Fallo de limpieza se reintenta al volver a la app; un solo escaneo del directorio |
| T2.10 | Implementado: exportación cifrada AES-256-GCM v2 con contraseña; lectura de JSON antiguos sin cifrar | — | Hecho | Validar exportación/restauración y compatibilidad en dispositivo |
| T2.11 | Política de migraciones: ningún cambio de esquema sin `LeeDatabaseMigrationTest` + esquema exportado | — | 1 d | Checklist en PR; CI compara `git diff core/database/schemas` |

**Criterio de salida F2:** escenario «cambio de teléfono» completo — exportar, reinstalar, importar libros, restaurar y verificar progreso, notas, colecciones y estadísticas en un segundo dispositivo o emulador.

---

## 7. Fase 3 — Rendimiento y escala (semanas 6-7 · 11-14 días)

**Objetivo:** que la app siga siendo fluida con 10 000 libros y PDFs grandes. Se mide antes y después; sin cifras no se cierra la fase.

| ID | Tarea | Hallazgo | Esf. | Criterio de aceptación |
|---|---|---|---|---|
| T3.1 | Flows estables en ViewModel + `collectAsStateWithLifecycle` (añadir `lifecycle-runtime-compose`) | P-01, P-02 | 1 d | Escribir en el buscador no relanza consultas; 0 observadores con la app en background |
| T3.2 | Concurrencia acotada (2-3) en portadas EPUB y miniaturas PDF; cancelación dentro del render | P-03, P-04 | 1 d | Fling en 1 000 libros: sin picos > 100 MB ni descriptores abiertos |
| T3.3 | `LruCache` de páginas PDF con tamaño por memoria disponible y `onRemoved` diferido | P-05 | 1 d | PDF de 500 páginas en scroll continuo sin OOM en 4 GB |
| T3.4 | Decisión de paginación con datos: medir 1k/10k; si se adopta, `room3-paging` + `paging-compose` con contadores agregados | P-06 | 3 d (opc.) | 10 000 libros: memoria pico < 150 MB y scroll estable |
| T3.5 | Memoizar índice y callbacks del lector; dividir `ReaderChrome` (superior / inferior / hojas) | A-30 | 1,5 d | Pasar página no reconstruye el índice; recomposiciones medidas con Layout Inspector |
| T3.6 | `locateProgression` en IO, cancelando el job anterior, con indicador | A-31 | 0,5 d | Arrastrar el slider en libro grande: responde < 300 ms, sin ANR |
| T3.7 | Evitar que el TTS deshaga saltos manuales | A-32 | 0,5 d | Saltar mientras se narra: la vista se queda y el TTS se re-ancla |
| T3.8 | **A-35** orden con `Collator` español | A-35 | 0,25 d | «Ángel» < «Buenos Aires» < «Ñandú» < «Zorro» |
| T3.9 | Escaneo de carpetas en ViewModel (sobrevive a rotación) y reanudable | A-36 | 1 d | Rotar a mitad de escaneo: continúa y muestra progreso |
| T3.10 | `LazyVerticalGrid` con clave por libro | A-40 | 1 d | Cuadrícula conserva posición al alternar vista y al marcar favorito |
| T3.11 | Baseline Profiles + `StartupTimingMetric` con Macrobenchmark | — | 1,5 d | Arranque en frío ≤ 1,2 s en gama media |
| T3.12 | **P-07** assets: logo a WebP ≤ 200 KB, densidades correctas, eliminar duplicados entre módulos | P-07, P-08 | 0,5 d | APK reduce ≥ 2 MB; sin cambios visuales |

**Criterio de salida F3:** `docs/METRICAS.md` actualizado con antes/después en 3 escenarios (1k libros, 10k libros, PDF 500 páginas) y umbrales cumplidos: jank < 1 %, arranque ≤ 1,2 s, memoria de lectura < 250 MB.

---

## 8. Fase 4 — i18n, accesibilidad y pulido (semana 8 · 9 días)

**Objetivo:** que la app se sienta terminada y que cualquier persona pueda usarla, en inglés o español, con TalkBack y fuente al 200 %.

| ID | Tarea | Hallazgo | Esf. | Criterio de aceptación |
|---|---|---|---|---|
| T4.1 | Extraer los **151 literales** a `strings.xml` + `values-en` (122 en `app`, 29 en `feature:library`), con `plurals` para contadores | A-20 | 3 d | `grep` de literales en UI = 0; lint bloqueante; revisión de ambos idiomas |
| T4.2 | Accesibilidad: objetivos táctiles ≥ 48 dp, `contentDescription` correctos (acción real, no estado), `liveRegion` en estados, `selectableGroup` en chips, `progressSemantics` en barras | A-19 | 2 d | TalkBack recorre biblioteca, lector, TTS y PDF sin callejones; axe/Android a11y scanner sin errores |
| T4.3 | Confirmación o «deshacer» en borrados: nota, resaltado, colección, copia offline | A-33, A-39 | 1 d | Ningún borrado irreversible en un toque |
| T4.4 | Límite visible de cita + contador en la tarjeta | A-23 | 0,5 d | Cita larga avisa antes de exportar; el PNG no recorta en silencio |
| T4.5 | Tema claro y seguimiento del sistema (`LeePeTheme`) | A-19 | 1 d | La app respeta modo claro/oscuro; contraste AA en ambos |
| T4.6 | Verificar RTL tras el cambio de iconos a `AutoMirrored` (T0.6): flechas, chevrons y listas | A-19 | 0,5 d | En RTL, volver/anterior/siguiente apuntan correctamente |
| T4.7 | Layout adaptable: TTS en `FlowRow`, insets reales (`navigationBarsPadding`/`statusBarsPadding`), teclado en tarjetas | A-19 | 1 d | Fuente 200 % y pantalla 360 dp sin recortes ni solapamientos |

**Criterio de salida F4:** build en inglés y español sin cadenas en el idioma equivocado; TalkBack completo; fuente 200 % sin recortes; contraste AA.

---

## 9. Fase 5 — Release y distribución (semanas 9-10 · 9 días)

**Objetivo:** un AAB firmado, reproducible y publicable.

| ID | Tarea | Hallazgo | Esf. | Criterio de aceptación |
|---|---|---|---|---|
| T5.1 | Build `release`: R8, reglas para Readium/Media3/Room/Compose, `signingConfig` desde variables de entorno, `versionCode`/`versionName` desde `version.properties` | A-16 | 2 d | `bundleRelease` firmado; app release pasa la matriz de humo |
| T5.2 | Mover el libro incluido a `FileProvider` (URI `content://`) con migración del registro existente | A-17 | 1 d | El EPUB de bienvenida abre y se respalda sin rutas privadas |
| T5.3 | CI de release: `bundleRelease`, artefacto, tag, `CHANGELOG` automático, publicación de esquemas y de informes | A-16 | 1 d | Push de tag genera AAB y release en GitHub |
| T5.4 | Play Console: ficha, política de privacidad, **Data safety** (sin datos recogidos), clasificación de contenido, capturas ES/EN | — | 2 d | Ficha enviada y aprobada |
| T5.5 | Matriz de aceptación en dispositivos: API 24, 26, 33, 35, 36 + tablet + TalkBack | — | 2 d | `docs/VALIDACION_ANDROID.md` completo con evidencias |
| T5.6 | Rollout: interno → cerrado (20 usuarios) → producción 10 % → 100 % con criterios de parada | — | 1 d | Crash-free ≥ 99,5 % antes de ampliar |

**Criterio de salida F5:** 1.0 en producción con rollout controlado, sin crashes bloqueantes en las primeras 72 h.

---

## 10. Fase 6 — Arquitectura y evolución (post-1.0, continuo)

Se planifica ahora, se ejecuta **después** de publicar. Cada ítem es un PR independiente y no bloquea releases.

| ID | Tarea | Hallazgo | Esf. |
|---|---|---|---|
| T6.1 | Unificar paquetes: mover `LocalEpubCover` a `feature:library`; alinear `pe.lee.app.data.local` | M-01 | 1 d |
| T6.2 | Extraer `feature:reader` y `feature:audiobook` (3 400 líneas salen de `:app`) | M-02 | 4-5 d |
| T6.3 | Contratos de dominio para `LibraryState` (dejar de exponer `BookEntity`) | M-03 | 2 d |
| T6.4 | Renombrar `library:readium` → `core:cover` o integrarlo en la feature | M-04 | 0,5 d |
| T6.5 | Búsqueda con columna normalizada indexada (sustituir el índice en memoria) | P-06 | 2 d |
| T6.6 | Evaluar Hilt solo si el grafo manual supera ~15 dependencias | — | 2 d |
| T6.7 | Sincronización opcional (Render/Neon) — fuera de 1.0 | — | ≥ 10 d |

---

## 11. Calendario resumido

| Semana | Fase | Entregable visible |
|---|---|---|
| 1 | F0 | CI reproducible, licencia, métricas de partida |
| 2-3 | F1 | Sin crashes de rotación, audio y PDF; servicios cerrados |
| 4-5 | F2 | Respaldo v2 con colecciones; sin basura en disco |
| 6-7 | F3 | 10 000 libros fluidos; arranque ≤ 1,2 s |
| 8 | F4 | App bilingüe, accesible, tema claro |
| 9-10 | F5 | AAB firmado, ficha de Play, rollout |
| 11+ | F6 | Modularización y evolución |

**Hitos de decisión:** final de F1 (¿es estable?), final de F3 (¿aguanta gama baja?), final de F5 (¿publicamos o posponemos una semana?).

---

## 12. Trazabilidad completa

Cada hallazgo de la auditoría, con su fase y tarea. Ninguno queda sin dueño.

| Hallazgo | Fase | Tarea | Hallazgo | Fase | Tarea |
|---|---|---|---|---|---|
| A-01 servicios exportados | 1 | T1.1 | A-21 OOM tarjetas | 1 | T1.13 |
| A-02 progreso→0 % | 1 | T1.16 | A-22 captura frame clock | 1 | T1.14 |
| A-03 respaldo colecciones | 2 | T2.1 | A-23 cita truncada | 4 | T4.4 |
| A-04 rotación | 1 | T1.9-T1.11 | A-24 timeout tarjeta | 1 | T1.15 |
| A-05 bitmap reciclado | 1 | T1.7 | A-25 Publication sin cerrar | 1 | T1.5 |
| A-06 MediaController | 1 | T1.2 | A-26 TTS huérfano | 1 | T1.6 |
| A-07 HttpClient | 2 | T2.2 | A-27 doble bind TTS | 1 | T1.4 |
| A-08 caché PDF | 2 | T2.3 | A-28 tiempo de lectura falso | 2 | T2.7 |
| A-09 StatFs por bloque | 2 | T2.4 | A-29 BackHandler | 1 | T1.12 |
| A-10 escritura por locator | 2 | T2.5 | A-30 recomposición lector | 3 | T3.5 |
| A-11 respaldo O(n) | 2 | T2.6 | A-31 locateProgression | 3 | T3.6 |
| A-12 (retirado: el aviso ya existía) | 2 | T2.10 | A-32 TTS vs saltos | 3 | T3.7 |
| A-13 (retirado: falso positivo) | — | — | A-33 borrar notas | 4 | T4.3 |
| A-14 startService | 1 | T1.3 | A-34 locator inválido | 1 | T1.8 |
| A-15 licencia | 0 | T0.5 | A-35 orden acentos | 3 | T3.8 |
| A-16 release/firma | 5 | T5.1 | A-36 escaneo cancelado | 3 | T3.9 |
| A-17 file:// | 5 | T5.2 | A-37 día congelado | 2 | T2.8 |
| A-18 wakelock | 1 | T1.3 | A-38 outbox | 2 | T2.9 |
| A-19 accesibilidad | 4 | T4.2, T4.5-T4.7 | A-39 borrados sin confirmar | 4 | T4.3 |
| A-20 i18n | 4 | T4.1 | A-40 grid sin claves | 3 | T3.10 |
| P-01 flows recreados | 3 | T3.1 | P-05 bitmaps sin límite | 3 | T3.3 |
| P-02 collectAsState | 3 | T3.1 | P-06 biblioteca en RAM | 3 | T3.4 |
| P-03 portadas en serie | 3 | T3.2 | P-07 assets | 3 | T3.12 |
| P-04 miniaturas sin límite | 3 | T3.2 | P-08 strings duplicados | 4 | T4.1 |
| M-01 paquetes divididos | 6 | T6.1 | M-04 módulo engañoso | 6 | T6.4 |
| M-02 app dios | 6 | T6.2 | M-05 código muerto | 0 | T0.6 |
| M-03 fuga de Room | 6 | T6.3 | | | |

---

## 13. Métricas de éxito (KPIs)

| KPI | Cómo se mide | Partida | Meta 1.0 |
|---|---|---|---|
| Crash-free sessions | Play Console / Firebase Crashlytics (si se acepta dependencia) | n/d | ≥ 99,5 % |
| Arranque en frío | Macrobenchmark `StartupTimingMetric` | medir en T0.7 | ≤ 1,2 s gama media |
| Jank | Macrobenchmark `FrameTimingMetric` | medir en T0.7 | < 1 % frames > 16 ms |
| Memoria pico al leer | `dumpsys meminfo` en EPUB/PDF grandes | medir en T0.7 | < 250 MB |
| Biblioteca 10 000 libros | Prueba automatizada de generación + scroll | falla hoy | sin OOM, scroll estable |
| Cobertura de lógica | Kover (sin contar UI) | ~15 % | ≥ 60 % en dominio/datos |
| Literales hardcodeados | lint `HardcodedText` | 151 | 0 |
| Tamaño | APK debug medido; `bundletool` para AAB | **32,1 MB** (debug, sin R8) | AAB release ≤ 20 MB |
| Pruebas unitarias en CI | Informe de Gradle | 35 (faltan 3 de `:core:domain`) | 38 + las nuevas de cada fix |
| `lint` | `lintRelease` | baseline | 0 errores, 0 warnings nuevos |

---

## 14. Riesgos del plan y mitigaciones

| Riesgo | Prob. | Impacto | Mitigación |
|---|---|---|---|
| R1 El build no es reproducible en otra máquina (versiones futuras del catálogo) | Media | Alto | `verification-metadata.xml`, plugin Management con versiones fijas, `docs/BUILD.md`, CI como fuente de verdad |
| R2 R8 rompe Readium/Media3 | Alta | Alto | Reglas `-keep` específicas + tests instrumentados sobre build `release` antes de publicar |
| R3 La extracción de strings introduce regresiones visuales | Media | Medio | PR por pantalla, capturas antes/después, revisión en ambos idiomas |
| R4 Respaldo v2 rompe archivos v1 de usuarios existentes | Baja | Alto | Tests de ida y vuelta con archivos v1 reales; mantener lector v1 durante 2 versiones |
| R5 El refactor de módulos consume el tiempo de la 1.0 | Alta | Medio | Fase 6 explícitamente post-1.0; no se aceptan PRs de arquitectura antes |
| R6 Rechazo en Play por permisos o Data safety | Baja | Alto | Revisión de la ficha en T5.4 antes del envío; sin `INTERNET` facilita la declaración |
| R7 Gama baja sigue fallando pese a las correcciones | Media | Alto | Dispositivo físico de 4 GB en la matriz desde F1, no solo al final |
| R8 Un solo desarrollador (autobús) | Media | Alto | Documentación en `docs/`, PRs revisados, CI estricto, decisiones en ADR |

---

## 15. Gobernanza y convenciones

- **Ramas:** `main` protegida; `feat/<tema>`, `fix/<tema>`, `chore/<tema>`. Sin push directo.
- **Commits:** convencionales (`fix(reader): …`, `feat(sharecard): …`, `perf(library): …`), con el ID del hallazgo en el cuerpo (`Refs: A-05`).
- **PR:** una tarea, ≤ 400 líneas, descripción con hallazgo + criterio de aceptación + evidencia (captura, log, cifra).
- **Revisión:** checklist de 6 puntos (tests, migraciones, i18n, a11y, rendimiento, seguridad).
- **Releases:** cada 2 semanas en track interno; tag `v0.x.y`; `CHANGELOG.md` generado.
- **Board:** un issue por tarea `T<fase>.<n>`, milestone por fase, label por tipo. El plan es el backlog; la auditoría es la fuente.

---

## 16. Rutas alternativas

### Ruta exprés — 2 semanas (demo estable, no publicable)
1. F0 mínimo (T0.1, T0.2).
2. Quick wins: **A-02**, **A-35**, **A-18**, **A-05**, **A-24** (5 días).
3. T1.1 seguridad de servicios + T1.9 rotación de biblioteca (3 días).
**Resultado:** demo sin crashes evidentes y sin exposición de servicios, pero sin release ni i18n.

### Ruta recomendada — 10-12 semanas (1 dev)
El plan completo de las fases 0 a 5.

### Ruta 2 devs — 6-7 semanas
- Dev A: F1 (estabilidad/seguridad) → F2 (datos) → F5 (release).
- Dev B: F3 (rendimiento) → F4 (i18n/a11y).
- Punto de sincronización semanal; conflictos esperados en `ReaderActivity` y `LibraryScreen` (repartir por archivo, no por tema).

---

## 17. Arranque inmediato: próximas 48 horas

1. **T0.1** — dejar `docs/BUILD.md` con el build ya verificado (JDK 21 + SDK 36, 14 min en frío) y el *workaround* del sandbox, para que nadie pierda un día en reproducirlo.
2. **A-02** — el parche de una línea que evita perder el progreso de lectura (`TtsPlaybackService.kt:81`), con test.
3. **A-35** — `Collator` en el orden alfabético, con test de «Ángel/Ñandú/Zorro».
4. **A-18** — `WakeLock` con timeout en el servicio de narración.
5. Abrir los 52 issues del backlog (§12) con labels y milestones, y crear la plantilla de PR.

Al final de esas 48 horas debe haber: build reproducible, dos bugs corregidos con test, y el backlog completo creado. Eso convierte este documento en trabajo real.
