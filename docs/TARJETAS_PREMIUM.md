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
