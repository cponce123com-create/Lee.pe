# Librero 3D — referencia del usuario

Se integra el mueble vacío suministrado por el usuario, sin regenerarlo ni cambiar
sus píxeles. Aunque el archivo adjunto se llamaba PNG, su contenido era JPEG:
`library_cabinet_3d.jpg` conserva los bytes originales, 1024 × 1536, en drawable-nodpi.
No depende de Internet. El fichero con libros se usa como guía de composición;
las portadas visibles siempre proceden de los documentos reales del usuario.

`Cabinet3d` conserva la proporción 2:3 y ajusta el mueble al espacio disponible,
sin recortarlo. Portadas, lomos y adornos usan el mismo espacio de coordenadas;
los tres suelos coinciden con la plantilla. Los cambios de ancho no estiran las
portadas. El paginador es interactivo y se sitúa entre los cajones.

- La biblioteca vacía muestra también el mueble original, con un acceso discreto
  al selector de archivos. Una búsqueda sin resultados permite restablecer filtros.
- Portadas: tres columnas y tres filas, nueve libros por página; la última página
  conserva sus huecos. Deslizamiento horizontal, botones anterior/siguiente y
  arrastre para ordenar, también al borde para pasar de página.
- Mixta: tres repisas por página; los grupos apilados y adornos se apoyan en los
  suelos del mismo mueble. Se preserva el límite de diez adornos, la ordenación
  persistida y el doble toque para cambiar la orientación.
- Tema marrón/dorado exclusivo de biblioteca, con verde para el selector. El
  lector conserva sus ajustes independientes. Buscar se despliega desde la lupa;
  importar permanece accesible y las estadísticas pasan al menú.
- No se dibujan como botones falsos las secciones ni notificaciones del boceto
  que aún no existen en la aplicación. Se conserva el logo propio suministrado.

La coincidencia del mueble está garantizada por usar el archivo original. La
composición completa depende de las portadas, el tamaño de pantalla y el texto
accesible: no se afirma identidad píxel a píxel de toda la pantalla del boceto.
La comparación visual se hace con capturas del emulador, además de probar páginas
incompletas y arrastre. TalkBack, escalado de texto y teléfono real siguen siendo
validaciones físicas pendientes.
