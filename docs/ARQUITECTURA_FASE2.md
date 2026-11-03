# Fase 2: arquitectura de la app

## Decisiones

Lee.pe usa inyección manual por constructor. La app tiene una sola base local y un conjunto pequeño de ViewModels; un contenedor `AppContainer` en `LeePeApplication` construye repositorios y `AppViewModelFactory` crea los ViewModels. Esto evita agregar Hilt y mantiene explícitas las dependencias. No se añade ningún servicio de red.

`SettingsRepository` centraliza el acceso a las preferencias Android existentes. Conserva los nombres de archivo, claves y tipos almacenados para que los valores previos y el respaldo local sigan siendo compatibles. La UI recibe esta fachada y no abre `SharedPreferences` directamente.

## Estado de implementación

- `BookRepository`, `AnnotationRepository`, `ReadingStatsRepository` y `LocalBackupRepository` encapsulan los DAO locales.
- La biblioteca expone libros y colecciones a través de `LibraryViewModel.state` (`StateFlow`).
- Los lectores EPUB y PDF exponen sus anotaciones observables mediante ViewModels y guardan el progreso/notas a través de esos ViewModels.
- El reproductor expone reproducción, duración, posición, velocidad y errores en `AudioPlayerViewModel.state` (`StateFlow`).
- Las preferencias se leen y escriben mediante `SettingsRepository`; su implementación conserva las preferencias antiguas para no exigir una migración de datos.
- La biblioteca se dividió entre `LibraryScreen.kt`, `LibraryCatalog.kt`, `LibraryProcessDialogs.kt`, `LibraryManagementDialogs.kt`, `LibraryStatisticsDialogs.kt`, `LibraryDocumentActions.kt`, `LibraryFileOperations.kt`, `LibraryResumeCards.kt` y `LibraryBookCards.kt`. El lector EPUB separa la actividad, Chrome, controles, búsqueda y hojas de anotaciones. El lector PDF separa actividad host, visor, controles, diálogos, estado de pantalla, estado de diálogos y búsqueda de texto.

## Verificación automatizada

- GitHub Actions, ejecución #94: correcta en `lintDebug`, `testDebugUnitTest`, `assembleDebug` y `connectedDebugAndroidTest` sobre emulador Android 35.
- Las pruebas instrumentadas de migración y limpieza de datos compilaron y pasaron en el emulador API 35.
- El artefacto `lee-pe-debug` se generó correctamente y queda disponible en la ejecución hasta el 17 de noviembre de 2026.
- `git diff --check` pasa. La compilación local no pudo ejecutarse porque el entorno no pudo descargar Gradle 9.5.0 (`Network is unreachable`); el build de GitHub Actions es la verificación reproducible.

## Validación manual pendiente

La compilación automatizada no sustituye probar la experiencia de lectura en un teléfono real. Antes de cerrar esta fase faltan los flujos manuales enumerados abajo, especialmente restauración de ubicación, audio con pantalla bloqueada y rotación.

## Avance de revisión (3 de noviembre de 2026)

- `ReaderActivity.kt` quedó en 303 líneas. Sus responsabilidades de host, estado de sesión, efectos, acciones TTS, controles, búsqueda y anotaciones están repartidas entre `ReaderActivityHost.kt`, `EpubReaderSessionState.kt`, `EpubReaderSessionEffects.kt`, `EpubTtsSessionActions.kt`, `ReaderChrome.kt`, `ReaderControls.kt`, `EpubSearchSheet.kt`, `EpubAnnotationSheets.kt` y `EpubReaderUtilities.kt`.
- `LibraryScreen.kt` tiene 399 líneas y la biblioteca está dividida entre los componentes y acciones locales descritos arriba.
- `PdfReaderActivity.kt` quedó en 354 líneas. `PdfPageViewport.kt`, `PdfReaderControls.kt`, `PdfReaderDialogs.kt`, `PdfReaderScreenState.kt`, `PdfReaderDialogsState.kt` y `PdfTextSearch.kt` separan visor, controles, paneles, estado y búsqueda. Todos los archivos Kotlin bajo `ui/` tienen 400 líneas o menos.
- La búsqueda estática de `getSharedPreferences`, llamadas DAO y accesos directos a `LeeDatabase` bajo `app/src/main/java/pe/lee/app/ui` no encontró coincidencias. Esto no reemplaza la compilación.
- La ejecución #94 de GitHub Actions pasó `lintDebug`, `testDebugUnitTest`, `assembleDebug` y `connectedDebugAndroidTest` en emulador API 35. El APK quedó publicado como artefacto `lee-pe-debug`.
- La comprobación local no inició Gradle porque el wrapper intentó descargar Gradle 9.5.0 y el entorno rechazó la conexión (`Network is unreachable`).

## Validación pendiente en dispositivo

Ejecutar y registrar el resultado de estas pruebas antes de cerrar Fase 2:

- Abrir EPUB, avanzar y retroceder, volver a Biblioteca y reanudar en la misma ubicación.
- Cambiar apariencia y brillo, cerrar el lector y comprobar que se restauran.
- Guardar, editar, recolorear y borrar anotaciones; navegar a la ubicación guardada.
- Abrir PDF, pasar páginas, usar desplazamiento continuo, zoom, rotación y salto directo.
- Buscar texto en un PDF con texto seleccionable; cancelar la búsqueda; seleccionar texto, copiar, resaltar y crear una nota.
- Reproducir audio, pausar, buscar, cambiar velocidad, salir y volver a abrirlo; confirmar que no queden notificaciones ni controladores activos después de cerrar.
- Probar lector EPUB, PDF y audio en rotación de pantalla y tras volver desde otra aplicación.

La separación del ViewModel de audio también cancela el sondeo anterior al volver a asociar el controlador y evita emitir el mismo estado de posición si no cambió. La compilación quedó validada por CI; la reproducción, el control de audio y el ciclo de vida todavía requieren prueba manual en dispositivo.
