# Tarjetas de citas

El módulo `feature/sharecard` permite convertir una selección de lectura en una
imagen. Incluye 40 plantillas, con vista previa y datos de la cita. Los textos de
interfaz se verifican en español e inglés mediante `verify_sharecard_locale_parity.py`.

## Uso

Selecciona una frase en el lector, abre la acción de compartir como imagen,
elige una plantilla y revisa la vista previa antes de compartirla.
La exportación usa PNG (`image/png`) y abre el selector nativo de Android.

## Archivos y privacidad

`ComposeShareCardGenerator` renderiza la tarjeta. `ShareCardSharer` guarda un PNG
temporal en `cache/quotecards`, entrega una URI de FileProvider y concede permiso
de lectura temporal a la aplicación elegida. La limpieza elimina archivos con más
de 24 horas al preparar nuevas exportaciones; no es un temporizador en segundo plano.

El proceso es local; la aplicación receptora controla el envío posterior. No hace
falta añadir permiso INTERNET a Lee.pe para exportar una tarjeta.

## Pruebas

Los tests de ViewModel y los instrumentados de pantalla, distribución y generación
comprueban el flujo. Para añadir plantillas, conserva la legibilidad de citas largas,
los márgenes y la paridad de recursos; comprueba el PNG, además de la vista previa.
