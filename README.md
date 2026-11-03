# Lee.pe para Android

**Lee.pe** es una aplicación nativa Android para leer y escuchar libros desde el teléfono, con lectura local y sin conexión como objetivo principal.

## Estado del desarrollo

La portada de la biblioteca presenta ahora una interfaz limpia con acciones **Importar** y **Escanear**, con vista de portadas o lista compacta. El escáner busca libros en carpetas elegidas explícitamente por el usuario mediante SAF; trabaja sin internet y muestra una vista previa para confirmar antes de importar. Consulta [Escáner local](docs/ESCANER_LOCAL.md).

La biblioteca permite importar varios EPUB, PDF y archivos de audiolibro en una sola selección con el selector del sistema y conserva sus URI locales. Los EPUB ya se abren con Readium, con índice, avance guardado y acceso para saltar a un porcentaje escrito por el usuario. El menú de apariencia ofrece temas claro, sepia, oscuro y OLED; tamaño y tipografía; interlineado, márgenes, alineación, paginación o desplazamiento; brillo por libro con opción de usar el brillo del sistema y un control para mantener la pantalla encendida. El EPUB se abre directamente a página completa, con las barras del sistema y las herramientas ocultas; un toque recupera los controles. La barra superior es compacta y mantiene las herramientas secundarias en «Más opciones» para no saturar pantallas estrechas.

Los PDF ofrecen además un modo de lectura inmersiva activable: oculta las herramientas para ampliar la página y presenta un botón accesible para recuperarlas. Los PDF locales ya abren en un visor sin conexión con navegación por página, salto a página, restauración de la última página y zoom por gesto o controles, desplazamiento de la página ampliada y rotación en incrementos de 90° y desplazamiento continuo con renderizado bajo demanda. En EPUB ya se pueden guardar, abrir y eliminar marcadores; crear, editar y borrar notas en una posición; y seleccionar texto para copiarlo o guardarlo como resaltado amarillo. Notas y resaltados se persisten en Room mediante una migración que conserva la biblioteca. El PDF permite guardar marcadores y notas vinculadas a una página, editarlas, eliminarlas y volver a esa página. En Android 15 (API 35) o posterior, el modo de selección permite tocar una palabra para copiarla, crear una nota o resaltarla. La búsqueda de texto PDF también está disponible desde Android 15 (API 35): busca localmente página por página, permite saltar a una coincidencia y resalta sus líneas; PDFs escaneados sin capa de texto requieren OCR. En versiones anteriores, Lee.pe explica que esta búsqueda no está disponible. Los resaltados PDF se guardan y se muestran sobre la página, con cuatro colores. En vista de página, con el zoom normal y sin rotación, se puede seleccionar una palabra o arrastrar sobre un rango de texto; requiere Android 15 (API 35) o posterior y que el PDF contenga texto seleccionable. Los audiolibros locales se reproducen con Media3 en un servicio de sesión multimedia, con controles del sistema y notificación para continuar al cambiar de app o bloquear la pantalla. La pantalla permite pausar, buscar en la pista, saltar 15 segundos y elegir una velocidad de 0.75x a 2x. La posición se guarda localmente por archivo y se restaura al volver a abrirlo. En Android 13 o posterior, Lee.pe solicita permiso de notificaciones al abrir el reproductor. El lector EPUB también incorpora narración con Readium y la voz Android: empieza desde el elemento visible, resalta el fragmento leído y mantiene el avance del libro, con pausa, reanudación, velocidad de 0.75x a 2x y temporizador de 15, 30 o 60 minutos. El control de voz permite elegir una voz Android local instalada y guardar la preferencia por libro; las voces que requieren internet no aparecen en la lista. El temporizador de narración se mantiene en el servicio aunque se cierre la pantalla del lector. La narración TTS también usa una sesión multimedia en segundo plano, con controles del sistema y notificación para continuar con la pantalla bloqueada; su código está implementado, pendiente de validación en dispositivo. La reproducción de audiolibros y la narración EPUB en segundo plano, sus controles de sesión y la restauración de progreso están implementadas en el repositorio, pero todavía deben validarse instalando la app y probándola en dispositivos reales. El lector EPUB ahora recrea el motor desde su ubicación persistida tras rotación o recreación de actividad; el temporizador de voz tiene un solo propietario en el servicio multimedia. Ambas mejoras siguen pendientes de pruebas manuales. La biblioteca guarda sus libros en Room y migra automáticamente las URI que ya estaban en preferencias locales. El avance EPUB (locator y porcentaje) y la página PDF se guardan también en Room; las preferencias siguen como respaldo de compatibilidad para posiciones previas. Esta integración queda pendiente de sincronizar y validar en Android Studio. Los ajustes avanzados de EPUB dependen de la API experimental de preferencias de Readium.

Las cubiertas EPUB se extraen del propio libro mediante Readium cuando la tarjeta es visible y se guardan en una caché limitada de memoria; si falta la portada o el archivo está inaccesible, se usa un icono. No requiere Cloudinary. La búsqueda de la biblioteca funciona localmente por título, formato y colección, ignorando mayúsculas y acentos. Incluye borrado rápido, filtros «Por leer» y «Copia en Lee.pe», orden por progreso y títulos en ambas direcciones. Las portadas PDF usan miniaturas de la primera página, generadas solo para libros visibles y almacenadas en una caché pequeña de memoria; EPUB y audio conservan identificadores visuales cuando no hay portada local. La biblioteca incorpora respaldo local exportable y restaurable en formato JSON: guarda títulos, progreso, anotaciones, estadísticas y algunos ajustes sin incluir EPUB, PDF ni audiolibros. Para restaurar en otro teléfono hay que importar primero los libros y confirmar las coincidencias. El archivo **no está cifrado**. Consulta [Respaldo local](docs/RESPALDO_LOCAL.md) para las precauciones y límites.

Consulta el [plan de trabajo](docs/PLAN_TRABAJO.md) para el orden de los siguientes módulos y sus criterios de salida. La [arquitectura offline-first](docs/ARQUITECTURA_OFFLINE_FIRST.md) describe cómo conectar en el futuro Render, Neon y Cloudinary sin depender de ellos para leer.

## Identidad visual y apertura

Lee.pe utiliza el logotipo de libro con audífonos como icono adaptativo de la aplicación, y el símbolo aparece también dentro de un marco fijo en la cabecera de la biblioteca. La pantalla de apertura muestra el logotipo completo sobre fondo azul petróleo. En Android 12 y posteriores se usa la pantalla de inicio del sistema; en versiones anteriores se conserva el fondo de ventana de compatibilidad. La cabecera aplica las zonas seguras para que el logo no quede debajo de la hora ni de los indicadores del sistema.

En la primera instalación se añade una guía EPUB original de Lee.pe a la biblioteca local para probar navegación, apariencia y narración. Se guarda en el dispositivo, se puede quitar y no se vuelve a insertar después de eliminarla.

El selector de narración muestra solo voces Android locales en español y ofrece abrir el instalador de datos del motor TTS del dispositivo. La disponibilidad de Español (México) depende del motor y de sus datos. Hay voces mexicanas de código abierto en Piper, pero integrarlas dentro de Lee.pe requiere un motor de inferencia Android y comprobar la licencia individual de cada modelo; por eso no se descargan ni se distribuyen en esta versión.

## Tecnologías

- Kotlin y Jetpack Compose para la app Android.
- Readium Kotlin Toolkit para EPUB y navegación por locators.
- Android Storage Access Framework para importar archivos con permisos limitados.
- Room para biblioteca, progreso, marcadores/notas EPUB y anotaciones por página PDF, incluidos resaltados persistentes.
- Media3 para audiolibros locales en segundo plano y Readium/Android TTS para narración EPUB; ambas rutas están implementadas en código y requieren validación en dispositivos.
- Render y Neon solo para API y sincronización opcional; Cloudinary para portadas si hace falta. Los libros no se subirán automáticamente.

## Abrir el proyecto

1. Clona este repositorio.
2. Ábrelo en Android Studio compatible con Android Gradle Plugin 9.3.
3. Instala Android SDK Platform 36 y sincroniza Gradle.
   Ejecuta `./gradlew testDebugUnitTest assembleDebug` para pruebas locales y APK de depuración.
4. Ejecuta la configuración `app` en un emulador o dispositivo Android.

## Principios

- La lectura local no depende de internet y el APK no solicita permiso de acceso a Internet.
- El respaldo se crea en el destino que el usuario seleccione, sin pasar por Render, Neon ni Cloudinary.
- Se desactiva la copia automática de Android para proteger biblioteca, notas y progreso local. **Advertencia:** al desinstalar o cambiar de teléfono, estos datos pueden perderse; aún falta implementar un respaldo manual local completo y restaurable. Las copias de libros guardadas en el espacio privado también se borran al desinstalar.
- La posición EPUB se conserva con un locator estructural además del porcentaje.
- La sincronización será opcional y no enviará el archivo original por defecto.
- Las pantallas solo mostrarán funciones implementadas.
- Lee.pe y la sección Lector de Escribiendo se conectarán mediante una API después de estabilizar la experiencia local.

## Licencias

Readium Kotlin Toolkit se distribuye bajo BSD-3-Clause. Conserva sus avisos de licencia al distribuir la aplicación.
