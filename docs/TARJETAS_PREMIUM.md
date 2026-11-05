# Tarjetas de citas premium — 0.3.6

## Flujo

En EPUB, «Convertir en imagen» ocupa una posición prioritaria de la barra de
selección (`SHOW_AS_ACTION_ALWAYS | WITH_TEXT`), con icono y antes de copiar/resaltar.
No se abre automáticamente el editor ni se publica nada al seleccionar: el usuario
pulsa la acción visible y después confirma Compartir en el selector del sistema.
PDF conserva su acceso directo desde el diálogo de selección/anotación.

El editor muestra una galería de miniaturas con cuarenta fondos y categorías:
literarios, calma, reflexión, melancolía, alegría y fortaleza. Compartir permanece
en la barra inferior. Se mantienen edición del texto y autor y fuentes del sistema.
Hay historia 9:16, cuadrado 1:1 y publicación 4:5, todos a 1080 píxeles de ancho.

## Arte y composición

Se importa intacto el collage JPEG 1536 × 1519 que suministró el usuario. Se guarda
una sola copia como atlas y se selecciona cada región al dibujar; no se regeneran,
retocan ni cortan archivos de imagen con herramientas externas. La imagen decodificada
se comparte mediante una caché blanda para no cargar cuarenta fondos completos.
Las miniaturas se componen solo mientras están visibles.

La frase y el autor usan una zona de lectura con contraste alto. La tipografía se
ajusta al espacio antes de permitir exportar; los textos que no caben se bloquean
con un mensaje, sin truncarlos silenciosamente. El logo oficial y Lee.pe quedan en
la esquina inferior derecha, separados de la atribución. No se imprime el título
del libro ni controles del editor en la tarjeta.

**Límite de calidad:** las celdas originales tienen aproximadamente 145–205 píxeles
de ancho y 249–305 de alto. El PNG final y su texto son nítidos a 1080 px, pero el
fondo se amplía desde esa resolución y no recupera detalle HD. Para una colección
con detalle fotográfico nativo 1080/1920 se necesitan originales individuales de
mayor tamaño; no se presenta el collage como cuarenta imágenes HD independientes.
El ajuste conserva la proporción y centra el recorte en cada formato.

## Validación

Pruebas JVM de cuarenta IDs únicos, regiones válidas sin solapamiento y categorías.
Pruebas instrumentadas de galería, selección de tema, texto largo, bloqueo de texto
excesivo, captura exclusiva de la tarjeta y ejemplos PNG 1080 × 1920 con texto,
autor y logo. La interfaz de selección debe comprobarse también en un teléfono real,
porque su barra pertenece a Android/Readium y puede variar por fabricante.
La estantería, libros, adornos y sus interacciones no se modifican en este lote.


## Diez diseños editoriales aprobados (0.3.7)

La galería suma 50 diseños: conserva los 40 previos y añade Noche dorada,
Marfil botánico, Bosque elegante, Rosa de papel, Piedra y oro, Pergamino vivo,
Luna de tinta, Lino y pluma, Cuero literario y Brote de luz. Los nuevos usan
texto directamente sobre la textura, sin panel ni etiqueta opaca para la marca.
La frase, el autor y el logo oficial se componen en la app y no forman parte del fondo.

Se generó un atlas vacío a partir del boceto aprobado, sin texto ni logos, de
1487×1058 píxeles, en cuadrícula 5×2. Cada región se selecciona proporcionalmente
al dibujar; el archivo original se incluye intacto. No son diez originales HD:
los fondos se amplían, mientras que texto y marca se dibujan a resolución de salida.
La caché carga únicamente el atlas necesario y comparte cada bitmap.

Cinco paletas claras y cinco oscuras, serif por defecto y opción sans serif.
Márgenes relativos reservan las esquinas y los detalles inferiores en 9:16,
1:1 y 4:5. Si el texto no cabe ni al tamaño mínimo, se mantiene el bloqueo
existente de exportación. No se guarda una frase truncada silenciosamente.
Las pruebas recorren los diez diseños en historias y las variantes cuadrada y 4:5;
los ejemplos reales editorial-night y editorial-ivory se guardan en los diagnósticos.
