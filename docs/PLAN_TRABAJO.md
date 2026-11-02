# Plan de trabajo — Lee.pe para Android

## Objetivo del producto

Crear un lector nativo Android que permita importar EPUB, PDF y audiolibros desde el teléfono; leer en una vista amplia de página completa; retomar exactamente donde se dejó; y usar las funciones principales sin conexión. La estética tomará como referencia la interfaz futurista de Lee.pe, con controles discretos durante la lectura y herramientas accesibles al tocar la página.

Estado actual: biblioteca local con Room e importación SAF (incluida migración de la lista anterior), lector EPUB con Readium, índice, controles de apariencia, opción de mantener pantalla activa y navegación paginada o por desplazamiento, y visor PDF offline con navegación por página, zoom, rotación y modo de desplazamiento continuo. El avance se guarda en Room; apariencia y brillo EPUB se conservan por libro. Falta validar la integración en Android Studio.

## Principios de producto

1. **Local primero:** los archivos y la lectura funcionan sin internet.
2. **Página completa:** el texto ocupa el área disponible como en un lector dedicado; los controles se ocultan durante la lectura.
3. **Navegación precisa:** capítulo, índice, página PDF, búsqueda y posición guardada.
4. **Progreso duradero:** guardar una ubicación estructural, no solo el porcentaje.
5. **Privacidad y costo:** no subir libros completos a Render, Neon ni Cloudinary; la nube será opcional.
6. **Funciones verificables:** no mostrar como disponible algo que aún no se ha implementado.

## Fases y criterios de salida

### Fase 0 — Base del proyecto e identidad

**Trabajo**
- Renombrar producto, paquete Android, identificador de aplicación y preferencias locales a Lee.pe.
- Mantener Kotlin, Compose, Gradle y Readium versionados en un catálogo único.
- Separar presentación, dominio y acceso a datos en paquetes claros.
- Añadir una decisión documentada sobre formatos admitidos y licencias.

**Se considera lista cuando**
- Android Studio reconoce una sola app llamada Lee.pe.
- La compilación de depuración instala y abre en emulador y teléfono.
- No quedan nombres visibles de LumenReader ni referencias de paquete antiguo.

### Fase 1 — Biblioteca local e importación

**Trabajo**
- [x] Importar EPUB, PDF y audio desde el selector del sistema sin acceso general al almacenamiento.
- [x] Conservar URI de documentos y filtrar por formato en la biblioteca local prototipo.
- [x] Reemplazar el almacenamiento de la biblioteca por Room con URI, título, formato, fecha de importación, progreso y campos preparados para locator y colecciones.
- [x] Migrar automáticamente las URI guardadas por la versión anterior y evitar duplicados al importar.
- Importar con el selector del sistema: EPUB, PDF y formatos de audio definidos para el MVP.
- Leer título, autor y portada cuando el formato los proporcione; permitir corregir metadatos.
- [x] Buscar títulos, filtrar por formato y quitar libros de la biblioteca sin borrar el archivo original.
- [x] Detectar URI duplicadas y mostrar un aviso cuando el archivo ya está agregado.
- [x] Exigir permiso persistente antes de guardar una URI y conservar en la migración las referencias antiguas que no se puedan recuperar.
- [x] Añadir favoritos y orden por título, última lectura o fecha de importación.
- [x] Editar el título que muestra Lee.pe sin cambiar el archivo original.
- [x] Crear colecciones locales, asignar o mover libros y filtrar la biblioteca por colección.
- Añadir lectura y edición de autor/portada, y recuperación asistida de URI revocadas.
- Conservar el comportamiento sin conexión y no pedir acceso general a archivos.

**Se considera lista cuando**
- Un archivo importado permanece en la biblioteca tras cerrar y volver a abrir la app.
- Se puede encontrar, filtrar, quitar y volver a importar sin duplicados silenciosos.
- La app informa claramente cuando el proveedor del archivo revoca el permiso.

### Fase 2 — Lector EPUB

**Trabajo**
- [x] Integrar Readium y abrir EPUB compatibles en una vista de página completa.
- [x] Guardar/restaurar el locator Readium por URI de documento en Room, conservar compatibilidad con preferencias anteriores y saltar por porcentaje.
- [x] Integrar navegación por gesto y tabla de contenidos con salto a capítulo.
- [x] Alternar modo inmersivo tocando el contenido o el botón para ocultar controles.
- [x] Configurar lectura paginada o con desplazamiento vertical.
- [x] Mantener la pantalla activa durante la lectura, con preferencia por libro.
- [x] Navegar por gesto, tabla de contenidos y porcentaje.
- [x] Añadir tabla de contenidos navegable.
- [x] Permitir ingresar un porcentaje exacto para saltar a una ubicación EPUB; la paginación numérica fija queda sujeta a la configuración de pantalla y letra.
- [x] Guardar y restaurar la ubicación Readium junto al progreso.
- [x] Añadir apariencia EPUB con temas claro, sepia, oscuro y OLED, tamaño y familia de letra; guardar los ajustes por libro.
- [x] Añadir interlineado, márgenes laterales, alineación, brillo del lector y preferencia de paginación; guardar ajustes por libro.
- Añadir orientación y zona de toque ajustable.

**Se considera lista cuando**
- Se abre un EPUB local sin conexión, avanza y retrocede, y permite saltar desde el índice.
- Al cambiar tipografía o tamaño y reabrir el libro, vuelve al mismo pasaje.
- En teléfono, una página se lee de forma cómoda sin quedar reducida a una tarjeta de pocos párrafos.
- Los controles no tapan el texto y pueden recuperarse con un toque.

### Fase 3 — PDF

**Trabajo**
- [x] Integrar un motor PDF nativo compatible con Android y licencias adecuadas (`PdfRenderer`).
- [x] Mostrar página completa, guardar el avance en Room y recuperar la última página.
- Mostrar una página completa por defecto; ofrecer desplazamiento continuo y ajuste de ancho.
- [x] Ir a número de página y recuperar la última página.
- [x] Ampliar y reducir el PDF con gestos y controles; desplazar la página ampliada y restablecer el zoom.
- [x] Rotar la página PDF en incrementos de 90°.
- [x] Ofrecer desplazamiento continuo entre páginas con renderizado diferido de las páginas visibles.
- [x] Guardar y administrar marcadores y notas vinculadas al número de página del PDF.
- [x] En API 35+, seleccionar una palabra tocándola para copiarla, crear una nota o resaltarla; restaurar el resaltado al volver a la página.
- Añadir búsqueda PDF en documentos que contengan texto seleccionable.
- Identificar PDF escaneados y explicar que necesitan OCR para buscar o narrar.

**Se considera lista cuando**
- [x] Abre PDFs locales sin conexión y permite navegar por número de página.
- [x] El zoom opera sobre la página actual, sin cargar el documento completo en memoria.
- [x] El desplazamiento continuo renderiza páginas visibles bajo demanda, sin cargar el documento completo.
- La app distingue búsqueda disponible de documentos escaneados sin OCR.

### Fase 4 — Marcadores, subrayado y notas

**Trabajo**
- [x] Guardar, listar, abrir y eliminar marcadores EPUB locales desde el lector; persistir locators en Room.
- [x] Añadir entidad/DAO de anotaciones y migración Room 1→2 conservando la biblioteca.
- [x] Crear, editar, abrir y eliminar notas locales ancladas al locator actual del EPUB.
- [x] Seleccionar texto EPUB y ofrecer acciones para copiarlo o crear un resaltado.
- [x] Guardar resaltados EPUB amarillos con locator y texto seleccionado en Room; volver a mostrarlos al abrir el libro.
- [x] Cambiar el color de un resaltado EPUB entre amarillo, azul, verde y rosa; guardar el cambio localmente.
- [x] Añadir notas y marcadores PDF anclados al número de página, con edición, eliminación y navegación de retorno.
- [x] Selección de palabra PDF por toque en API 35+ y acción para copiarla o crear una nota.
- [x] Guardar y mostrar resaltados PDF con locator geométrico y cuatro colores.
- [x] Seleccionar palabra o rango arrastrando en vista de página a zoom normal, en API 35+; guardar y mostrar la selección como resaltado.
- Guardar anclas estables por locator/página, relacionarlas con libro y perfil, y permitir edición y eliminación.
- Crear una vista de notas con filtros por libro, capítulo y color.
- [x] Exportar anotaciones, citas seleccionadas, notas y ubicaciones a un archivo Markdown mediante el selector SAF; el archivo del libro nunca se incluye.

**Se considera lista cuando**
- Las anotaciones reaparecen tras cerrar la app y tras modificar preferencias de lectura.
- El usuario puede ir desde una nota a su pasaje y administrar sus anotaciones.
- Los fallos de anclaje se presentan como contenido no localizado, sin mover la nota a otro texto.

### Fase 5 — Lectura por voz y audiolibros

**Trabajo**
- Hecho en código: narración EPUB mediante Readium y el motor Android TTS; inicia desde el elemento visible del lector.
- Hecho en código: sincronizar voz y lector con resaltado del fragmento hablado y avance de página; pausar/reanudar, velocidad de 0.75x a 2x y temporizador de 15, 30 o 60 minutos.
- Hecho en código: elegir una voz Android local instalada, guardar la preferencia por libro y ajustar la velocidad.
- Hecho: reproducir archivos locales con Media3, progreso, pausa, saltos de 15 segundos y velocidad de 0.75x a 2x.
- Hecho en código: ejecutar Media3 en un MediaSessionService con controles del sistema y notificación para continuar en segundo plano y con pantalla bloqueada; falta validarlo en dispositivo, incluida la solicitud de permiso de notificaciones.
- Hecho en código: guardar la posición local por archivo y restaurarla al abrirlo; falta validarlo en Android Studio y en dispositivo.
- Hecho en código: alojar TTS en un MediaSessionService con notificación, controles del sistema, progreso EPUB guardado cada pocos segundos, temporizador activo aunque el lector se cierre y WakeLock mientras suena. Falta probarlo en Android 13+ y con la pantalla bloqueada.

**Se considera lista cuando**
- Los audiolibros locales continúan al cambiar de app y con pantalla bloqueada, con controles de sesión multimedia del sistema.
- La posición de cada archivo se restaura desde el almacenamiento local.
- TTS comienza desde el elemento visible, resalta el fragmento hablado y actualiza la posición del EPUB; pausa, velocidad, selección de voz local y temporizador funcionan desde el lector.
- TTS continúa con la pantalla bloqueada y responde a los controles multimedia del sistema.
- Validar compilación, permisos, voz y reproducción en dispositivo antes de cerrar la fase.
- El selector manual de voz y la continuidad TTS con la pantalla bloqueada se validan e implementan antes de cerrar la fase.

### Fase 6 — Acabado de lectura, accesibilidad y datos locales

**Trabajo**
- [x] Mostrar resumen local de biblioteca: libros, lecturas en curso, terminados y favoritos.
- Registrar tiempo de lectura, velocidad, rachas y objetivos configurables.
- Añadir diccionario/traducción como función separada, con explicación sobre si requiere conexión.
- Revisar TalkBack, contraste, tamaño de objetivos táctiles, escalado de letra y modo horizontal.
- Mejorar portadas, estados vacíos, errores, carga y recuperación tras cierre inesperado.
- Crear respaldo local exportable de biblioteca, preferencias, progreso y anotaciones.

**Se considera lista cuando**
- Las funciones principales son accesibles sin depender solo del color o de gestos.
- Se puede recuperar la biblioteca y el progreso desde un respaldo local.
- Las estadísticas se calculan desde datos locales y se pueden reiniciar.

### Fase 7 — Sincronización opcional con Escribiendo

**Trabajo**
- Definir contrato de API y autenticación antes de conectar servicios.
- Sincronizar metadatos de biblioteca, posición, preferencias y anotaciones seleccionadas; evitar subir el archivo original por defecto.
- Crear cola local de cambios, reintentos con límites, resolución de conflictos y modo solo local.
- Usar Render y Neon únicamente para API y datos compactos; Cloudinary para portadas si realmente hace falta.
- Permitir enviar manuscritos desde Escribiendo a Lee.pe y mantener separados edición y lectura.

**Se considera lista cuando**
- La app no requiere cuenta ni conexión para leer archivos locales.
- El usuario puede activar/desactivar sincronización y sabe qué datos se sincronizan.
- Las interrupciones de red no bloquean la lectura ni crean duplicados.

### Fase 8 — Estabilización y publicación

**Trabajo**
- Verificar formatos EPUB/PDF/audio con una colección de documentos variados y archivos grandes.
- Revisar consumo de memoria, batería, almacenamiento, rendimiento y accesibilidad en teléfonos de gama media.
- Configurar firma de release, respaldo, privacidad, permisos mínimos, ficha de Play Store y canal de pruebas.
- Revisar avisos de licencia y las reglas de publicación vigentes antes de distribuir.

**Se considera lista cuando**
- Una compilación release se instala limpia y actualiza sin perder biblioteca ni progreso.
- El lector puede usarse en modo avión, y los fallos de archivos inválidos se explican con claridad.
- La política de privacidad coincide con el comportamiento real de la app.

## Endurecimiento tras auditoría técnica — 2 de noviembre de 2026

- [x] Limitar a 4.194.304 píxeles cada bitmap PDF, también en desplazamiento continuo.
- [x] Hacer única la copia temporal PDF, cancelar su copia si se abandona la carga y eliminarla si falla.
- [x] Comprobar el espacio disponible antes y durante la copia temporal.
- [x] Dar al enlace TTS un ciclo explícito de desconexión al detener la voz o salir del lector.
- [x] Ejecutar el build Android en pull requests e instalar explícitamente Android SDK Platform 36, alineado con compileSdk/targetSdk 36.
- [x] Confirmar `assembleDebug` en CI de los pull requests #1 (run 24) y #2 (run 27).
- [ ] Ejecutar lint y pruebas; el entorno local no pudo descargar Gradle y el repositorio no tiene suites de prueba.
- [x] Quitar un libro elimina en una transacción sus notas, marcadores y resaltados de Room; el diálogo informa al usuario.
- [ ] Validar PDF, notificaciones y reproducción TTS en Android 13+, Android 15/16 y dispositivos Samsung/Xiaomi.

Las casillas marcadas indican cambios en el código; la validación en dispositivos reales sigue pendiente.

## Orden recomendado del MVP

1. Identidad y compilación del proyecto.
2. Biblioteca persistente e importación local.
3. Lector EPUB con página completa y restauración de posición.
4. Navegación, búsqueda, ajustes y marcadores básicos.
5. Visor PDF con salto de página exacto.
6. TTS y reproducción de audiolibros.
7. Anotaciones, accesibilidad, estadísticas y sincronización opcional.

No iniciar la sincronización ni las funciones de IA antes de que biblioteca, EPUB y PDF funcionen localmente. La IA queda fuera del MVP para evitar costos, complejidad y envío innecesario de texto privado.

## Decisiones pendientes para cada módulo

Antes de implementar cada etapa, revisar la documentación del motor y probar la API real con los formatos objetivo. En especial: compatibilidad EPUB del toolkit, motor PDF y OCR, formatos de audio admitidos, guardado de locators, migraciones de Room y requisitos actuales de Google Play. No fijar comportamiento visual o prometer compatibilidad universal si el formato o el DRM lo impiden.


## Copias locales sin conexión — noviembre de 2026

- [x] Añadir acción voluntaria «Guardar sin conexión» para crear una copia local privada de EPUB, PDF o audiolibro importado.
- [x] Abrir la copia local cuando existe y conservar el URI original como clave de progreso, notas, marcadores y posición de audio.
- [x] Mostrar disponibilidad sin conexión en la tarjeta y permitir quitar la copia sin quitar el libro.
- [x] Eliminar la copia local al quitar el libro de la biblioteca; el archivo original del proveedor permanece intacto.
- Las copias se guardan en el directorio privado excluido de la copia automática de Android; se eliminan al desinstalar Lee.pe. No se envían a servicios en la nube.
- [ ] Validar en un teléfono con EPUB, PDF y audio de distintos proveedores SAF, incluida falta de espacio, URI revocada, cancelación y reapertura sin conexión.
