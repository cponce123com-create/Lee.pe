# Seguimiento de la auditoría del 4 de noviembre de 2026

La auditoría aportada es una revisión estática de una instantánea 0.3.0. Sus
notas son valoraciones del revisor; no representan una medición del APK actual.
Esta hoja separa lo implementado de lo que todavía requiere trabajo y evidencia.

## Lote inmediato: lectura por voz y presentación

- [x] Los controles TTS siguen la visibilidad del lector. Iniciar o reanudar la
  narración oculta el panel; tocar el libro permite recuperarlo.
- [x] Se retira el resaltado celeste automático del TTS. Los subrayados guardados
  por el usuario se mantienen en un grupo independiente.
- [x] El seguimiento usa el localizador de palabra de Readium, con la frase como
  alternativa cuando el motor no informa rangos. Pausar permite navegar libremente.
- [x] Índice, búsqueda, anotaciones y navegación manual pausan la narración antes
  de saltar, evitando que la voz arrastre la página al punto anterior.
- [x] El lector y el servicio retienen propietarios independientes del EPUB:
  detener la voz no cierra el libro abierto y salir del lector no corta el audio.
- [x] Pruebas JVM de seguimiento, pausa/reanudación, reintento de navegación y
  cierre del recurso compartido. Su ejecución se acredita con los resultados de CI.
- [x] Contador de páginas encima de la estantería, etiqueta de estado más corta,
  nombres de archivos legibles en los lomos y espacio inferior para la cuadrícula.
- [x] Versión 0.3.1 visible en el menú y nombre del APK con versión y commit.
- [x] `localeConfig` ES/EN, etiqueta del manifiesto mediante recursos y retirada
  de la marca usada incorrectamente como artista de los audiolibros.
- [x] `lintRelease` añadido a la verificación del APK minificado.
- [ ] Escuchar un capítulo largo en el teléfono, con controles ocultos, cambio de
  página/capítulo, pausa desde notificación y retorno desde segundo plano.

## Precisiones frente al código actual

1. `coverageGoals` **sí se ejecuta** en el workflow Android, después de publicar
   el APK. Tiene `continue-on-error`: los objetivos aún no son una puerta obligatoria.
2. Hay pruebas de migración en dispositivo en `feature/library`. La ausencia de
   tests JVM en `core/database` no equivale a ausencia total de pruebas de migración.
3. Los paquetes compartidos dificultan mantener la estructura. Sin embargo,
   `internal` de Kotlin sigue restringido al módulo, no al nombre del paquete.
   Referencia: [visibilidad en Kotlin](https://kotlinlang.org/docs/visibility-modifiers.html).
4. Los datos de tamaño, tiempo y cobertura del informe deben conservar su commit
   y entorno de origen. No se trasladan automáticamente a la versión 0.3.1.

## Siguientes lotes con criterios de aceptación

| Prioridad | Lote | Criterio para cerrarlo |
|---|---|---|
| P0 | Respaldo cifrado v2 | Cabecera con coste KDF y AAD; lectura de v1 preservada; límites contra costes maliciosos; pruebas de contraseña Unicode, datos manipulados y proveedor nativo/fallback; medición en teléfono fuera del hilo principal. |
| P0 | Errores tipados | Dominio/datos sin mensajes destinados al usuario; traducción ES/EN en la UI; cancelación conservada; pruebas de mapeo y errores de respaldo/almacenamiento. |
| P0 | Firma de actualizaciones | Clave de firma persistente configurada fuera del repositorio; huella verificada entre compilaciones. El incremento de versión no resuelve por sí mismo una firma distinta. |
| P1 | Red de seguridad | Probar DAOs/restauración, copia parcial/espacio insuficiente y máquinas de estado de lectores. Cumplir y después exigir los objetivos de cobertura, sin exclusiones para inflar cifras. |
| P1 | Diagnóstico local | Registro acotado sin texto de libros, notas ni URIs completas; manejo de cierres/ANR; exportación iniciada por el usuario; sin permiso de Internet. |
| P1 | Fronteras de módulos | Cambios mecánicos separados de cambios funcionales; preservar componentes públicos y restauración; mover lectores/respaldo/escáner con pruebas antes de reducir las dependencias `api`. |
| P1 | Persistencia | Audio y ajustes por ID estable; importación de preferencias idempotente y recuperable. Una migración SQL sola no importa SharedPreferences: se requiere una fase de datos controlada. |
| P1 | Entrada y herramientas | Abrir/compartir archivos con permisos temporales validados, temporizador de audiolibros y procesamiento de selección por otras apps. Cada entrada necesita pruebas con proveedores SAF. |
| P1 | Validación física | TalkBack, texto al 200 %, voz en segundo plano, teléfono de gama baja y ejecución real del release minificado. |
| P2 | Documentación y escala | Índice único de documentación, archivo histórico sin enlaces rotos y métricas generadas con procedencia. Medir antes de añadir OCR, FTS o nuevas cachés. |

El lote criptográfico debe conservar `core/domain` independiente de Android:
seleccionar el proveedor PBKDF2 por disponibilidad, con fallback probado, en lugar
de introducir `Build.VERSION` en el dominio. El coste propuesto debe contrastarse
con [OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
y con medidas del dispositivo objetivo. Este lote no cambia todavía el formato
de los respaldos ni migra los datos persistidos.
