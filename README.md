# Lee.pe para Android

**Lee.pe** es una aplicación nativa Android para leer y escuchar libros desde el teléfono, con lectura local y sin conexión como objetivo principal.

## Estado del desarrollo

La biblioteca importa EPUB, PDF y archivos de audiolibro desde el selector del sistema y conserva sus URI locales. Los EPUB ya se abren con el navegador de Readium en una vista de página completa. El avance se puede mover con la barra de progreso y la posición estructural del EPUB se guarda para reanudar la lectura.

PDF y audiolibros aparecen en la biblioteca, pero sus visores todavía no están implementados. La biblioteca usa actualmente preferencias locales como almacenamiento inicial; la migración a Room está planificada antes de ampliar las funciones de biblioteca y anotaciones.

Consulta el [plan de trabajo](docs/PLAN_TRABAJO.md) para el orden de los siguientes módulos y sus criterios de salida.

## Tecnologías

- Kotlin y Jetpack Compose para la app Android.
- Readium Kotlin Toolkit para EPUB y navegación por locators.
- Android Storage Access Framework para importar archivos con permisos limitados.
- Room previsto para biblioteca, progreso, marcadores y anotaciones estructuradas.
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
