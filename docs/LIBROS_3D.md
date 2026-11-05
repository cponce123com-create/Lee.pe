# Volumen y legibilidad de libros (0.3.5)

El cambio se limita a la representación de los libros. La plantilla del mueble,
las luces, los cajones, los adornos y su colocación no cambian. Tampoco se cambian
la cabecera, los filtros, las medidas del librero ni el número de libros por página.

- Portadas: encuadernación coloreada, canto de papel con líneas finas, tapa inferior
  y sombra. El contenido de la portada EPUB/PDF se conserva; no se genera una imagen
  alternativa ni se altera el archivo original.
- Verticales: páginas superiores en perspectiva, lateral oscuro, lomo con iluminación
  gradual y filetes. El título se mide con la altura y anchura reales del lomo antes
  de rotarlo; desaparece la medida fija de 116 dp que podía dejar letras fuera.
- Apilados: superficie de portada inclinada, lomo frontal sombreado y títulos de
  hasta dos líneas. Dentro de cada pila se reserva un poco más de altura a los libros
  inferiores, conservando la altura total del grupo y la posición de los adornos.
- Los guiones bajos y extensiones habituales se limpian solo al mostrar títulos.
  Identidad, URI, nombre guardado, progreso y anotaciones se conservan.

Los títulos que exceden el espacio físico usan puntos suspensivos, conservando el
nombre completo en la descripción accesible. El tamaño se adapta al lomo; no se
promete que cualquier título largo quepa íntegro en un libro estrecho. Arrastrar,
abrir y girar por doble toque mantienen sus manejadores existentes.

La revisión visual usa capturas del emulador con nombres representativos en español,
además de las pruebas de paginación/arrastre y una prueba de doble toque. El ajuste de
fuente del sistema y la lectura en un teléfono real siguen pendientes de validación.
