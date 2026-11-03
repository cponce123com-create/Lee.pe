# Lee.pe — Escáner local de libros y biblioteca moderna

## Escaneo de archivos (no cámara ni OCR)

En la portada de Lee.pe, toca **Escanear** y elige una carpeta con el selector de Android. Puedes seleccionar por ejemplo **Documentos** o una carpeta de libros. El escáner recorre de forma recursiva esa carpeta y subcarpetas accesibles y detecta **EPUB, PDF, MP3, M4A, M4B, AAC, OGG, WAV y FLAC** según su extensión. No descarga contenido ni sube archivos.

- No solicita `MANAGE_EXTERNAL_STORAGE` ni acceso libre a toda la tarjeta SD.
- Emplea Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) y guarda el permiso de **lectura** de la carpeta autorizada por el usuario.
- Trabaja en `Dispatchers.IO` con progreso visible y cancelación.
- Antes de añadir archivos muestra cuántos libros encontró, duplicados, carpetas omitidas y si alcanzó límites de seguridad.
- No crea copias automáticas de los libros; solo añade sus referencias locales a Room.
- Limita cada pasada a 20.000 archivos, 2.000 carpetas y 16 niveles para evitar ciclos, lentitud o proveedores problemáticos.
- Si ya hay documentos de ese mismo proveedor, intenta reconocerlos mediante identidad SAF, aunque su URI de acceso sea distinta.
- Al cambiar permisos o borrar archivos de las carpetas originales, el usuario necesitará volver a autorizar el acceso.

**Importante:** esto no es un escáner de páginas por cámara ni realiza OCR. Digitalizar libros impresos requeriría un módulo distinto y permisos de cámara opcionales.

## Interfaz moderna

La página principal muestra **Mi biblioteca**, búsqueda local, **Retomar lectura**, filtros por estado, controles de colección, ordenación y vista **cuadrícula/lista**. La cabecera cuenta con acciones primarias Importar y Escanear, sin reproducir estanterías de madera; mantiene el enfoque en leer.

El botón **Importar** conserva la selección múltiple de archivos individuales. El botón **Escanear** requiere elegir una carpeta. Ambos métodos respetan la privacidad y no necesitan red, Render, Neon ni Cloudinary.

## Validación antes de fusionar

- [ ] GitHub Actions: `testDebugUnitTest assembleDebug`.
- [ ] Abrir el selector de árbol en Android 13, 15 y 16, conceder permisos, escanear carpetas anidadas y cancelar.
- [ ] Confirmar que las entradas pueden leerse desde la URI que concede `ACTION_OPEN_DOCUMENT_TREE`.
- [ ] Analizar carpetas con PDFs, EPUBs, audios, duplicados y archivos sin extensión.
- [ ] Probar microSD/proveedores de terceros con directorios no accesibles y permisos revocados.
- [ ] Escanear una carpeta de varios miles de archivos y registrar memoria y tiempos reales.
- [ ] Probar vista en lista/cuadrícula, TalkBack, escalado de fuente y orientación.
- [ ] Comprobar que no hay permisos generales de almacenamiento ni conexión a servidores.

## Límites conocidos

El esquema Room todavía observa el catálogo completo; la cuadrícula/lista utilizan `LazyColumn` pero no un `PagingSource`. Paging 3 y una búsqueda totalmente paginada son una fase separada para colecciones muy grandes. El escáner no monitoriza permanentemente las carpetas: se ejecuta cuando el usuario lo solicita.
