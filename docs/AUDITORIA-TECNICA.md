# Auditoría técnica — Lee.pe (Android)

**Repositorio:** [cponce123com-create/Lee.pe](https://github.com/cponce123com-create/Lee.pe) · rama `main` · commit `f050bec`
**Fecha de la auditoría:** 3 de noviembre de 2026
**Alcance:** 153 archivos versionados · 88 archivos Kotlin de producción (10.614 líneas) · 10 módulos Gradle · 1 APK de depuración (CI)
**Naturaleza:** revisión estática de código, configuración de build, datos, seguridad, CI y documentación, más una **compilación completa verificada** (`testDebugUnitTest assembleDebug`, 35 pruebas verdes). No se ejecutó la app en dispositivo o emulador (ver §2).

> **Actualización del estado (4 de noviembre de 2026):** el informe detallado que sigue es una captura histórica del commit `f050bec` del 3 de noviembre; sus descripciones de `versionCode`, `buildTypes`, CI y recuentos no describen `main` actual. `main` ya usa `versionCode=3`, contiene `buildTypes.release` con R8/minificación y firma opcional por variables de entorno, y la línea base más reciente registrada es 64 pruebas unitarias + 12 instrumentadas. El PR #38 corrigió además el cierre al iniciar y pasó lint, pruebas unitarias, `assembleDebug` y una prueba instrumentada de arranque en API 35. Los pendientes de dispositivo físico y la revalidación de release se mantienen explícitos.

---

## 1. Resumen ejecutivo

Lee.pe es una app Android nativa (Kotlin + Compose + Readium + Media3 + Room) para leer y escuchar EPUB, PDF y audiolibros locales, con una arquitectura **offline-first** coherente: no pide permiso de `INTERNET`, usa exclusivamente SAF para acceder a archivos, desactiva el backup automático de Android y guarda todo en almacenamiento privado. Esa parte del diseño está bien resuelta y es el principal activo del proyecto.

En la captura del 3 de noviembre, la base compilaba con **38 pruebas unitarias**. La línea base posterior registrada en `main` es **64 pruebas unitarias y 12 instrumentadas**; el CI reciente confirmó el APK de depuración y el arranque en emulador API 35. Las cifras de la captura no deben usarse como estado actual.

El código, sin embargo, está en un estado **de prototipo avanzado, no de producto publicable**. Los problemas más serios no son de funcionalidad sino de **acabado de ingeniería**: dos estrategias de internacionalización conviviendo (la pantalla principal traduce; el lector no traduce nada), estado de UI que no sobrevive a la rotación, ciclo de vida de `MediaController`/bitmaps con riesgo de cierre inesperado, un respaldo que pierde datos organizativos, y una cadena de publicación (release, firma, R8, versión) que no existe.

### Veredicto por área

| Área | Estado | Comentario |
|---|---|---|
| Privacidad / permisos | **Bueno** | Sin `INTERNET`, SAF, `allowBackup=false`; solo falta endurecer servicios exportados |
| Modelo de datos | **Bueno con deuda** | Migraciones 1→5 escritas a mano y probadas; respaldo pierde colecciones múltiples |
| Lector EPUB/PDF | **Aceptable con riesgos** | Funciona por diseño, pero rotación, bitmaps y caché tienen fugas y riesgos de crash |
| Audio / TTS | **Frágil** | Servicios exportados sin permiso, ciclo de vida del controlador, progreso que puede resetearse a 0 % |
| Rendimiento | **Mejorable** | Suscripciones Room recreadas en cada recomposición, `StatFs` por bloque, cachés sin purga |
| i18n / accesibilidad | **Deficiente** | 151 literales en español fuera de recursos; `values-en` no cubre el lector |
| Pruebas | **Parcial** | La línea base más reciente es 64 pruebas unitarias + 12 instrumentadas; faltan pruebas específicas de SAF, reproductor y varios flujos del lector |
| CI / release | **Parcial** | Existe `release` con R8 y firma opcional; el release minificado aún necesita validación reproducible y no se declara firmado sin secretos |
| Documentación | **Buena pero desincronizada** | `docs/` es honesta y detallada, pero contradice al código en varios puntos |

### Los cinco riesgos que atendería primero

1. **Servicios exportados sin permiso** (`TtsPlaybackService` y `AudioPlaybackService`): cualquier app instalada puede enlazarse, controlar la narración y observar metadatos del libro en curso. *(A-01)*
2. **Progreso de lectura que puede volver a 0 %** mientras se escucha el libro, por `totalProgression ?: 0f` en el servicio TTS. *(A-02)*
3. **Pérdida de organización al restaurar un respaldo**: solo se exporta una colección por libro y se descarta la tabla de colecciones múltiples. *(A-03)*
4. **Estado de UI que no sobrevive a la rotación** en biblioteca y lectores: se pierde la búsqueda, el filtro, el orden, los diálogos y el EPUB se vuelve a abrir desde cero. *(A-04)*
5. **Reciclado de `Bitmap` en pleno uso** en el visor PDF y en el scroll continuo: patrón clásico de `Canvas: trying to use a recycled bitmap`. *(A-05)*

Y un sexto que conviene no perder de vista: **la generación de tarjetas para compartir copia el frame 4-5 veces y no captura `OutOfMemoryError`**, de modo que el crash por memoria no se muestra como error sino como cierre de la app. *(A-21)*

En segundo plano, dos fugas del lector explican buena parte del desgaste: la `Publication` de Readium **nunca se cierra** *(A-25)* y la narración **sigue sonando sin controles** tras rotar o salir del libro *(A-26)*.

---

## 2. Alcance, método y limitaciones

**Qué se revisó**

- Todo el código Kotlin de producción (88 archivos), recursos XML, manifiestos, scripts Gradle, catálogo de versiones, workflow de CI y los 9 documentos de `docs/`.
- Contraste de cada afirmación del `README.md` y de `docs/` contra el código.
- Verificación de que **todas** las coordenadas de dependencia del catálogo existen realmente en Google Maven / Maven Central (AGP 9.3.1, Kotlin 2.4.20, Compose BOM 2025.12.00, `androidx.room3` 3.0.3, KSP 2.3.10, Media3 1.11.1, Readium 3.2.0, entre otras).
- Comprobación del esquema Room exportado (`5.json`) contra las entidades y migraciones.
- Cruce de los 162 identificadores de `R.string` usados contra los definidos (0 rotos, 0 huérfanos) y comparación ES/EN.

**Qué NO se pudo verificar (y por qué)**

| Verificación | Estado | Motivo |
|---|---|---|
| `./gradlew test assembleDebug` | **Verificado** | Instalados JDK 21 + SDK 36 en un directorio de trabajo; `./gradlew --no-daemon testDebugUnitTest assembleDebug` terminó **BUILD SUCCESSFUL en 14 m 9 s** (311 tareas): **35 pruebas unitarias, 0 fallos** y `app-debug.apk` de **32,1 MB**. Como ese comando no toca el módulo JVM, se ejecutó aparte `:core:domain:test` (**3 pruebas más, verdes**): total **38, 0 fallos**. |
| Ejecución en dispositivo / emulador | **No ejecutado** | Sin dispositivo conectado ni emulador. |
| `lint` real (Android Lint) | **No ejecutado** | Requiere toolchain Android; se hizo una revisión manual equivalente sobre manifiestos, recursos y APIs. |
| Rendimiento medido (FPS, memoria pico) | **No medido** | Se documentan riesgos por inspección de código, sin cifras inventadas. |

Por tanto, los hallazgos marcados como *riesgo* (por ejemplo, A-04, A-05, A-06) están justificados por el código y por patrones conocidos de Android/Compose, pero su reproducción en dispositivo queda pendiente de confirmación. Los hallazgos marcados como *defecto* son deterministas por lectura del código.

---

## 3. Ficha del repositorio

| Dato | Valor |
|---|---|
| Creado | 2026-11-02 · 100 commits en ~19 h · un solo autor (`sataco`) |
| Licencia | **Ninguna** (`license: null`) — ver A-15 |
| Releases / tags | Ninguno |
| Módulos | `app`, `core:{database,datastore,domain,ui}`, `data`, `feature:{library,notes,sharecard}`, `library:readium` |
| Stack | AGP 9.3.1 · Gradle 9.5.0 · Kotlin 2.4.20 · Compose BOM 2025.12.00 · Room 3.0.3 (`androidx.room3`) · KSP 2.3.10 · Readium 3.2.0 · Media3 1.11.1 |
| SDK | `minSdk 24` · `targetSdk 36` · `compileSdk 36` · Java 17 con desugaring |
| Versión de app (captura histórica) | `versionCode 1` · `versionName 0.1.0`; el `main` actual usa `versionCode 3` · `versionName 0.3.0` |
| Permisos | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `POST_NOTIFICATIONS` — **sin `INTERNET`** |
| Pruebas | 12 archivos (9 unitarios + 3 instrumentados): **38 unitarias ejecutadas, 0 fallos** (35 en `testDebugUnitTest` + 3 en `:core:domain:test`) + 9 instrumentadas |
| Build | `testDebugUnitTest assembleDebug` **verde** · 14 m 9 s · 311 tareas · 44 advertencias (25 de API experimental de Readium, 16 deprecaciones de Compose, 3 `Elvis` muertos) |
| APK de depuración | **32,1 MB / 30,6 MiB** (medido; 19 dex, sin R8) |
| CI | GitHub Actions: lint + tests unitarios + `assembleDebug` + emulador API 35 |

**Distribución del código de producción**

| Módulo | Archivos | Líneas |
|---|---|---|
| `app` | 29 | 4.883 |
| `feature/library` | 19 | 3.476 |
| `feature/sharecard` | 16 | 772 |
| `core/database` | 10 | 764 |
| `data` | 6 | 315 |
| `library/readium` | 1 | 127 |
| `feature/notes` | 1 | 92 |
| `core/domain` | 4 | 75 |
| `core/datastore` | 1 | 71 |
| `core/ui` | 1 | 39 |

El 46 % del código (y casi todo el motor de lectura) vive todavía en `:app`; es la deuda que el propio `docs/ARQUITECTURA_MODULAR.md` reconoce.

---

## 4. Arquitectura y modularidad

### Lo que está bien

- **Separación real de contratos**: `core:domain` es Kotlin/JVM puro (sin Android, Room ni Compose) y define `ReaderBookRepository`, `ReaderAnnotationRepository`, `ReadingStatisticsRepository`. Los ViewModels de lectura consumen esos contratos, no DAOs.
- **Reglas de dependencia explícitas y documentadas** en `docs/ARQUITECTURA_MODULAR.md:33-49`, con una lista honesta de extracciones pendientes (`:68-80`).
- **Catálogo de versiones único** (`gradle/libs.versions.toml`), sin versiones dinámicas ni `+`.
- **Inyección manual explícita** (`AppContainer`, `AppViewModelFactory`), sin frameworks que oculten el grafo.

### Deuda estructural

| ID | Hallazgo | Evidencia |
|---|---|---|
| M-01 | **Paquetes divididos entre módulos**: `pe.lee.app.ui.library` existe en `feature:library` y en `library:readium`; `pe.lee.app.data.local` existe en `core:database` y en `data`. Compila, pero oculta violaciones de dependencia y complica el refactor y el `R`/KSP. | `library/readium/src/main/java/pe/lee/app/ui/library/LocalEpubCover.kt:1` vs `feature/library/.../ui/library/*` |
| M-02 | **`:app` es un módulo-dios**: lectores EPUB/PDF, servicios de audio y TTS, tema y navegación conviven con el ensamblado. `feature:reader` sigue sin existir. | `app/src/main/java/pe/lee/app/ui/reader/*` (18 archivos, 3.400 líneas) |
| M-03 | **Fuga de tipos de persistencia**: `feature:library` y `data` exponen `api(project(":core:database"))`, de modo que cualquier consumidor ve entidades y DAOs Room; `LibraryState` transporta `BookEntity` directamente. | `feature/library/build.gradle.kts:26-31`, `data/build.gradle.kts:17`, `LibraryViewModel.kt:15-18` |
| M-04 | **Nombre de módulo engañoso**: `library:readium` solo contiene el extractor de portadas EPUB; el motor Readium vive en `:app`. | `docs/ARQUITECTURA_MODULAR.md:30-31` (reconocido) |
| M-05 | **Código muerto y restos de refactor**: imports de Compose en archivos sin UI, constantes de preferencias sin uso, 3 `Elvis` muertos en mensajes de error y 44 advertencias de compilación. | `LibraryFileOperations.kt:6-10`; `ReaderActivityHost.kt:96,107,125-127`; `EpubTtsSessionActions.kt:125`; `ReaderActivity.kt:3-6` |

---

## 5. Seguridad y privacidad

### Fortalezas verificadas

- **Sin `INTERNET`**: el manifiesto solo declara `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` y `POST_NOTIFICATIONS` (`app/src/main/AndroidManifest.xml:8-11`). La afirmación del README se cumple.
- **Sin acceso general al almacenamiento**: no hay `MANAGE_EXTERNAL_STORAGE` ni `READ_EXTERNAL_STORAGE`; todo pasa por SAF con `takePersistableUriPermission` y se exige el permiso antes de insertar (`LibraryDocumentActions.kt:143-151`).
- **Backup de Android desactivado**: `allowBackup="false"` + `fullBackupContent="false"` (`AndroidManifest.xml:14-15`), coherente con la promesa de no exfiltrar la biblioteca.
- **`FileProvider` mínimo**: autoridad `${applicationId}.fileprovider`, `exported="false"`, y un único `cache-path` restringido a `quotecards/` (`feature/sharecard/src/main/AndroidManifest.xml:8-19`, `res/xml/file_paths.xml`).
- **`PendingIntent` inmutables** (`AudioPlaybackService.kt:64`, `TtsPlaybackService.kt:63`).
- **Copia offline en `noBackupFilesDir`** y con nombre derivado de SHA-256, no del nombre original (`OfflineBookStore.kt:146-152`).

### Hallazgos

**A-01 · Alto · Servicios exportados sin permiso (control de reproducción y fuga de metadatos)**

`AudioPlaybackService` y `TtsPlaybackService` se declaran `android:exported="true"` con el filtro `androidx.media3.session.MediaSessionService` y **sin `android:permission`** (`AndroidManifest.xml:21-36`). Además, `TtsPlaybackService.onBind()` devuelve el binder propio a cualquier cliente que envíe la acción pública `"pe.lee.app.ui.audio.TtsPlaybackService"` (`TtsPlaybackService.kt:160-167,185-193`), y `onGetSession()` entrega la sesión activa sin comprobar el controlador (`:169-170`).

*Consecuencia:* una app de terceros puede enumerar y controlar la reproducción, pausar/reanudar, fijar el temporizador y recibir los metadatos de la sesión (título, autor y, en TTS, la locución en curso). No expone los archivos, pero sí actividad de lectura y contenido narrado.

*Corrección:* no hace falta cerrar el servicio (los controles del sistema y Android Auto dependen de que sea visible): basta con **limitar quién puede conectarse**. Implementar `MediaSession.Callback.onConnect()` y devolver `ConnectionResult.rejected` para paquetes no confiables, devolver `null` en `onGetSession()` cuando el `controllerInfo.packageName` no sea el propio o el sistema, y sacar el binder interno de TTS a un servicio aparte no exportado (o detrás de un permiso de firma propio) en lugar de reutilizar la acción pública `SERVICE_INTERFACE`.

**A-02 · Alto · El progreso EPUB puede retroceder a 0 % durante la narración**

```kotlin
// TtsPlaybackService.kt:81-88
val progression = locator.locations.totalProgression?.toFloat() ?: 0f
container.books.saveEpubProgress(uri = bookUri, locator = ..., progress = progression, at = now)
```

Si el EPUB no publica `totalProgression` (habitual en libros sin `page-list` ni spine completo), cada 3 s se persiste `0f` y se pisa el avance real del usuario, que además se escribe en el `locator` con la misma operación.

*Corrección:* `progress = locator.locations.totalProgression?.toFloat() ?: book.readProgress` (leer el valor previo) o no escribir el campo `readProgress` cuando sea nulo, y guardar solo el locator.

**A-03 · Alto · El respaldo pierde la organización en colecciones**

`LocalLibraryBackup.save()` exporta `collectionId` como **cadena única por libro** (`LocalLibraryBackup.kt:63`) y no exporta la tabla `book_collections`. Al restaurar, `updateRestoredOrganization()` borra los vínculos y asigna **como máximo una** colección (`LocalBackupDao.kt:80-100`). Un libro que pertenece a «Novela» y «Perú» vuelve con una sola etiqueta, y las colecciones vacías desaparecen.

*Corrección:* exportar `collections: [{name, sortOrder}]` + `bookCollections: [{bookUri, collectionName}]` con `version = 2` y un lector que acepte v1 y v2; restaurar por nombre normalizado.

**A-04 · Alto · El estado de UI no sobrevive a la rotación; el EPUB se reabre entero**

- `LibraryScreen` guarda búsqueda, filtro, orden, colección seleccionada, diálogos y borradores en `remember { mutableStateOf(...) }` (`LibraryScreen.kt:140-226`): **todo se pierde al girar**. En todo el proyecto **no existe ni un solo `rememberSaveable` ni `SavedStateHandle`** (verificado por búsqueda global).
- `ReaderActivity.onCreate(null)` descarta deliberadamente el estado guardado (`ReaderActivityHost.kt:38`), de modo que cada rotación vuelve a ejecutar `openPublication()` → reparsea el EPUB completo (y recrea el `DefaultHttpClient`, ver A-07).
- El visor PDF guarda página y zoom en `remember` (`PdfReaderActivity.kt:82-96`), así que la rotación reabre el documento y vuelve a renderizar.

*Consecuencia:* jank perceptible en libros grandes, pérdida de la búsqueda escrita y de los diálogos abiertos. Es el defecto de UX más reportable por un usuario real.

*Corrección:* `rememberSaveable` para lo que sea serializable (query, filtro, orden, página, zoom, `chromeVisible`), y conservar el `Publication`/`PdfRenderer` en un `ViewModel` con `onCleared()` en lugar de reiniciar en cada `onCreate`.

---

## 6. Datos y persistencia

### Lo que está bien

- Room 5 con **migraciones explícitas 1→2→3→4→5** (`LeeDatabase.kt:30-181`), esquema exportado (`core/database/schemas/.../4.json`, `5.json`) y una prueba instrumentada que migra **desde cada versión soportada** y verifica biblioteca, notas y estadísticas (`feature/library/src/androidTest/.../LeeDatabaseMigrationTest.kt:26-58`).
- **Outbox de borrado**: `deleteByUri()` inserta en `pending_book_cleanup` dentro de la misma transacción Room y la limpieza de archivos se completa después, de forma reintentable (`BookDao.kt:189-197`, `LibraryScreen.kt:228-243`).
- **Respaldo validado antes de escribirse**: `parse(bytes)` se ejecuta sobre el archivo recién generado para no producir un respaldo que el propio importador rechace (`LocalLibraryBackup.kt:94-95`), con límites (16 MB, 20 000 libros, 60 000 notas, 100 000 sesiones) y validación de tipos y rangos (`:191-314`).
- **El emparejamiento de respaldos no adivina**: URI exacta primero, y título+formato solo si es único (`LocalBackupDao.kt:171-183`), con pruebas que cubren el caso ambiguo.
- Estadísticas de lectura que **parten el tiempo por día local** y sobreviven a cambios de día (`ReadingSessionRecorder.kt:97-129`).

### Hallazgos

**A-05 · Alto · Reciclado de `Bitmap` que Compose puede seguir dibujando (riesgo de crash)**

```kotlin
// PdfReaderActivity.kt:132-134
DisposableEffect(bitmap) { onDispose { bitmap?.takeIf { !it.isRecycled }?.recycle() } }
```

```kotlin
// PdfRendering.kt:187-189 (scroll continuo, una por página)
DisposableEffect(bitmap) { onDispose { bitmap?.takeIf { !it.isRecycled }?.recycle() } }
```

Cuando `bitmap` cambia, el efecto anterior se descarta y **recicla** el bitmap que la capa gráfica pudo haber registrado en el frame en curso. En el scroll continuo esto ocurre muchas veces por segundo, con varias páginas vivas a la vez.

*Consecuencia:* `RuntimeException: Canvas: trying to use a recycled bitmap` o páginas en blanco intermitentes en dispositivos con renderizado por software. Es un patrón desaconsejado por la propia documentación de Compose.

*Corrección:* no reciclar manualmente; dejar que el GC libere (`Image(bitmap.asImageBitmap())` mantiene la referencia) y, si se quiere acotar memoria, usar un `LruCache` de páginas con `onRemoved` diferido al siguiente frame.

**A-06 · Medio · `MediaController` liberado pero retenido por el ViewModel**

`AudioPlayerActivity.onDestroy()` libera el futuro del controlador (`AudioPlayerActivity.kt:126-131`), pero el `AudioPlayerViewModel` sobrevive a la rotación y conserva `controller` (`AudioPlayerViewModel.kt:27-36`), con un bucle de 500 ms que sigue llamando `player.currentPosition` (`:59-64`). En la reconexión, `attach()` intenta `removeListener` sobre el controlador ya liberado (`:34`).

*Consecuencia:* riesgo de `IllegalStateException` al girar durante la reproducción, y sondeo de posición en segundo plano aunque la pantalla esté cerrada.

*Corrección:* exponer `detach()` y llamarlo en `onDestroy()`, comprobar `controller.isConnected` antes de tocar el reproductor, y mover el sondeo a un `StateFlow` producido por el servicio o a `repeatOnLifecycle(STARTED)`.

**A-07 · Medio · `DefaultHttpClient` de Readium creado por apertura y nunca cerrado**

`ReaderActivityHost.kt:93` y `LocalEpubCover.kt:57` instancian `DefaultHttpClient()` para abrir cada publicación/portada. Aunque la app no hace red, Readium crea el cliente con su pool de ejecutores y no se cierra al terminar la publicación.

*Consecuencia:* hilos y recursos retenidos por libro abierto o portada extraída; más visible al recorrer una biblioteca grande.

*Corrección:* un único cliente por proceso/`Application`, reutilizado y cerrado con el `AppContainer`, o `HttpClient` nulo para recursos puramente locales.

**A-08 · Medio · Caché de PDFs copiados sin purga**

`openPdf()` copia el PDF a `cacheDir` cuando el proveedor no permite `PdfRenderer` directo (`PdfRendering.kt:239-269`) y lo borra al cerrar el `PdfHandle`; si el proceso muere (o el sistema mata la actividad) el archivo queda. No hay barrido de `lee-pe-*.pdf` al arrancar.

*Consecuencia:* crecimiento silencioso del caché con PDFs de decenas o cientos de MB.

*Corrección:* limpiar `cacheDir` de archivos `lee-pe-*.pdf` con antigüedad > 24 h en el arranque de `LeePeApplication` y borrar también en `onTrimMemory`.

**A-09 · Medio · Copia offline con `StatFs` por bloque de 8 KB**

```kotlin
// OfflineBookStore.kt:83-89
val count = source.read(buffer)
if (StatFs(directory.absolutePath).availableBytes < count.toLong()) { ... }
```

Un audiolibro de 500 MB implica ~64 000 consultas `statfs`, cada una con acceso a sistema de archivos, dentro del bucle de copia.

*Corrección:* comprobar el espacio una vez antes de empezar y cada ~8-16 MB, o llevar un contador acumulado; además, validar el espacio contra el tamaño esperado cuando `OpenableColumns.SIZE` esté disponible.

**A-10 · Medio · Escritura en Room por cada emisión de locator**

`LaunchedEffect(navigator)` guarda locator + progreso en Room en **cada** emisión de `currentLocator` (`EpubReaderSessionEffects.kt:301-317`). En modo desplazamiento eso puede ser varias veces por segundo.

*Corrección:* `debounce(2_000)` + `conflate()`, y guardar de forma inmediata solo en `onPause`/salida del lector.

**A-11 · Medio · Respaldo con coste O(n) en preferencias**

`LocalLibraryBackup.save()` recorre todos los libros y por cada uno consulta `SettingsRepository.contains` y `AudioProgressStore.get` (`LocalLibraryBackup.kt:64-75`), lo que reabre `SharedPreferences` y realiza una lectura por libro.

*Corrección:* una sola lectura de `all(LIBRARY)` (ya se hace) y resolver las claves con el mapa en memoria; para audio, exportar el mapa completo de `AUDIO_PROGRESS` de una vez.

> **A-12 retirado (parcialmente).** La revisión estática afirmó que la app no avisaba del respaldo sin cifrar. Al ir a implementar el aviso, el diálogo de respaldo ya lo mostraba: `backup_privacy_notice` («El respaldo JSON contiene notas y datos de lectura sin cifrar. Guárdalo en un lugar privado. Nunca se envía a Lee.pe.») se muestra antes de los botones Exportar/Restaurar (`LibraryProcessDialogs.kt:155-158`). El riesgo del formato (JSON en claro) sigue siendo real y el cifrado opcional continúa como mejora de producto, pero **el hallazgo de UX se retira**: no faltaba aviso.

---

## 7. Corrección funcional del lector y del audio

> **A-13 retirado.** La revisión estática concluyó que `AndroidTtsNavigatorFactory(...)` no era nullable y que su comprobación era código muerto. Al compilar el código y eliminar esa rama, el compilador demostró lo contrario (`Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver`): **la comprobación es necesaria y el mensaje sí es alcanzable**. Se restauró el código original y el hallazgo se retira. Queda como ejemplo de por qué la fase 0 del plan exige compilar antes de tocar nada.

**A-14 · Medio · `startService` en un servicio de primer plano**

`TtsPlaybackService.start()` usa `application.startService(...)` (`TtsPlaybackService.kt:191-193`). En Android 8+, arrancar un servicio en segundo plano sin `startForegroundService` lanza `IllegalStateException` si la llamada ocurre con la app no visible (por ejemplo, al reconectar desde una notificación en frío).

*Corrección:* `ContextCompat.startForegroundService` y `ServiceCompat.startForeground(...)` inmediato en `onStartCommand`, o eliminar el `startService` y confiar solo en `bindService(BIND_AUTO_CREATE)`.

**A-15 · Medio · Sin licencia en el repositorio**

`GET /repos/...` devuelve `license: null` y no existe archivo `LICENSE`. El README menciona BSD-3-Clause **solo para Readium** (`README.md:52-54`). Sin licencia, el código no es legalmente reutilizable ni redistribuible por terceros, y no se puede publicar en la mayoría de tiendas con certeza jurídica.

*Corrección:* añadir `LICENSE` (propietaria o la que se decida) y un `NOTICE` con las atribuciones de terceros (Readium BSD-3, Media3 Apache-2.0, Compose Apache-2.0).

**A-16 · Medio · No existe configuración de release ni firma**

En el commit auditado (`f050bec`), `app/build.gradle.kts` no declaraba `buildTypes { release { ... } }` y el CI solo producía `assembleDebug`. **Esto quedó superado:** `main` ya tiene release con R8, firma opcional por variables de entorno y `version.properties`; el seguimiento del release minificado se conserva como criterio de validación pendiente.

*Consecuencia:* no hay AAB publicable, no hay ofuscación, no hay estrategia de versionado y las credenciales de firma no tienen dónde vivir.

*Corrección:* `release` con `isMinifyEnabled = true` + reglas de Readium/Media3/Room, `signingConfig` desde variables de entorno, `versionCode`/`versionName` derivados de un `version.properties`, y `bundleRelease` en CI con `--no-daemon`.

**A-17 · Medio · `file://` como clave de biblioteca**

`BundledWelcomeBook` inserta el EPUB incluido con `Uri.fromFile(bookFile).toString()` (`BundledWelcomeBook.kt:49`). Hoy funciona porque Readium acepta `file://`, pero cualquier futura acción de compartir/abrir con `Intent` producirá `FileUriExposedException`, y el respaldo exportará rutas privadas del dispositivo.

*Corrección:* exponer el libro incluido con `FileProvider` (o copiarlo dentro de `noBackupFilesDir` y usar un URI `content://` propio), manteniendo la migración para el registro existente.

**A-18 · Bajo · `WakeLock` sin tiempo de espera**

`TtsPlaybackService` adquiere un `PARTIAL_WAKE_LOCK` sin `timeout` mientras la narración está activa (`:151-157`, `:179-183`). Si el estado queda inconsistente (servicio sin sesión), la CPU puede quedar despierta.

*Corrección:* `acquire(30 * 60 * 1000)` y renovación, o `setReferenceCounted(true)` con liberación garantizada en `onDestroy`.

**A-19 · Bajo · Accesibilidad del gesto de toque y de TTS**

- Un `InputListener` global alterna los controles con cualquier toque (`EpubReaderSessionEffects.kt:211-216`); con TalkBack, el doble toque también genera `TapEvent` y el usuario puede ocultar los controles sin querer.
- `TtsControls` coloca cinco controles en una sola `Row` sin adaptación (`ReaderControls.kt:95-165`): con fuente al 200 % o pantalla de 360 dp, el contenido se recorta.
- Varios `contentDescription` son nulos o genéricos (`LocalEpubCover.kt:113-117`, `PdfRendering.kt:195`).

*Corrección:* `Modifier.semantics { }` para el contenedor del lector y excluir el gesto cuando `AccessibilityManager.isTouchExplorationEnabled`; `FlowRow` o menú para TTS; descripciones derivadas del título del libro.

### 7.1 Tarjetas para compartir (`feature:sharecard`)

Es la feature más pequeña (772 líneas) y la mejor probada en su capa de ViewModel, pero concentra el mayor riesgo de memoria de la app.

**A-21 · Alto · Riesgo de `OutOfMemoryError` al compartir (y el `catch` no lo captura)**

La captura copia el frame ARGB completo **cuatro o cinco veces**:

```kotlin
// ShareCardViewModel.kt:66-70 → toPixelMap() + IntArray manual (2 copias)
val pixels = image.toPixelMap()
val argb = IntArray(image.width * image.height) { index ->
    pixels[index % image.width, index / image.width].toArgb()
}
```

```kotlin
// ComposeShareCardGenerator.kt:20-33 → Bitmap desde IntArray, escala y compresión PNG
val source = Bitmap.createBitmap(raster.argbPixels, ...)
val output = Bitmap.createScaledBitmap(source, format.widthPx, format.heightPx, true)
```

Además el raster de origen **no es 1080×1920**, sino el tamaño del preview en pantalla (`ShareCardScreen.kt:78-84`), que después se **amplía** (pérdida de nitidez en pantallas pequeñas). Sumando origen + PixelMap + IntArray + Bitmap + escalado + buffer PNG, una tarjeta historia en un móvil de 411 dp a 3,5× ronda **40-50 MB de pico**.

Y el manejo de errores no cubre el caso:

```kotlin
// ShareCardViewModel.kt:87
} catch (_: Exception) {
```

`OutOfMemoryError` es un `Error`, no una `Exception`: si ocurre, **no se captura**, la app se cierra y el usuario ve un crash al pulsar «Compartir».

*Corrección:* leer píxeles con `ImageBitmap.readPixels` en un único `IntArray` reutilizado (sin `toPixelMap` ni bucle por píxel), renderizar directamente al tamaño destino (`format.widthPx`) en vez de escalar el preview, y tratar `OutOfMemoryError` aparte (mensaje «no hay memoria suficiente para esta tarjeta» + reciclado defensivo).

**A-22 · Medio · Captura ligada a un frame clock y a una composición que puede desaparecer**

```kotlin
// ShareCardScreen.kt:58
val frameClock = requireNotNull(rememberCoroutineScope().coroutineContext[MonotonicFrameClock])
...
// :143-145
withContext(frameClock) { withFrameNanos { }; layer.toImageBitmap() }
```

Funciona hoy porque `AndroidUiDispatcher` implementa `MonotonicFrameClock`, pero: (a) la captura se ejecuta en `viewModelScope`, que **sobrevive a la rotación**, reteniendo el `GraphicsLayer` y el clock de una composición ya destruida —`layer.toImageBitmap()` sobre un layer desprendido puede lanzar o devolver una imagen vacía—; (b) cualquier host que no aporte ese elemento al contexto (preview, tests, `ComposeView` con otro dispatching) **crashea la pantalla entera** en composición.

*Corrección:* materializar el `ImageBitmap` dentro de `rememberCoroutineScope().launch` (contexto de la UI) y pasar a la ViewModel el bitmap ya capturado; cancelar la captura si la composición se destruye.

**A-23 · Medio · Citas largas se recortan en silencio en el PNG**

`QuoteCardPreview.kt:71-72` limita a 6 líneas (cuadrado) u 11 (historia) con `TextOverflow.Ellipsis`, y el tamaño mínimo es 17 sp para textos de más de 420 caracteres (`:124-131`). El lector entrega la selección completa sin límite (`EpubReaderSessionEffects.kt:168`, `PdfReaderDialogs.kt:373`). Una cita de 800 caracteres se exporta con «…» y el resto se pierde sin aviso.

*Corrección:* límite de entrada visible (contador + `maxLength`), o un layout que garantice que el texto quepa (autoajuste por medida real, `TextMeasurer`).

**A-24 · Medio · «Preparando imagen…» sin tiempo de espera ni estado recuperable**

`startSharing` (`ShareCardViewModel.kt:76-93`) no tiene `withTimeout` ni `finally`: si la captura no llega (app en segundo plano, frame perdido, rotación a mitad), el estado queda en `Generating` y el botón deshabilitado **indefinidamente**; todos los `update*` se ignoran mientras tanto (`:46-60`), así que la pantalla parece congelada. Tampoco hay `SavedStateHandle`: la muerte de proceso descarta texto, autor y tema (`ShareCardScreen.kt:59` usa `remember`, `ShareCardModule.kt:22` no recibe estado).

*Corrección:* `withTimeout(15_000)` + `finally { Idle }`, y `SavedStateHandle`/`rememberSaveable` para la tarjeta editable.

**Otros puntos de esta feature (bajo)**

- Los errores del share sheet se reportan como errores de generación con un mensaje único en español (`ShareCardViewModel.kt:102-104`), aunque la generación fue correcta.
- Los `FilterChip` de formato y tema no están en un `selectableGroup()` y los mensajes de estado no declaran `liveRegion`: TalkBack no anuncia «Listo»/«Error» (`ShareCardScreen.kt:95-106,159-166`).
- `ShareCardTopBar.kt:27` describe la flecha de volver como «Cerrar».
- `PdfReaderDialogs.kt` y `EpubReaderSessionEffects.kt` pasan a la tarjeta la selección completa, sin recorte, lo que alimenta el caso A-23.

### 7.2 Lectores EPUB/PDF y narración: revisión línea a línea

**A-25 · Alto · La `Publication` de Readium nunca se cierra**

`EpubReaderSessionEffects.kt:106-226` abre la publicación dentro de un `LaunchedEffect` y la guarda en `session.publication.value`, pero **no hay ningún `onDispose` que la cierre**: el único `DisposableEffect` del archivo (`:64-66`) solo desvincula el servicio TTS. Un `grep` de `publication.close()` en todo `app/` no devuelve resultados.

*Consecuencia:* cada apertura y **cada rotación** deja viva una `Publication` con su ZIP/asset abierto: fuga de memoria y de descriptores de archivo que crece con el uso; si la composición se cancela durante `openPublication()`, la publicación recién creada queda huérfana sin nadie que la libere. Los `InputListener` añadidos (`:208-216`) tampoco se retiran.

*Corrección:* `DisposableEffect(uri) { onDispose { session.ttsNavigator.value?.close(); session.publication.value?.close(); session.publication.value = null } }`, `removeInputListener` en el mismo bloque, y cancelación explícita si `openPublication()` termina después de que la composición murió.

**A-26 · Alto · La narración queda huérfana al rotar o salir del lector**

`DisposableEffect(Unit) { onDispose { session.ttsServiceBinding.value?.unbind() } }` (`EpubReaderSessionEffects.kt:64-66`) solo desvincula: el `TtsPlaybackService` sigue en primer plano reproduciendo. Tras recrear la Activity, `session.ttsNavigator` es `null` (el estado vive en `remember(uri)`, `ReaderActivity.kt:73`), así que `if (ttsNavigator != null) TtsControls(...)` (`:247`) no dibuja nada y el `LaunchedEffect` que hacía `reader.go(location.utteranceLocator)` (`:250-263`) ya no existe.

*Consecuencia:* audio sonando sin panel de TTS y sin que la página avance; el usuario solo puede detenerlo desde la notificación. Al salir con «atrás» ocurre lo mismo (no hay `stopTts()` en `onBack`).

*Corrección:* al abrir el libro, si `binder.currentSession?.bookUri` coincide, re-adherir navegador, voces y `isPlaying`; y decidir explícitamente el comportamiento al salir (detener o avisar «la narración continúa»).

**A-27 · Medio · Doble toque en TTS crea dos vinculaciones y filtra una**

`EpubTtsSessionActions.kt:79-88`: `session.ttsServiceBinding.value ?: try { TtsPlaybackService.bind(app).also { session.ttsServiceBinding.value = it } }` no tiene bandera de «en curso». Dos toques rápidos lanzan dos corrutinas, ambas ven `null`, ambas hacen `bindService`, y la segunda asignación pisa a la primera, cuyo `ServiceConnection` ya no se libera.

*Consecuencia:* `ServiceConnection`, `MediaSession` y `WakeLock` retenidos aunque el usuario cierre el libro.

*Corrección:* `AtomicBoolean`/`Mutex` de arranque y botón deshabilitado mientras se prepara.

**A-28 · Medio · Se registra tiempo de lectura cuando el libro nunca abrió**

`ReaderActivityHost.kt:50-53` crea el `ReadingSessionRecorder` y `onResume()` (`:72-75`) arranca el cronómetro sin esperar a que `openPublication()` tenga éxito; `ReadingSessionRecorder.kt:41-46` solo comprueba `closed`/`checkpointElapsedMs`.

*Consecuencia:* si el EPUB falla (archivo movido, permiso SAF revocado) y el usuario deja la pantalla de error abierta, esos minutos se suman a las estadísticas de un libro que quizá ya no existe.

*Corrección:* arrancar/pausar el recorder según `!loading && errorMessage == null`. El mismo patrón se repite en el visor PDF (`PdfReaderActivityHost.kt:48-51`).

**A-29 · Medio · «Atrás» cierra el libro en lugar de ocultar los controles**

No existe `BackHandler` ni `OnBackPressedCallback` en ningún archivo del lector (verificado por búsqueda). `onBack = { finish() }` (`ReaderActivityHost.kt:66`) es la única salida, y el manifest no declara `android:enableOnBackInvokedCallback` para `targetSdk 36`.

*Consecuencia:* el gesto de atrás, que en un lector se espera para volver de la hoja de apariencia o para ocultar la barra, abandona el libro; no hay soporte de *predictive back*.

*Corrección:* `BackHandler` con prioridad hojas → controles → `finish()`, y `enableOnBackInvokedCallback="true"`.

**A-30 · Medio · Reconstrucción completa del árbol en cada cambio de página o frase narrada**

`ReaderActivity.kt:233` evalúa `flattenContents(it.tableOfContents)` **en el cuerpo de la composición**, y `ReaderChrome(...)` recibe 25 parámetros con lambdas inline (`:149-244`). Como la pantalla lee `progress`, `ttsSentence`, `ttsIsPlaying`, etc., cada página o frase recrea la lista completa del índice y todas las lambdas, además del `AndroidView`.

*Corrección:* `remember(publication)` para el índice, `remember`/`rememberUpdatedState` para los callbacks, y separar barra superior, inferior y hojas para que el progreso solo recomponga la barra inferior.

**A-31 · Medio · `locateProgression` en el dispatcher de UI, sin cancelación**

`ReaderActivity.kt:237-243` lanza `publication?.locateProgression(...)` desde `rememberCoroutineScope()`, que despacha en `AndroidUiDispatcher`. El cálculo recorre el `readingOrder`; cada arrastre nuevo del control de progreso apila otra ejecución.

*Consecuencia:* la barra «no responde» en libros grandes (sin indicador) y riesgo de ANR.

*Corrección:* `withContext(Dispatchers.IO)`, cancelar el `Job` anterior y mostrar estado «Buscando posición…».

**A-32 · Medio · La narración deshace los saltos manuales**

`EpubReaderSessionEffects.kt:262` ejecuta `reader.go(location.utteranceLocator, animated = false)` en cada emisión de `speech.location`, sin pausar TTS ni detectar que el usuario está navegando.

*Consecuencia:* si se salta de capítulo mientras se narra, la vista vuelve sola al punto narrado a los pocos segundos; parece que el salto falló.

*Corrección:* pausar y re-anclar el TTS antes de un salto explícito, y suprimir el `go()` automático durante un breve periodo tras interacción del usuario.

**A-33 · Medio · Borrado de notas y resaltados sin confirmación ni deshacer**

`EpubAnnotationSheets.kt:146-148`: la papelera de cada anotación ejecuta `onDeleteAnnotation` directamente contra un `DELETE` duro (`ReadingAnnotationDao.kt:26`). Está junto a la fila que navega al punto de la nota.

*Consecuencia:* un toque accidental borra una nota escrita, sin recuperación.

*Corrección:* `AlertDialog` de confirmación o `Snackbar` con «Deshacer» reinsertando la entidad.

**A-34 · Medio · Locator almacenado inválido que nunca se puede corregir**

`ReaderActivity.kt:196` y `EpubReaderSessionEffects.kt:115-117` aceptan cualquier JSON que `Locator.fromJSON` pueda parsear y lo pasan como `initialLocator` al `EpubNavigatorFactory` (`:195`) sin comprobar `href`/`locations` contra el `readingOrder` del EPUB abierto. Un locator de otra edición del libro abre en una posición inexistente y permanece en Room indefinidamente.

*Corrección:* validar `href` contra `opened.readingOrder`; si no coincide, ignorar el locator y limpiar la columna.

**Otros (bajo, mismo módulo)**

- Brillos: cada frame de arrastre escribe en `SharedPreferences` y reasigna `window.attributes` (`ReaderActivity.kt:211-216`, `ReaderControls.kt:335-340`).
- Insets hardcodeados (`bottom = 112.dp`, `top = 88.dp`) que se solapan con notch/gestos (`ReaderActivity.kt:295,299`).
- `contentDescription` incorrectos: «Pausar narración» cuando en realidad reanuda (`ReaderChrome.kt:283`), «Eliminar marcador» para notas y resaltados (`EpubAnnotationSheets.kt:147`); slider de progreso sin etiqueta (`ReaderChrome.kt:338`).
- La búsqueda se ejecuta en el scope de la hoja y se cancela al cerrarla (`EpubSearchSheet.kt:64`), y el truncado por límite de páginas no siempre se informa (`:112-120`).
- Se pide `POST_NOTIFICATIONS` y se ignora el resultado (`ReaderActivity.kt:69-71`), de modo que en API 33+ puede arrancar un servicio en primer plano sin notificación visible.
- El filtro de voces solo admite locales (`EpubReaderUtilities.kt:12-18`): si la única voz española del dispositivo es de red, la lista queda vacía y la narración falla sin explicar por qué.
- `Icons.Default.ArrowBack` está deprecado y los chevrons no se espejan con `supportsRtl="true"` (`ReaderChrome.kt:270,350,365`).
- El «modo inmersivo» del PDF solo oculta los controles de la app: `PdfReaderActivityHost.kt:17-46` no llama a `WindowCompat.setDecorFitsSystemWindows` ni oculta las barras del sistema, a diferencia de `ReaderActivityHost.kt:56`; las barras del sistema siguen visibles aunque el README hable de lectura inmersiva (`README.md:11`).
- Lecturas de `SharedPreferences` dentro de la composición (`EpubReaderSessionState.kt:18-33` invocado desde `ReaderActivity.kt:73`).

### 7.3 Biblioteca y catálogo

**A-35 · Medio · El orden alfabético rompe con acentos y Ñ**

```kotlin
// LibraryScreen.kt:361-362
LibrarySortMode.TITLE_DESC -> visibleBooks.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
LibrarySortMode.TITLE_ASC  -> visibleBooks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
```

`CASE_INSENSITIVE_ORDER` compara unidades UTF-16: `'Á'` (193) y `'Ñ'` (209) quedan **después de `'z'`** (122). En una biblioteca en español, «Ángel», «Época» y «Ñandú» aparecen al final del listado A-Z.

*Corrección:* `Collator.getInstance(Locale.getDefault())` (o comparar la forma normalizada sin diacríticos que ya existe en `LibrarySearch.kt`).

**A-36 · Medio · El escaneo de carpetas se cancela al rotar y se pierde entero**

`scannerJob.value = coroutineScope.launch { LocalFolderScanner.scan(...) }` con `coroutineScope = rememberCoroutineScope()` (`LibraryScreen.kt:160`, `LibraryDocumentActions.kt:244-261`). Al recrearse la Activity el scope se cancela: un escaneo de varios minutos se aborta y, como `scannerBusy` y `scannerPreview` también viven en `remember`, no queda diálogo ni aviso; el usuario debe repetirlo. Además el permiso de árbol ya se persistió, así que no hay rastro de lo ocurrido.

*Corrección:* lanzar el escaneo en `viewModelScope` (con el `treeUri` y el progreso en el ViewModel) y consultar el permiso persistido al volver.

**A-37 · Medio · «Tiempo de lectura hoy» y la meta diaria se congelan a medianoche**

`val todayKey = readingDayKey(System.currentTimeMillis())` (`LibraryScreen.kt:134`) se calcula una vez por composición y nada lo invalida. Con la app abierta pasada la medianoche, la consulta sigue apuntando al día anterior: «hoy» muestra el tiempo de ayer y la barra de meta no se reinicia.

*Corrección:* `LaunchedEffect` que reprograme el cambio de día (`delay` hasta la próxima medianoche) y actualice el estado.

**A-38 · Medio · El outbox de limpieza se drena una sola vez y provoca N escaneos del directorio**

`LibraryScreen.kt:228-243`: `pendingCleanupUris()` se lee una vez al crear la pantalla; una limpieza que falle con la pantalla abierta no se reintenta (aunque el mensaje al usuario diga «se reintentará al abrir la biblioteca»), y **cada** éxito hace `offlineRevision++`, que dispara otro `OfflineBookStore.findCopies` completo (`listFiles()` de todo el directorio, `:147-152`): N pendientes = N escaneos.

*Corrección:* drenar el outbox en el `Application`/ViewModel al arrancar y en `ON_RESUME`, e incrementar la revisión una sola vez al final del bucle.

**A-39 · Medio · Acciones destructivas sin confirmación**

- Borrar colección (`LibraryManagementDialogs.kt:214-221`): elimina la colección y limpia `collectionId` de sus libros (`BookDao.kt:137-143`), con el botón a 4 dp de «Renombrar».
- Quitar copia sin conexión (`LibraryCatalog.kt:341-347`): borra el archivo de `noBackupFilesDir` de un toque; si el permiso SAF del original se revocó, esa copia puede ser la única fuente legible.
- Borrar nota o resaltado en el lector (A-33).

*Corrección:* diálogo con el recuento de afectados, o `Snackbar` con «Deshacer».

**A-40 · Medio · La cuadrícula es una fila Lazy por cada dos libros, con claves por posición**

```kotlin
// LibraryCatalog.kt:301-308
count = if (listMode.value) orderedBooks.size else (orderedBooks.size + 1) / 2,
key = { row -> orderedBooks[if (listMode.value) row else row * 2].uri.toString() }
```

Cada fila compone dos tarjetas aunque solo se vea una; la clave es la URI del **primer** libro de la fila, cambia al alternar lista/cuadrícula y al borrar el primero, y no hay `contentType`, así que Lazy no puede reutilizar subárboles ni animar por libro; tocar «favorito» en uno recompone los dos.

*Corrección:* `LazyVerticalGrid(GridCells.Fixed(2))` con `items(orderedBooks, key = { it.id })`.

**Trabajo de catálogo en el hilo principal (P-01, ampliado)**

`searchIndex`, `visibleBooks` y `orderedBooks` están memoizados con `remember(books)` (`LibraryScreen.kt:322-364`), pero `books` cambia con **cada emisión de Room** (cada guardado de progreso), de modo que normalizar títulos con `Normalizer` + dos regex por libro, filtrar y ordenar se repite en el hilo de UI con cada emisión. Con 1 000+ libros se nota al volver del lector.

*Corrección:* derivar índice, filtro y orden en el ViewModel (`Dispatchers.Default`) y exponer el resultado ya calculado; o `derivedStateOf` sobre un índice por `id`.

**Otros (bajo, `feature:library`)**

- Ítems `LazyColumn` con clave (`"dashboard"`, `"filters"`, `"viewControls"`) mezclados con ítems sin clave (buscador, «Retomar», colecciones): al aparecer/desaparecer secciones condicionales se pierde la identidad del buscador y su foco/IME (`LibraryCatalog.kt:118,156,200`).
- El diálogo de reinicio de estadísticas se apila sobre el de estadísticas y su botón no se deshabilita durante la operación (`LibraryStatisticsDialogs.kt:155-178`).
- La barra de meta diaria no tiene `progressSemantics` ni valor textual (`LibraryStatisticsDialogs.kt:88-91`); el contador de «en curso» es un número suelto para TalkBack (`LibraryCatalog.kt:167`).
- `contentDescription = "Vista previa local de la primera página PDF"` hardcodeado dentro de una tarjeta ya etiquetada (`LocalPdfCover.kt:110`).
- Lectura de `SharedPreferences` en composición para la meta diaria (`LibraryScreen.kt:187-192`), sin refresco si cambia por otra vía.

---

## 8. Rendimiento y memoria

| ID | Hallazgo | Evidencia | Impacto |
|---|---|---|---|
| P-01 | **Flows de Room recreados en cada recomposición** de `LibraryScreen`: `viewModel.observeDurationForDay(todayKey)` y `observeReadingDayKeys()` se invocan como argumentos de `collectAsState`, sin `remember`, así que cada tecla del buscador reinicia la suscripción y relanza la consulta. | `LibraryScreen.kt:135-136` | Consultas Room repetidas al escribir; jank perceptible |
| P-02 | **`collectAsState` en lugar de `collectAsStateWithLifecycle`** en biblioteca, lector y audio: las consultas siguen vivas con la app en segundo plano. | `LibraryScreen.kt:135-137`, `PdfReaderActivity.kt:63`, `AudioPlayerActivity.kt:146` | Batería y trabajo de BD innecesario |
| P-03 | **Extracción de portadas serializada por un mutex global**: todas las tarjetas visibles hacen cola detrás de un único `Mutex` y cada una parsea el EPUB completo. | `LocalEpubCover.kt:42,51-93` | Portadas lentas en cuadrícula con muchos libros |
| P-04 | **Miniaturas PDF sin límite de concurrencia**: cada tarjeta visible abre su propio `PdfRenderer` y renderiza la primera página. | `feature/library/.../LocalPdfCover.kt:47-79` | Picos de memoria al hacer scroll rápido |
| P-05 | **Bitmaps de página sin caché acotada**: cada página viva puede ocupar hasta 4 M píxeles (≈16 MB ARGB) y conviven varias en el scroll continuo. | `PdfRendering.kt:115,119-139` | OOM en gama baja con PDFs grandes |
| P-06 | **Todos los libros en memoria**: `observeAll()` sin paginación; el propio equipo lo documenta como pendiente. | `BookDao.kt:13-14`, `docs/VALIDACION_ANDROID.md:35` | 10 000 libros → lista completa en RAM |
| P-07 | **Assets sobredimensionados**: `leepe_logo.png` es 1254×1254 y 1,37 MB, y se usa como icono de splash (`values-v31/styles.xml:6`) y fondo de ventana (`drawable/leepe_splash_background.xml`), lo que supone ~6 MB decodificados por ventana. `leepe_icon_foreground.png` (279 KB) está **duplicado** en `app` y en `feature:library`; `leepe_logo_mark.png` repite el mismo archivo en `feature:sharecard`. | `res/drawable-nodpi/*` | APK inflado y arranque más lento |
| P-08 | **140 strings duplicados byte a byte** entre `app` y `feature:library` (archivos idénticos según SHA-256) | `app/src/main/res/values/strings.xml` ≡ `feature/library/src/main/res/values/strings.xml` | Divergencia futura garantizada |

---

## 9. Internacionalización y accesibilidad

**A-20 · Alto · Dos estrategias de i18n en la misma app**

La biblioteca y las tarjetas usan recursos: **162 identificadores `R.string`** referenciados, todos definidos, sin huérfanos, y una traducción `values-en` completa y real («Your next story starts here», «Backup and restore», «%1$d%% read»). Pero la cobertura es despareja: **151 literales de UI en español** quedan fuera de los recursos.

| Módulo | Literales hardcodeados | `R.string` |
|---|---|---|
| `app` (lector EPUB/PDF, audio, TTS) | **122** | 2 |
| `feature:library` (toasts de importación/respaldo, tarjetas «Retomar») | **29** | ~140 |
| `feature:sharecard` | 0 | 22 |

Los peores archivos son `PdfReaderDialogs.kt` (28), `ReaderControls.kt` (26), `ReaderChrome.kt` (18), `PdfReaderControls.kt` (12), `EpubAnnotationSheets.kt` (10), `AudioPlayerActivity.kt` (9), `EpubSearchSheet.kt` (7) y `LibraryDocumentActions.kt` (11 toasts).

*Consecuencia:* un usuario en inglés ve la biblioteca traducida y **el lector, el PDF, el reproductor y los mensajes de importación en español**; las etiquetas de accesibilidad («Volver», «Página anterior») tampoco se traducen.

*Corrección:* extraer los literales a `values/strings.xml` + `values-en/`, con `plurals` para los contadores de importación, y activar en CI `lint --check HardcodedText,SetTextI18n,ContentDescription`.

**Accesibilidad adicional**

- `La app` no respeta el tema claro del sistema: `LeePeTheme` usa siempre `darkColorScheme` (`core/ui/.../Theme.kt:25-43`), aunque el lector sí ofrece claro/sepia/oscuro/OLED. Un usuario con el sistema en claro ve una biblioteca oscura sin opción.
- Faltan `contentDescription` en las portadas/íconos decorativos ya citados y no hay etiquetas de estado («leído al 45 %») en las tarjetas (depende de `LibraryBookCards`, ver §11).

---

## 10. Pruebas, CI y proceso

**Cobertura actual**

| Suite | Pruebas | Qué cubre |
|---|---|---|
| `core:domain` | 3 | Normalización del progreso |
| `app` (unit) | 9 | Emparejamiento de respaldos, partición de tiempo por día |
| `feature:library` (unit) | 20 | Búsqueda, scanner (solo constantes), respaldo (parseo), estadísticas |
| `feature:notes` | 2 | Exportación Markdown |
| `feature:sharecard` (unit + androidTest) | 9 | ViewModel de tarjeta, generación de PNG, UI |
| `feature:library` (androidTest) | 4 | Migraciones Room, borrado transaccional, colecciones |

**Brechas**

- **Cero pruebas** para `OfflineBookStore` (copias, espacio, bloqueos), `PdfRendering.openPdf`/`pdfBitmapSize` (función pura, trivial de testear), `PdfTextSearch`, `ReadingSessionRecorder.splitSecondsByLocalDay` (solo se prueba el día, no el cambio de horario), `LocalLibraryBackup.restore` (solo se prueba `parse`), `DeletedBookCleanup` y todo el flujo de importación SAF.
- Una prueba es tautológica: `LocalFolderScannerTest.kt:23-26` afirma que las constantes valen lo que valen; no ejercita el escaneo.
- No hay pruebas de UI del lector ni de rotación, que es justo donde están los riesgos A-04/A-05/A-06.

**CI (` .github/workflows/android-build.yml`)**

- Ejecuta `lintDebug` + tests + `assembleDebug` + `assembleDebugAndroidTest` y luego un emulador API 35: buena base.
- **Duplica trabajo**: `:feature:sharecard:testDebugUnitTest` junto a `testDebugUnitTest` (que ya corre en todos los módulos) y `:feature:sharecard:connectedDebugAndroidTest` junto a `connectedDebugAndroidTest` (`.github/workflows/android-build.yml:58,73`), lo que alarga el pipeline sin cubrir nada nuevo.
- **Estado histórico del módulo `:core:domain`:** en el commit auditado, `testDebugUnitTest` no ejecutaba sus pruebas JVM. El workflow actual incluye `:core:domain:test`, así que ese hueco ya está cerrado.
- **44 advertencias de compilación** en el build verificado, la mayoría deprecaciones con acción concreta: `Icons.Filled.MenuBook`/`ArrowBack`/`VolumeUp`/`ViewList`/`RotateRight` → `Icons.AutoMirrored.Filled.*` (que además corrige RTL) y `LinearProgressIndicator(progress: Float)` en `LibraryResumeCards.kt:154` y `LibraryStatisticsDialogs.kt:88` → sobrecarga con lambda.
- **Tres `Elvis` muertos** (`?:` sobre un `String` no nulo, detectados por el compilador): `EpubTtsSessionActions.kt:125`, `ReaderActivityHost.kt:96` y `:107`. El mensaje de respaldo en español nunca se muestra; el usuario ve el texto de la librería, a menudo en inglés y sin contexto.
- **No valida el wrapper** (`gradle/actions/wrapper-validation`), no usa `verification-metadata` para checksums de dependencias y no tiene Dependabot/Renovate: cadena de suministro sin control.
- **No publica AAB ni artefacto firmado**; el artefacto es un APK de depuración.
- Sin `matrix` de API (24/26/33/35): los caminos de compatibilidad (`minSdk 24`, búsqueda PDF solo API 35) no se prueban en CI.

---

## 11. Documentación

`docs/` es inusualmente buena para un proyecto de este tamaño: hay matriz de validación en dispositivo con criterios de salida (`VALIDACION_ANDROID.md`), arquitectura con reglas de dependencia y deuda reconocida (`ARQUITECTURA_MODULAR.md`), y un plan por fases con casillas honestas (`PLAN_TRABAJO.md`).

**Desincronizaciones concretas**

| Documento | Dice | Código |
|---|---|---|
| `README.md:46` | «aún falta implementar un respaldo manual local completo y restaurable» | El respaldo JSON está implementado y probado (`LocalLibraryBackup.kt`) |
| `README.md:13` | El respaldo «no está cifrado» | Correcto, y la app lo advierte en el diálogo (A-12 retirado) |
| `docs/GRADLE_VALIDACION.md:28` | CI ejecuta `:feature:library:connectedDebugAndroidTest` | El workflow ejecuta `:feature:sharecard:connectedDebugAndroidTest connectedDebugAndroidTest` |
| `docs/ARQUITECTURA_MODULAR.md:84-90` | «CI debe … publicar dichos esquemas» | El workflow sube `core/database/schemas/**/*.json` dentro del artefacto del APK; no hay verificación de que el esquema no cambie sin migración |
| `README.md:37` | Android Studio con «AGP 9.3» | El catálogo fija AGP **9.3.1** (correcto, pero conviene un rango mínimo soportado) |

---

## 12. Tabla consolidada de hallazgos

**Escala de severidad usada:** *Alto* = riesgo de cierre inesperado, pérdida de datos, exposición de información o incumplimiento de una promesa explícita del producto; *Medio* = defecto visible o deuda que degrada experiencia, rendimiento o mantenibilidad; *Bajo* = higiene. Los hallazgos marcados como *riesgo* están justificados por el código y por patrones conocidos de la plataforma, pero requieren confirmación en dispositivo.

| ID | Sev. | Área | Hallazgo | Evidencia |
|---|---|---|---|---|
| A-01 | **Alto** | Seguridad | Servicios exportados sin permiso; binder TTS público | `AndroidManifest.xml:21-36`, `TtsPlaybackService.kt:160-222` |
| A-02 | **Alto** | Datos | Progreso EPUB puede guardarse como 0 % durante TTS | `TtsPlaybackService.kt:81-88` |
| A-03 | **Alto** | Datos | Respaldo pierde colecciones múltiples | `LocalLibraryBackup.kt:63`, `LocalBackupDao.kt:80-100` |
| A-04 | **Alto** | UX | Estado perdido al rotar; EPUB/PDF se reabren | `LibraryScreen.kt:140-226`, `ReaderActivityHost.kt:38`, `PdfReaderActivity.kt:82-96` |
| A-05 | **Alto** | Estabilidad | Reciclado de bitmaps en uso | `PdfReaderActivity.kt:132-134`, `PdfRendering.kt:187-189` |
| A-06 | Medio | Estabilidad | `MediaController` liberado retenido por el ViewModel | `AudioPlayerActivity.kt:126-131`, `AudioPlayerViewModel.kt:27-64` |
| A-07 | Medio | Recursos | `DefaultHttpClient` por apertura, sin cerrar | `ReaderActivityHost.kt:93`, `LocalEpubCover.kt:57` |
| A-08 | Medio | Almacenamiento | Copias PDF en caché sin purga | `PdfRendering.kt:239-269` |
| A-09 | Medio | Rendimiento | `StatFs` por bloque en copia offline | `OfflineBookStore.kt:83-89` |
| A-10 | Medio | Rendimiento | Escritura Room por cada locator | `EpubReaderSessionEffects.kt:301-317` |
| A-11 | Medio | Rendimiento | Respaldo O(n) en preferencias | `LocalLibraryBackup.kt:64-75` |
| A-12 | — | **Retirado** | El diálogo de respaldo **sí** avisa de que el JSON no está cifrado (`backup_privacy_notice`); el cifrado opcional queda como mejora, no como defecto | `LibraryProcessDialogs.kt:155-158` |
| A-13 | — | **Retirado** | La comprobación `factory == null` **no** es código muerto: el factory es nullable (lo demostró el compilador al eliminarla). Se restauró el código | `EpubTtsSessionActions.kt:103-110` |
| A-14 | Medio | Estabilidad | `startService` en servicio de primer plano | `TtsPlaybackService.kt:191-193` |
| A-15 | Medio | Legal | Sin archivo `LICENSE` | Repo metadata |
| A-16 | Medio | Release | Sin build `release`, firma, R8 ni versionado | `app/build.gradle.kts` (sin `buildTypes`) |
| A-17 | Medio | Robustez | `file://` como clave de biblioteca | `BundledWelcomeBook.kt:49` |
| A-18 | Bajo | Batería | `WakeLock` sin timeout | `TtsPlaybackService.kt:151-157` |
| A-19 | Bajo | Accesibilidad | Gesto global con TalkBack, `Row` de TTS sin adaptación | `EpubReaderSessionEffects.kt:211-216`, `ReaderControls.kt:95-165` |
| A-20 | **Alto** | i18n | 151 literales fuera de recursos; lector sin traducir | §9 |
| A-21 | **Alto** | Memoria | 4-5 copias del frame al compartir; `OOM` fuera del `catch` | `ShareCardViewModel.kt:66-87`, `ComposeShareCardGenerator.kt:20-33` |
| A-22 | Medio | Ciclo de vida | Captura dependiente de `MonotonicFrameClock` y de composición destruida | `ShareCardScreen.kt:58,143-145` |
| A-23 | Medio | Corrección | Citas largas truncadas sin aviso en el PNG | `QuoteCardPreview.kt:71-72,124-131` |
| A-24 | Medio | UX | Estado `Generating` sin timeout ni recuperación | `ShareCardViewModel.kt:76-93` |
| A-25 | **Alto** | Recursos | `Publication` de Readium nunca se cierra; listeners sin retirar | `EpubReaderSessionEffects.kt:106-226` |
| A-26 | **Alto** | UX | Narración huérfana al rotar o salir: audio sin controles | `EpubReaderSessionEffects.kt:64-66`, `ReaderActivity.kt:73,247` |
| A-27 | Medio | Recursos | Doble toque en TTS → dos `bindService`, una conexión filtrada | `EpubTtsSessionActions.kt:79-88` |
| A-28 | Medio | Datos | Tiempo de lectura contado aun si el libro no abrió | `ReaderActivityHost.kt:50-53,72-75` |
| A-29 | Medio | UX | Sin `BackHandler`: «atrás» cierra el libro con los controles visibles | `ReaderActivityHost.kt:66` |
| A-30 | Medio | Rendimiento | Índice y 25 lambdas recreados por cambio de página/frase | `ReaderActivity.kt:233,149-244` |
| A-31 | Medio | Rendimiento | `locateProgression` en Main, sin cancelación | `ReaderActivity.kt:237-243` |
| A-32 | Medio | UX | La narración deshace los saltos manuales | `EpubReaderSessionEffects.kt:262` |
| A-33 | Medio | UX | Borrado de notas/resaltados sin confirmación | `EpubAnnotationSheets.kt:146-148` |
| A-34 | Medio | Corrección | Locator inválido aceptado sin validar contra `readingOrder` | `ReaderActivity.kt:196`, `EpubReaderSessionEffects.kt:115-117` |
| A-35 | Medio | Corrección | Orden alfabético roto con acentos y Ñ | `LibraryScreen.kt:361-362` |
| A-36 | Medio | UX | El escaneo de carpetas se cancela al rotar | `LibraryDocumentActions.kt:244-261` |
| A-37 | Medio | Corrección | «Hoy» y meta diaria congelados a medianoche | `LibraryScreen.kt:134` |
| A-38 | Medio | Datos | Outbox drenado una vez y N escaneos del directorio | `LibraryScreen.kt:228-243` |
| A-39 | Medio | UX | Borrado de colección/copia offline sin confirmación | `LibraryManagementDialogs.kt:214-221`, `LibraryCatalog.kt:341-347` |
| A-40 | Medio | Rendimiento | Cuadrícula en filas de 2 con claves por posición | `LibraryCatalog.kt:301-308` |
| P-01 | **Alto** | Rendimiento | Flows Room recreados por recomposición | `LibraryScreen.kt:135-136` |
| P-02 | Medio | Rendimiento | `collectAsState` sin ciclo de vida | 3 archivos |
| P-03 | Medio | Rendimiento | Portadas EPUB serializadas por mutex | `LocalEpubCover.kt:42-93` |
| P-04 | Medio | Rendimiento | Miniaturas PDF sin control de concurrencia | `LocalPdfCover.kt:47-79` |
| P-05 | Medio | Memoria | Bitmaps de página sin caché acotada | `PdfRendering.kt:115-139` |
| P-06 | Medio | Escalabilidad | Biblioteca completa en memoria | `BookDao.kt:13-14` |
| P-07 | Bajo | APK | Logo 1,37 MB como splash; assets duplicados | `res/drawable-nodpi/*` |
| P-08 | Bajo | Mantenimiento | 140 strings duplicados entre módulos | `values/strings.xml` ×2 |
| M-01…M-05 | Bajo | Arquitectura | Paquetes divididos, `:app` dios, fuga de Room, código muerto | §4 |

---

## 13. Plan de remediación propuesto

### Sprint 1 — Estabilidad y seguridad (1 semana)

1. **A-01**: permiso de firma para el binder TTS, `exported` acotado y validación de `packageName`.
2. **A-02**: no persistir `progress = 0f` cuando `totalProgression` es nulo; prueba unitaria con locator sin `totalProgression`.
3. **A-05**: eliminar `recycle()` manual en PDF y scroll continuo; añadir `LruCache` de páginas si hace falta memoria.
4. **A-06**: `detach()` en `onDestroy()`, comprobación de conexión y sondeo ligado al ciclo de vida.
5. **A-14/A-18**: `startForegroundService` + `startForeground` inmediato; `WakeLock` con timeout.
6. **A-04 (parte 1)**: `rememberSaveable` en `LibraryScreen` (búsqueda, filtro, orden, diálogos) y en visor PDF (página, zoom).
7. **A-21**: captura de tarjetas con una sola copia de píxeles, render al tamaño destino y manejo explícito de `OutOfMemoryError`.
8. **A-24**: `withTimeout` + `finally` en `startSharing` para que «Preparando imagen…» nunca quede bloqueado.
9. **A-25**: `DisposableEffect` que cierre la `Publication` y retire los `InputListener`.
10. **A-26**: re-adherir el TTS al reabrir el libro y decidir qué pasa al salir con narración activa.
11. **A-27**: bandera de arranque en TTS para impedir vinculaciones duplicadas.
12. **A-29**: `BackHandler` con prioridad hojas → controles → salir.

**Criterio de salida:** rotar en biblioteca, EPUB, PDF y audio sin perder estado, sin crashes y sin reabrir el documento; `lint` limpio.

### Sprint 2 — Datos y rendimiento (1-2 semanas)

1. **A-03**: respaldo v2 con colecciones múltiples + compatibilidad con v1 + prueba de ida y vuelta.
2. **A-07/A-08**: cliente Readium único por proceso; purga de `lee-pe-*.pdf` y de copias huérfanas al arrancar.
3. **P-01/P-02**: `remember` de los flows + `collectAsStateWithLifecycle` en las cuatro pantallas.
4. **A-09/A-10/A-11**: espacio comprobado por bloques grandes, `debounce` de locator, respaldo con una sola lectura de preferencias.
5. **P-05/P-06**: decidir paginación (Room Paging 3) o al menos límite de páginas vivas; medir con 1 000 y 10 000 libros.
6. **A-35/A-37/A-38/A-40**: orden con `Collator`, cambio de día a medianoche, outbox drenado en el ViewModel y cuadrícula con `LazyVerticalGrid`.
7. **A-36**: mover el escaneo de carpetas al ViewModel para que la rotación no lo cancele.
8. **A-30/A-31**: memoizar el índice y los callbacks del lector; `locateProgression` en IO con cancelación.
9. **A-33/A-39**: confirmación o «deshacer» en todos los borrados (nota, colección, copia offline).

**Criterio de salida:** respaldo restaurado en otro dispositivo conserva colecciones y notas; perfilado sin jank al escribir en el buscador.

### Sprint 3 — Producto publicable (1-2 semanas)

1. **A-20**: extraer los 151 literales a recursos (122 en `app`, 29 en `feature:library`) y completar `values-en`; activar el chequeo de strings en CI.
2. **A-16/A-15**: `release` con R8, firma por variables de entorno, `versionCode` automático, `LICENSE` y `NOTICE`.
3. **A-19**: accesibilidad (TalkBack, fuente 200 %, `FlowRow` en TTS), tema claro.
4. **A-17**: mover el libro incluido a `FileProvider`.
5. **CI**: validación del wrapper, `verification-metadata`, matriz de API 24/33/35, `bundleRelease`, publicación de esquemas Room y de informes.

**Criterio de salida:** AAB firmado, instalable en API 24 y 36, con biblioteca y lector íntegros en inglés y español.

---

## 14. Anexos

### A. Comandos de verificación recomendados

```bash
# Calidad estática y pruebas
./gradlew --no-daemon lint test

# Strings hardcodeados (una vez extraídos los recursos)
# en cada módulo Android:  android { lint { check += setOf("HardcodedText","SetTextI18n","ContentDescription") } }
./gradlew lintDebug

# Esquema Room sin cambios silenciosos
./gradlew :core:database:kspDebugKotlin && git diff --exit-code core/database/schemas

# Release firmado (variables de entorno, nunca en el repo)
./gradlew --no-daemon bundleRelease

# Pruebas instrumentadas por API
./gradlew connectedDebugAndroidTest   # con emulador API 24, 33 y 35
```

### B. Checklist mínimo antes de publicar

- [ ] `LICENSE` + `NOTICE` con atribuciones (Readium BSD-3, Media3/Compose Apache-2.0).
- [ ] `release` con R8, firma y `versionCode` incremental.
- [ ] Sin `INTERNET` (verificado con `aapt dump permissions`).
- [ ] Controladores de `MediaSession` restringidos (`onConnect`) y binder de TTS no exportado.
- [ ] Rotación y muerte de proceso probadas en EPUB, PDF y audio.
- [ ] Respaldo exportado/restaurado entre dos dispositivos con colecciones múltiples.
- [ ] Inglés y español completos en lector, PDF y audio.
- [ ] TalkBack y fuente al 200 % en biblioteca y lector.
- [ ] API 24, 33 y 36 verificadas en dispositivo físico.
