# Biblioteca interactiva

La biblioteca usa la identidad de papel crema, verde petróleo y dorado de las
referencias del proyecto. Esta apariencia es independiente del tema del lector.
Las barras del sistema se configuran en MainActivity para acompañar esa pantalla.

## Portadas

- Tres columnas y tres filas por página, con nueve posiciones estables.
- Deslizamiento horizontal entre páginas y contador de página.
- Una página incompleta conserva sus espacios y el tamaño de las portadas.
- Pulsación prolongada y arrastre para ordenar, con vista previa local.
- Mantener el objeto en un borde cambia de página mientras se arrastra.
- Se guarda el orden al soltar; cancelar el gesto descarta la vista previa.
- Las acciones del libro siguen disponibles en su botón de opciones.

## Mixta

- Repisas calculadas antes de dibujar; cada fila visual tiene su propia base.
- Libros verticales y grupos de hasta tres libros horizontales.
- Arrastre directo de libros y adornos, entre posiciones y entre repisas.
- Desplazamiento vertical al mantener el objeto cerca de un borde.
- Durante el arrastre aparece una zona para crear una repisa nueva.
- Doble toque en un libro para cambiar su orientación.
- Tocar un adorno permite sustituirlo o quitarlo.
- Hasta diez adornos colocados simultáneamente, elegidos del catálogo de 35.
- Los diseños antiguos con más adornos se conservan; el límite impide nuevas
  incorporaciones hasta quedar por debajo de diez.

## Integridad

Libros y adornos comparten un único orden de inserción. Guardar, reconciliar y
volver a abrir conserva su intercalado. Los identificadores y la estructura JSON
existente se mantienen. Ordenar portadas filtradas conserva los libros ajenos al
filtro y las posiciones de los adornos. La persistencia se ejecuta al terminar
el gesto, no en cada movimiento del dedo.

## Validación

Las pruebas JVM cubren orden mixto, guardado, cambios de repisa, orden filtrado,
límite de adornos y reparto en filas. Las pruebas Compose comprueban nueve
portadas visibles, navegación lateral, tamaño de la última página y arrastre.
Los resultados de CI deben verificarse para el commit correspondiente. La
implementación requiere revisión visual en teléfono; compilar no demuestra por
sí solo equivalencia con las imágenes de referencia.
