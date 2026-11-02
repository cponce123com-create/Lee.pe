# Respaldo local de Lee.pe — versión 1

## Objetivo

Exportar y recuperar **metadatos locales**, progreso, anotaciones, estadísticas y ajustes de lectura, sin cuentas ni tráfico de red. La función aparece en Biblioteca → Más opciones → Respaldo y restauración local.

## Qué guarda el archivo

Se genera un archivo JSON UTF-8 seleccionado mediante Storage Access Framework. Se incluyen: URI original como identificador de referencia, título y formato, fecha de importación, favoritos, colecciones, posición EPUB estructurada, última página PDF, progreso y fecha de lectura, anotaciones y resaltados, sesiones de lectura, meta diaria y ajustes por libro seleccionados (EPUB, narración, PDF y audio).

**No contiene los archivos originales EPUB, PDF, MP3/M4B ni las copias offline privadas.** Tampoco permite recuperar los permisos SAF después de desinstalar la aplicación. No es un respaldo cifrado: si el usuario lo guarda en almacenamiento compartido o lo envía a otra persona, esa persona podrá leer sus notas y hábitos de lectura.

## Restauración (no destructiva)

1. Importar al dispositivo los libros correspondientes antes de restaurar.
2. Elegir el JSON local desde el selector de documentos.
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

El archivo no acepta versiones desconocidas; la validación limita el tamaño a 16 MiB y el número de registros. Estos límites protegen los teléfonos de bibliotecas corruptas o archivos maliciosos; son cuotas **por archivo de respaldo**, no límites al número de usuarios ni a la lectura normal.

## Pruebas necesarias en teléfonos

- Exportar, modificar una nota y restaurar; comprobar que la nota nueva no se reemplaza.
- Desinstalar/reinstalar: importar libros de nuevo, restaurar JSON, comprobar que no hay entradas rotas ni pérdida de anotaciones vinculadas.
- Probar duplicados de título, ediciones diferentes, EPUB frente a PDF, URI revocada, archivo JSON inválido, JSON mayor de 16 MiB y cancelación del selector.
- Probar más de 1.000 libros: tiempo de vista previa, CPU, memoria y duración de transacción.
- Abrir EPUB, PDF y audio restaurados; comprobar progreso, voz, temporizador y preferencias.

**Limitación importante:** aún no se recuperan archivos ausentes automáticamente; no es un gestor de almacenamiento de libros ni una sincronización en la nube.
