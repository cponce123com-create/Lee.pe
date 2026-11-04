# Recortes transparentes de adornos

La lámina de referencia contiene 35 adornos. Cada uno se aisló individualmente
con edición de imagen asistida, conservando el conjunto del adorno y restaurando
sus contornos completos. La edición puede reconstruir detalles de la miniatura;
no se garantiza identidad píxel a píxel con la lámina.

## Recursos Android

Los 35 recursos activos están en
`feature/library/src/main/res/drawable-nodpi/shelf_ornament_*.webp`.
Se mantienen los nombres y los IDs de `ShelfDecorationType` para conservar los
diseños de estantería guardados. Los recursos heredados `vase`, `figure` y
`bookend` quedan fuera de esta colección activa.

- WebP RGBA de 512 × 512, calidad 92 y canal alfa de calidad 100.
- Silueta proporcional dentro de 472 × 472 píxeles, centrada horizontalmente.
- Base a 16 píxeles del borde inferior y al menos 20 píxeles de margen lateral.
- Los libros del gato, el perro, los audífonos, la cámara y el conjunto de café
  forman parte del adorno. Se conservan sus bases y accesorios completos.
- Las alas, hojas, estrellas y plumas se incluyen en los límites del objeto.
- `ShelfDecorationArt` utiliza `ContentScale.Fit` y `Alignment.BottomCenter`.

## Comprobación visual

`assets/ornaments-light.webp` y `assets/ornaments-dark.webp` muestran la colección
sobre fondos claro y oscuro. Revisar ambos permite detectar restos del fondo,
halos y elementos cortados. La comprobación de los archivos verifica que todos
se decodifican, contienen píxeles transparentes y opacos, y dejan libre de
contenido el borde exterior completo.
