# Plan de trabajo — Lee.pe para Android

## Objetivo del producto

Crear un lector nativo Android que permita importar EPUB, PDF y audiolibros desde el teléfono; leer en una vista amplia de página completa; retomar exactamente donde se dejó; y usar las funciones principales sin conexión. La estética tomará como referencia la interfaz futurista de Lee.pe, con controles discretos durante la lectura y herramientas accesibles al tocar la página.

El repositorio actual es una base inicial: Compose y la biblioteca prototipo ya existen, pero los archivos todavía no se renderizan. Este plan marca el orden de implementación y las condiciones para considerar completa cada etapa.

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
- Reemplazar el prototipo de preferencias por Room: libro, URI persistente, formato, metadatos, portada, fecha de importación y estado de lectura.
- Importar con el selector del sistema: EPUB, PDF y formatos de audio definidos para el MVP.
- Leer título, autor y portada cuando el formato los proporcione; permitir corregir metadatos.
- Añadir búsqueda, filtros, orden, recientes, favoritos, colecciones y quitar de biblioteca.
- Manejar URI revocadas, archivos movidos, duplicados y documentos que no se pueden abrir.
- Conservar el comportamiento sin conexión y no pedir acceso general a archivos.

**Se considera lista cuando**
- Un archivo importado permanece en la biblioteca tras cerrar y volver a abrir la app.
- Se puede encontrar, filtrar, quitar y volver a importar sin duplicados silenciosos.
- La app informa claramente cuando el proveedor del archivo revoca el permiso.

### Fase 2 — Lector EPUB

**Trabajo**
- Integrar Readium para abrir EPUB refluibles y fijos compatibles.
- Crear la pantalla de lectura inmersiva: página completa, barras superior e inferior ocultables y modo de desplazamiento configurable.
- Navegar por gesto, capítulo, tabla de contenidos, porcentaje y ubicación.
- Añadir selector de capítulo y control para ir a una posición exacta.
- Guardar y restaurar la ubicación Readium junto al progreso.
- Configurar tamaño y familia de letra, interlineado, márgenes, alineación, temas claro/sepia/oscuro/negro y brillo.
- Añadir orientación, mantener pantalla activa y zona de toque ajustable.

**Se considera lista cuando**
- Se abre un EPUB local sin conexión, avanza y retrocede, y permite saltar desde el índice.
- Al cambiar tipografía o tamaño y reabrir el libro, vuelve al mismo pasaje.
- En teléfono, una página se lee de forma cómoda sin quedar reducida a una tarjeta de pocos párrafos.
- Los controles no tapan el texto y pueden recuperarse con un toque.

### Fase 3 — PDF

**Trabajo**
- Integrar un motor PDF compatible con Android y licencias adecuadas.
- Mostrar una página completa por defecto; ofrecer desplazamiento continuo y ajuste de ancho.
- Ir a número de página, ampliar, reducir, rotar y recuperar la última página.
- Añadir búsqueda y selección de texto cuando el PDF incluya texto seleccionable.
- Identificar PDF escaneados y explicar que necesitan OCR para buscar o narrar.

**Se considera lista cuando**
- Abre PDFs locales sin conexión y la navegación por número de página coincide con el documento.
- El zoom y el desplazamiento funcionan en documentos largos sin cargar todo el archivo en memoria.
- La app distingue búsqueda disponible de documentos escaneados sin OCR.

### Fase 4 — Marcadores, subrayado y notas

**Trabajo**
- Seleccionar texto desde EPUB y PDF cuando el motor lo permita.
- Añadir resaltados con colores, notas y marcadores.
- Guardar anclas estables por locator/página, relacionarlas con libro y perfil, y permitir edición y eliminación.
- Crear una vista de notas con filtros por libro, capítulo y color.
- Exportar notas y citas a TXT/Markdown; definir límites de cita al compartir.

**Se considera lista cuando**
- Las anotaciones reaparecen tras cerrar la app y tras modificar preferencias de lectura.
- El usuario puede ir desde una nota a su pasaje y administrar sus anotaciones.
- Los fallos de anclaje se presentan como contenido no localizado, sin mover la nota a otro texto.

### Fase 5 — Lectura por voz y audiolibros

**Trabajo**
- Añadir narración EPUB con TextToSpeech y soporte de voces Android instaladas.
- Sincronizar voz con párrafo o fragmento, permitir pausar, reanudar, velocidad, temporizador y elegir desde dónde escuchar.
- Reproducir archivos de audiolibro mediante Media3.
- Implementar servicio de reproducción en segundo plano, notificación y controles de pantalla bloqueada, audífonos y Bluetooth.
- Guardar posición de audio y restaurarla.

**Se considera lista cuando**
- TTS y audiolibros funcionan con la pantalla bloqueada y siguen tras cambiar de app.
- El usuario puede pausar/reanudar, cambiar velocidad y usar temporizador.
- Reanudar la lectura por voz no altera inesperadamente la posición visual.

### Fase 6 — Acabado de lectura, accesibilidad y datos locales

**Trabajo**
- Agregar estadísticas locales, tiempo de lectura, rachas y objetivos configurables.
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

## Orden recomendado del MVP

1. Identidad y compilación del proyecto.
2. Biblioteca persistente e importación local.
3. Lector EPUB con página completa y restauración de posición.
4. Navegación, búsqueda, ajustes y marcadores básicos.
5. PDF con salto de página exacto.
6. TTS y reproducción de audiolibros.
7. Anotaciones, accesibilidad, estadísticas y sincronización opcional.

No iniciar la sincronización ni las funciones de IA antes de que biblioteca, EPUB y PDF funcionen localmente. La IA queda fuera del MVP para evitar costos, complejidad y envío innecesario de texto privado.

## Decisiones pendientes para cada módulo

Antes de implementar cada etapa, revisar la documentación del motor y probar la API real con los formatos objetivo. En especial: compatibilidad EPUB del toolkit, motor PDF y OCR, formatos de audio admitidos, guardado de locators, migraciones de Room y requisitos actuales de Google Play. No fijar comportamiento visual o prometer compatibilidad universal si el formato o el DRM lo impiden.
