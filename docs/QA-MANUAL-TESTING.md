# QA manual en dispositivos Android

Registra el commit, versionName/versionCode, fecha, modelo, firmware, API, motor y voz TTS y proveedor de archivos. Usa EPUB, PDF con texto, PDF escaneado y audiolibro local de prueba. Ejecuta con modo avión tras instalar los datos de voz. Las columnas de fabricante se prueban además de las columnas por API y deben indicar la API real.

Estados: **P** pendiente, **OK** aprobado, **F** fallo, **N/A** fuera del alcance con motivo.

| ID | Caso y resultado esperado | Android 7 / API 24 | Android 13 / API 33 | Android 15 / API 35 | Xiaomi / MIUI o HyperOS | Samsung / One UI |
|---|---|---|---|---|---|---|
| TTS-01 | Leer EPUB con voz instalada sin red. Pausar y continuar conserva la posición y el resaltado. | P | P | P | P | P |
| TTS-02 | Cambiar de voz, bloquear pantalla 10 min y regresar. Comprobar reproducción según los ajustes y que no se duplique la narración. | P | P | P | P | P |
| TTS-03 | Probar sin datos de voz y con interrupción por otro reproductor. Hay un mensaje o estado recuperable. | P | P | P | P | P |
| M3-01 | Reproducir audiolibro 15 min en segundo plano y con pantalla bloqueada. Validar controles del sistema y posición al volver. | P | P | P | P | P |
| M3-02 | Pausar/reanudar desde auriculares y notificación. Desconectar auriculares y simular pérdida de foco de audio. Comprobar transición sin reproducción duplicada. | P | P | P | P | P |
| M3-03 | Repetir M3-01 con ahorro de batería y tras quitar la app de Recientes. Registrar política del fabricante y si la app recupera el progreso al reabrir. | P | P | P | P | P |
| PDF-01 | Seleccionar texto en PDF compatible y verificar que la selección coincide al ampliar. Comprobar acciones de copiar y resaltar cuando estén disponibles. | N/A, API inferior a 35 | N/A, API inferior a 35 | P | P si API ≥35 | P si API ≥35 |
| PDF-02 | Abrir PDF escaneado y PDF con texto en API anterior a 35. La lectura funciona y la ausencia de selección se maneja sin cierre ni selección ficticia. | P | P | P, PDF escaneado | P | P |
| SAF-01 | Importar desde Documentos y almacenamiento extraíble cuando exista. Cerrar y reiniciar el dispositivo. El URI autorizado sigue abriendo el libro. | P | P | P | P | P |
| SAF-02 | Cancelar el selector y retirar el acceso al archivo de prueba, renombrándolo o desmontando el volumen. La app informa el problema y permite volver a seleccionar. | P | P | P | P | P |
| SAF-03 | Exportar respaldo a una ubicación elegida y restaurarlo con el selector. Comprobar contenido y manejo de cancelación. | P | P | P | P | P |
| SAF-04 | Revocar el permiso de un EPUB y un PDF con progreso guardado y abrirlos desde Continuar leyendo. Verificar el aviso de acceso y volver a vincular el archivo original. Comprobar que se recupera la posición guardada. | P | P | P | P | P |

Para Xiaomi y Samsung, ejecuta primero con los ajustes de batería predeterminados y repite con ahorro activado. Usa un perfil sin restricciones únicamente para aislar un fallo y registra el cambio. Distingue quitar de Recientes de forzar detención.

Para cada fallo adjunta pasos, tiempo hasta el fallo, resultado observado y evidencia sin datos privados. La validación de actualización requiere dos APK firmados con el mismo certificado y debe comprobar que se preservan libros, progreso y anotaciones.
