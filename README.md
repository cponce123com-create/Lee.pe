# Lee.pe para Android

**Lee.pe** es una aplicación nativa Android para leer y escuchar libros desde el teléfono, con lectura local y sin conexión como objetivo principal.

## Estado del desarrollo

La biblioteca importa EPUB, PDF y archivos de audiolibro desde el selector del sistema y conserva sus URI locales. Los EPUB ya se abren con Readium, con índice y avance guardado. El menú de apariencia ofrece temas claro, sepia, oscuro y OLED; tamaño y tipografía; interlineado, márgenes, alineación, paginación o desplazamiento; y brillo por libro con opción de usar el brillo del sistema. Al tocar el texto se ocultan o recuperan los controles.

Los PDF locales ya abren en un visor sin conexión con navegación por página, salto a página y restauración de la última página. El reproductor de audiolibros sigue pendiente. La biblioteca ahora guarda sus libros en Room y migra automáticamente las URI que ya estaban en las preferencias locales. El avance EPUB (locator y porcentaje) y la página PDF se guardan también en Room; las preferencias siguen como respaldo de compatibilidad para posiciones creadas antes de esta migración. Esta integración queda pendiente de sincronizar y validar en Android Studio. Los ajustes avanzados de EPUB dependen de la API experimental de preferencias de Readium.

Consulta el [plan de trabajo](docs/PLAN_TRABAJO.md) para el orden de los siguientes módulos y sus criterios de salida.

## Tecnologías

- Kotlin y Jetpack Compose para la app Android.
- Readium Kotlin Toolkit para EPUB y navegación por locators.
- Android Storage Access Framework para importar archivos con permisos limitados.
- Room para biblioteca local y campos de progreso; marcadores y anotaciones estructuradas se conectarán en fases posteriores.
- Android TextToSpeech para narración y Media3 para audiolibros, en fases posteriores.
- Render y Neon solo para API y sincronización opcional; Cloudinary para portadas si hace falta. Los libros no se subirán automáticamente.

## Abrir el proyecto

1. Clona este repositorio.
2. Ábrelo en Android Studio compatible con Android Gradle Plugin 9.3.
3. Instala Android SDK Platform 37 y sincroniza Gradle.
4. Ejecuta la configuración `app` en un emulador o dispositivo Android.

## Principios

- La lectura local no depende de internet.
- La posición EPUB se conserva con un locator estructural además del porcentaje.
- La sincronización será opcional y no enviará el archivo original por defecto.
- Las pantallas solo mostrarán funciones implementadas.
- Lee.pe y la sección Lector de Escribiendo se conectarán mediante una API después de estabilizar la experiencia local.

## Licencias

Readium Kotlin Toolkit se distribuye bajo BSD-3-Clause. Conserva sus avisos de licencia al distribuir la aplicación.
