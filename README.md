# LumenReader Android

Aplicación Android nativa para leer y escuchar libros desde el dispositivo. El nombre del repositorio es **Lee.pe** y la aplicación se presenta como **LumenReader**.

## Dirección del producto

La lectura debe funcionar sin conexión: los archivos se eligen desde el teléfono y permanecen bajo el control del usuario. Los servicios en la nube se agregarán después, de forma opcional, para sincronizar progreso y respaldos.

## Stack previsto

- Kotlin y Jetpack Compose para la aplicación nativa.
- Readium Kotlin Toolkit para la publicación y navegación EPUB.
- Android Storage Access Framework para seleccionar archivos sin pedir acceso general al almacenamiento.
- Room para biblioteca, progreso, marcadores y anotaciones estructuradas.
- Android TextToSpeech para narración EPUB.
- Media3 para audiolibros.
- Integración futura con Escribiendo mediante API, cuando la base local esté consolidada.

## Estado inicial

La primera entrega prepara el proyecto Android y la biblioteca local. Las siguientes etapas completarán el lector EPUB con página completa, el visor PDF, el audio, las anotaciones y la sincronización. La interfaz no debe presentar funciones como disponibles antes de que estén implementadas.

## Abrir el proyecto

1. Clona este repositorio.
2. Ábrelo en una versión reciente de Android Studio que admita Android Gradle Plugin 9.3.
3. Instala Android SDK Platform 37 y acepta la sincronización de Gradle.
4. Ejecuta la configuración `app` en un emulador o dispositivo Android.

## Decisiones de arquitectura

- El contenido de los libros no se sube automáticamente a Render, Neon ni Cloudinary.
- Los documentos importados se abren mediante el selector del sistema y conservan permisos locales.
- La sincronización será opcional y enviará solo los datos necesarios (por ejemplo, posición y anotaciones).
- El lector EPUB y el lector PDF se encapsularán detrás de interfaces de dominio para permitir evolucionar motores sin acoplar toda la app.

## Licencias y atribución

Readium Kotlin Toolkit se distribuye bajo BSD-3-Clause. Consulta sus avisos de licencia antes de distribuir una compilación que lo incluya.
