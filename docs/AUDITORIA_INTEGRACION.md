# Auditoría de integración y validación — 3 de noviembre de 2026

Base inspeccionada: `9bac7b775ca10c8c3d08697b2d9ab1d8031cbefc`.

## Ramas

| Rama | Adelante de main | Detrás |
|---|---:|---:|
| `codex/epub-in-book-search` | 4 | 185 |
| `codex/export-reading-annotations` | 0 | 184 |
| `codex/local-reading-stats` | 0 | 162 |
| `codex/offline-local-copies` | 0 | 190 |
| `codex/p0-pdf-tts-hardening` | 0 | 206 |
| `codex/p1-annotation-cleanup` | 0 | 201 |
| `codex/pdf-in-book-search` | 0 | 172 |
| `codex/phase-a-accessibility-fix` | 0 | 40 |
| `codex/phase-a-localization` | 0 | 50 |
| `codex/phase1-data-corrections` | 1 | 76 |
| `codex/phase2-ci` | 0 | 26 |
| `codex/room-schema-4-kvm` | 0 | 62 |
| `feature/default-treasure-book-20261103` | 0 | 86 |
| `feature/epub-local-covers-accessibility-20261102` | 0 | 103 |
| `feature/immersive-epub-reader-20261102` | 0 | 146 |
| `feature/leepe-branding-icon-splash-20261103` | 0 | 90 |
| `feature/library-performance-local-covers-20261102` | 0 | 115 |
| `feature/local-backup-restore-20261102` | 0 | 130 |
| `feature/modern-library-folder-scanner-20261102` | 0 | 92 |
| `feature/spanish-ui-welcome-voice-20261103` | 0 | 88 |
| `main` | 0 | 0 |
| `mejora/plan-mejora` | 0 | 1 |

Los cuatro commits exclusivos de `codex/epub-in-book-search` son `45c2af8`, `d71a0ab`, `2351f9a` y `3c98dbd`: exportador Markdown, conexión a biblioteca, consulta DAO y documentación. La funcionalidad ya existe en `feature:notes/AnnotationMarkdownExporter`, `feature:library/LibraryDocumentActions` y la biblioteca actual. No se fusiona el antiguo LibraryScreen monolítico.

El commit exclusivo `df2a9a1` de `codex/phase1-data-corrections` habilita KVM. El workflow actual ya contiene la regla udev. No requiere merge. No se eliminan ramas.

## Hallazgos y correcciones

| Prioridad | Hallazgo | Acción / evidencia |
|---|---|---|
| Alta | APK instalada no identificable por origen | Versión 0.3.0 (3), commit y SHA-256 junto al APK de CI. No es posible identificar el APK instalado sin sus metadatos. |
| Alta | Citas largas se exportaban con elipsis | Medición de texto y tamaño adaptable; bloquear exportación si no cabe con tamaño legible. |
| Alta | Captura ampliada desde resolución del teléfono | Componer tarjeta a 1080 px y escalar solo su presentación. Prueba instrumentada exige 1080 × 1920. |
| Media | Acción poco evidente | Etiqueta «Compartir como imagen» y prioridad en menú contextual Readium. |
| Media | Tres plantillas | Seis paletas y fuentes de sistema configurables. Sin descargas. |
| Alta | Tests instrumentados de library fallaban por desugaring | Ya corregido en main al iniciar esta revisión; se conserva. |

## Validación y límites

La ejecución local de `./gradlew --offline clean testDebugUnitTest assembleDebug lintDebug` no completó: el entorno no conserva la distribución y devolvió `Network is unreachable`. La ejecución anterior con proxy confirmó el arranque de Gradle, pero no garantiza disponibilidad tras reinicios del entorno. CI ejecuta clean, lintDebug, testDebugUnitTest, :core:domain:test, assembleDebug, assembleDebugAndroidTest y connectedDebugAndroidTest. El resultado se debe consultar por commit; no se da por aprobado antes de terminar.

No se modifican esquemas de Room, respaldo JSON ni acceso SAF. El FileProvider de tarjetas es no exportado y solo comparte cache/quotecards mediante permiso temporal. La acción EPUB obtiene Locator y autores desde Readium; PDF usa su selección existente. No hay permiso INTERNET en el Manifest principal.

## Pruebas físicas pendientes

- Instalar sobre la versión previa con la misma firma y comprobar biblioteca, notas y progreso. Si Android rechaza la firma, no desinstalar sin respaldo.
- EPUB: mantener pulsada una frase, abrir Compartir como imagen (puede estar en el menú de desbordamiento según Android), seleccionar cada plantilla y compartir a WhatsApp, Facebook e Instagram instalados.
- Probar una frase corta, un párrafo largo, títulos largos, tildes y saltos de línea; verificar que no falta texto.
- PDF con capa de texto: seleccionar, compartir, editar autor. PDF escaneado sin OCR no ofrece selección.
- Importar EPUB/PDF por SAF, revocar acceso y volver a vincular; probar modo avión, escáner y respaldo/restauración.
- TTS y audio: bloquear pantalla, pausa/reanudar, llamada y Bluetooth.
- Revisar logo/splash, continuar leyendo, rotación, TalkBack y fuente grande en teléfono pequeño y tableta.

No se asigna 9,5 ni se publica producción: faltan evidencias físicas, compatibilidad de actualización firmada y rendimiento reproducible.
