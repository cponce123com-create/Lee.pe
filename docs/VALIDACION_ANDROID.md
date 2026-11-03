# Lee.pe — Matriz de pruebas Android antes de publicar

La compilación GitHub Actions prueba que el APK compila, **no** que estos flujos funcionen en un teléfono físico. No marcar una fila como completada sin evidencia.

## Dispositivos objetivo

| Perfil | Android | Objetivo |
|---|---|---|
| Gama media económica, 4–6 GB RAM | 13 | Apertura EPUB/PDF, memoria y TTS |
| Gama media reciente, navegación por gestos | 15 | PDF con selección y búsqueda del sistema |
| Dispositivo actualizado | 16 | Notificaciones, pantalla inmersiva y permisos |
| Pantalla grande/tablet y modo horizontal | 13+ | Adaptación y accesibilidad |
| TalkBack + letra del sistema grande | 13+ | Acciones legibles, foco y gestos alternativos |

## Casos prioritarios

- [ ] **EPUB sin conexión**. Abrir y continuar tras modo avión, giro y reinicio de proceso.
- [ ] **EPUB con portada**. Importar EPUB 2/3, comprobar cubierta completa en biblioteca y «Retomar»; sin Internet.
- [ ] **EPUB sin cubierta**. Mostrar icono local, no pantalla vacía ni cierre inesperado.
- [ ] **Portada gigante / corrupta**. Sin bloqueo de UI ni ANR, sin superar memoria razonable. Verificar de forma específica el consumo de Readium durante la decodificación.
- [ ] **PDF grande**. Portada local, modo inmersivo, zoom, rotación, salto a página y conservación del progreso.
- [ ] **SAF**. Importación múltiple, permisos persistidos, proveedores con latencia y permisos revocados; copia privada disponible y ausente.
- [ ] **Biblioteca 1.000 / 10.000**. Tiempo para abrir, búsqueda «garcia», filtro, ordenación, scroll rápido y vuelta al lector. Medir p95 del frame, memoria pico y uso de CPU, sin inventar métricas.
- [ ] **Accesibilidad**. TalkBack anuncia cada libro y sus acciones; no se depende solo del color; botones accesibles por toque, navegación con teclado/gestos; letra al 200 %.
- [ ] **Audio y TTS**. Reproducción con pantalla bloqueada, voz local offline, controles multimedia y temporizador.
- [ ] **Respaldo/restauración**. Exportar JSON, reinstalar, reimportar libros, revisar vista previa y restaurar notas sin sobrescribir las nuevas.
- [ ] **Privacidad**. Inspeccionar permisos del APK (sin INTERNET), verificar que no se hacen solicitudes de red para leer, buscar o extraer portadas.

## Criterios de salida

1. Cero crashes reproducibles en lectura básica y recuperación tras reinicio.
2. Sin pérdida de progreso, notas o marcadores en pruebas de actualización.
3. Rendimiento suficiente en teléfono económico con libros realistas y biblioteca extensa; registrar cifras observadas.
4. No cerrar el PR de accesibilidad hasta comprobar TalkBack en dispositivo.
5. La paginación de datos con Room3 + Paging3 es una **decisión pendiente**: el grid ya es lazy, pero la biblioteca completa todavía se observa en memoria. No anunciar soporte comprobado de 10.000 libros sin probarlo.

## Diseño propuesto para paginación real

La versión actual usa Room 3.0.3. Para Paging 3 se necesita `androidx.room3:room3-paging` y registrar `@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)` en el DAO, conforme a la documentación oficial de Room 3. La UI también necesitará `paging-compose` y mover contadores, colecciones y «Retomar» a consultas agregadas separadas; **añadir solo una consulta paginada sin quitar `observeAll()` no reduce la memoria usada**.

La búsqueda que ignora tildes necesita un campo de búsqueda normalizado e indexado con una migración Room segura o una estrategia equivalente. No reemplazarla por `LIKE` básico que regresaría la búsqueda en español.
