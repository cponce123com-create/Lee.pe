# Lee.pe para Android

**Lee.pe** será un lector nativo de libros para Android, diseñado para leer en una página completa, escuchar contenido y continuar sin conexión.

## Objetivo

Construir una biblioteca personal que importe libros del dispositivo y ofrezca una experiencia de lectura amplia, cómoda y configurable. La app funcionará localmente primero; la sincronización con Escribiendo será opcional y posterior.

## Tecnologías

- Kotlin y Jetpack Compose para la app Android.
- Readium Kotlin Toolkit para navegación de publicaciones EPUB.
- Android Storage Access Framework para importar archivos sin acceso general al almacenamiento.
- Room para la biblioteca, progreso, notas y marcadores estructurados.
- Android TextToSpeech para narración y Media3 para audiolibros.
- Render y Neon solo para API y sincronización opcional; Cloudinary para portadas u otros recursos, nunca para subir libros automáticamente.

## Estado actual

El repositorio contiene la configuración inicial de Android, un tema visual oscuro y una biblioteca prototipo que importa archivos y guarda sus URI localmente. Todavía no abre ni renderiza EPUB, PDF o audio. La implementación se organizará según el [Plan de trabajo](docs/PLAN_TRABAJO.md).

## Abrir el proyecto

1. Clona este repositorio.
2. Ábrelo en una versión reciente de Android Studio compatible con Android Gradle Plugin 9.3.
3. Instala Android SDK Platform 37 y sincroniza Gradle.
4. Ejecuta la configuración `app` en un emulador o dispositivo Android.

## Principios

- La lectura y la biblioteca deben seguir disponibles sin internet.
- No se carga el contenido de los libros al servidor.
- La ubicación de lectura se guarda como una referencia estructural además del porcentaje, para que sobreviva a cambios de tamaño de letra.
- Las pantallas solo mostrarán funciones que ya estén implementadas.
- La app Lee.pe y la sección Lector de Escribiendo serán productos conectados por una API; no serán dos lectores independientes con formatos de datos incompatibles.

## Licencias

Readium Kotlin Toolkit se distribuye bajo BSD-3-Clause. Conserva sus avisos de licencia al distribuir la aplicación.
