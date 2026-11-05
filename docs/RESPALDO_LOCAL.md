# Respaldo local de Lee.pe — formato JSON v1/v2 y sobres cifrados v1/v2

## Objetivo

Exportar y recuperar **metadatos locales**, progreso, anotaciones, estadísticas y ajustes de lectura, sin cuentas ni tráfico de red. La función aparece en Biblioteca → Más opciones → Respaldo y restauración local.

## Qué guarda el archivo

La exportación actual genera un archivo `.leepebackup` seleccionado mediante Storage Access Framework. El JSON v2 se cifra localmente antes de escribirlo. La importación también sigue aceptando JSON UTF-8 antiguos v1 y v2 sin cifrar. Se incluyen: URI original como identificador de referencia, título y formato, fecha de importación, favoritos, colecciones, posición EPUB estructurada, última página PDF, progreso y fecha de lectura, anotaciones y resaltados, sesiones de lectura, meta diaria y ajustes por libro seleccionados (EPUB, narración, PDF y audio).

**No contiene los archivos originales EPUB, PDF, MP3/M4B ni las copias offline privadas.** Tampoco permite recuperar los permisos SAF después de desinstalar la aplicación. Los respaldos nuevos se cifran con AES-256-GCM y una clave derivada mediante PBKDF2-HMAC-SHA256 (600.000 iteraciones en el sobre v2, sal aleatoria de 16 bytes y nonce aleatorio de 12 bytes). La contraseña no se almacena en Lee.pe ni en el archivo. Si se olvida, el respaldo no se puede recuperar. Los JSON históricos v1/v2 permanecen sin cifrar y deben tratarse como datos privados.

## Restauración (no destructiva)

Para un archivo `.leepebackup`, Lee.pe solicita la contraseña antes de mostrar la vista previa. Una contraseña incorrecta y un archivo alterado presentan el mismo error genérico. La contraseña se mantiene solo en memoria durante la operación.

1. Importar al dispositivo los libros correspondientes antes de restaurar.
2. Elegir el JSON v1/v2 histórico o el archivo `.leepebackup` desde el selector de documentos.
3. Leer la vista previa de coincidencias exactas, coincidencias por título/formato y libros sin correspondencia.
4. Confirmar explícitamente la recuperación. Si no hay coincidencias la acción queda deshabilitada.
5. El sistema solo relaciona datos con libros **ya existentes** en Room; no inserta referencias `content://` inaccesibles.
6. La vinculación por título exige coincidencia **única** de título y formato. Si existen varias ediciones con el mismo título o si el nombre fue editado, se omite la coincidencia dudosa; conviene importar o renombrar primero el archivo adecuado.
7. El merge se realiza en transacción de Room: conserva datos locales más recientes, recupera posiciones del respaldo cuando corresponde, no reemplaza notas con el mismo ID, y para sesiones usa el máximo, nunca suma duplicados.
8. Los ajustes se recuperan solo cuando falta la preferencia local. El progreso y los ajustes no deben mezclarse entre libros distintos.

La previsualización puede mostrar libros que queden pendientes. El usuario conserva su JSON y puede importarlo de nuevo después de añadir los archivos que faltan.

## Formato de archivo

```json
{
  "schema": "lee.pe.local-library",
  "version": 1,
  "exportedAt": 1793653200000,
  "dailyGoalMinutes": 30,
  "books": [],
  "annotations": [],
  "sessions": [],
  "settings": {}
}
```

El JSON interno conserva el esquema v1/v2 y no acepta versiones desconocidas. El sobre cifrado tiene una versión propia; la validación limita el archivo a 64 MiB y el número de registros. Estos límites protegen los teléfonos de bibliotecas corruptas o archivos maliciosos; son cuotas **por archivo de respaldo**, no límites al número de usuarios ni a la lectura normal.

### Sobre binario desde Lee.pe 0.3.2

| Campo v2 | Bytes | Descripción |
|---|---:|---|
| Magic | 8 | ASCII `LEEPEBKP` |
| Versión | 1 | `2` |
| Iteraciones | 4 | Entero big-endian; exportación a 600.000 |
| Salt | 16 | Aleatorio para cada exportación |
| IV | 12 | Aleatorio para cada exportación |
| Carga y tag | Variable + 16 | JSON cifrado y autenticación GCM |

Los primeros 41 bytes se autentican completos como AAD. El coste se valida antes
de derivar y se limita a 600.000–1.200.000 para impedir trabajo descontrolado por
un archivo manipulado. Exportar/importar se ejecuta fuera del hilo principal.
Se usa el proveedor PBKDF2 nativo si está disponible y un fallback HMAC-SHA256
compatible con UTF-8 en Android 7. El sobre v1 se sigue leyendo con su coste
original de 120.000 y sin AAD; los archivos existentes no se reescriben.

Los lectores anteriores a **0.3.2 no abren los nuevos sobres v2**. El esquema JSON
interno y la versión del sobre son independientes: un JSON v2 no implica un sobre v2.
La sobrecarga de v2 es 57 bytes, incluida en el límite total de 64 MiB.

## Pruebas necesarias en teléfonos

- Exportar, modificar una nota y restaurar; comprobar que la nota nueva no se reemplaza.
- Desinstalar/reinstalar: importar libros de nuevo, restaurar JSON, comprobar que no hay entradas rotas ni pérdida de anotaciones vinculadas.
- Probar duplicados de título, ediciones diferentes, EPUB frente a PDF, URI revocada, archivo JSON inválido, archivo mayor de 64 MiB y cancelación del selector.
- Probar una exportación cifrada, cancelar el selector de destino, importar con contraseña correcta/incorrecta, alterar el archivo y comprobar el error; confirmar que un JSON v1/v2 anterior sigue restaurándose. Probar más de 1.000 libros: tiempo de vista previa, CPU, memoria y duración de transacción.
- Abrir EPUB, PDF y audio restaurados; comprobar progreso, voz, temporizador y preferencias.

**Limitación importante:** aún no se recuperan archivos ausentes automáticamente; no es un gestor de almacenamiento de libros ni una sincronización en la nube.

## Contraseña y preguntas frecuentes

La exportación exige al menos **8 caracteres**, con confirmación. AES-256-GCM
usa un tag de autenticación de 128 bits. La contraseña solo permanece en memoria
durante la operación; no se guarda en preferencias ni en el respaldo.

- **¿Puedo abrir `.leepebackup` en un editor de texto?** No: es un sobre binario cifrado.
- **¿Qué pasa si olvido la contraseña?** No existe recuperación de contraseña.
- **¿Funcionan los JSON v1/v2 anteriores?** Sí; se detectan automáticamente. Después de restaurarlos, conviene exportar un respaldo cifrado y custodiar o eliminar de forma segura el JSON anterior.
- **¿Se cifran también los libros originales?** No: el respaldo contiene metadatos y anotaciones, no los archivos EPUB/PDF/audio.
