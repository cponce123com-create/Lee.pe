# Estantería personalizable

La biblioteca ofrece portadas y estantería mixta con libros verticales u horizontales.
El modo personalizado guarda la posición usando el ID estable del libro; mover o
reenlazar un archivo no debe cambiar su identidad visual.

El catálogo de `ShelfDecorationType` contiene 35 adornos WebP: animales, plantas,
objetos literarios, fantasía y separadores. `BookshelfCustomizer` permite elegir
adornos y modificar la disposición. `BookshelfLayout` serializa el diseño en las
preferencias locales y reconcilia los IDs al importar o eliminar libros.

Los 35 adornos tienen siluetas individuales con transparencia y margen de
seguridad. Los detalles de los archivos y las vistas de comprobación están en
[Recortes transparentes de adornos](ORNAMENT_ASSETS.md).

## Decisiones de implementación

- Las portadas y adornos se dibujan con Compose; la apariencia de volumen no requiere un motor 3D.
- La biblioteca funciona sin conexión ni descarga de assets.
- La búsqueda aplica normalización de acentos y palabras en cualquier orden; el índice se reconstruye al cambiar los libros y se reutiliza al escribir.
- Las posiciones, orden y orientación se separan de los archivos originales.
- Las pruebas de disposición están en `BookshelfLayoutTest`; comprueban persistencia y reconciliación.

## Rendimiento

`LibraryBenchmarks.libraryScroll` recorre un catálogo de 60 EPUB de prueba.
Los resultados de emulador son diagnósticos; FPS y tiempos en teléfonos de gama
baja deben medirse y registrarse por dispositivo, sin extrapolar cifras de CI.
