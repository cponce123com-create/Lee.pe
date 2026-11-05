# Arquitectura Android offline-first — Lee.pe

> Decisión técnica, 2 de noviembre de 2026. **Este documento define la integración futura: no afirma que la sincronización esté implementada.**

## 1. Regla de producto

El lector abre archivos EPUB, PDF y audio desde `content://` mediante Storage Access Framework (SAF), preservando permisos de lectura; permite crear copias **locales y optativas** para no depender de proveedores externos de archivos. Los contenidos y la reproducción no dependen de una cuenta, Render, Neon o Cloudinary.

- **Android / Kotlin + Compose:** pantallas, comandos y preferencias.
- **Readium:** navegación EPUB y locators durables; no guardar un número de página absoluto como identificador de EPUB.
- **PdfRenderer:** vistas de PDF locales; no renderizar el documento completo en memoria.
- **Room:** biblioteca, progreso, marcadores, resaltados, notas y sesiones.
- **Media3 y TTS Android/Readium:** audio en primer y segundo plano donde sea compatible.
- **WorkManager (pendiente):** cola diferida de sincronización activada por el usuario y condicionada por conectividad.
- **Render (pendiente):** API autenticada de sincronización, sin datos sensibles ni secretos en el APK.
- **Neon (pendiente):** metadatos mínimos, cursores de sincronización, operaciones y anotaciones.
- **Cloudinary (optativo y pendiente):** miniaturas/portadas autorizadas; no alojar libros importados de forma predeterminada.

## 2. Qué se guarda y dónde

| Dato | Android (fuente de lectura) | Servidor opcional |
| --- | --- | --- |
| EPUB, PDF, audio originales | URI SAF o copia privada | **Nunca por defecto** |
| Posición EPUB | Locator JSON + progreso aproximado | Locator JSON tras consentimiento |
| Posición PDF | Número de página (base documentada) | Número de página tras consentimiento |
| Preferencias de presentación | Local, por libro | Optativo |
| Marcadores y resaltados | Room, IDs estables | Optativo, por cuenta |
| Notas | Room | Exportación local optativa, cifrada con contraseña; sin transmisión de red |
| Estadísticas | Room | No habilitar por defecto |
| Portadas | Caché local | Cloudinary, únicamente si hay permiso y necesidad |
| Credenciales | Almacenamiento seguro Android | Tokens revocables con caducidad |

Una referencia `content://` no se puede resolver desde otro teléfono: **no prometer que un libro estará disponible en todos los dispositivos si solo se sincronizaron metadatos**. La recuperación deberá pedir al usuario seleccionar el archivo o descargarlo de un proveedor al que tenga acceso legítimo.

## 3. Diseño de API propuesto (no implementado)

Base: `/api/v1`, HTTPS; acceso autenticado y autorizado **por propietario**. No transmitir secretos de Neon o Cloudinary al cliente Android.

- `POST /sync/changes`: acepta lote de operaciones con `operationId` idempotente y `clientId`, devuelve aceptadas, rechazadas y cursor.
- `GET /sync/changes?cursor=<opaque>`: devuelve cambios paginados del usuario autenticado.
- `POST /sync/ack`: confirma cursor/versión procesados; implementarlo solo si hace falta.
- `DELETE /account/sync-data`: borra los datos remotos del usuario sin tocar su biblioteca local.

Campos sugeridos de cada operación: `operationId`, `entityType`, `entityId`, `bookFingerprint`, `version`, `updatedAt`, `deletedAt` y `payload` validado; sin URL de archivo. Usar un identificador de libro derivado de metadatos estables y/o huella de contenido, calculado localmente, evitando exponer rutas personales. Tratar las colisiones explícitamente.

### Resiliencia

1. Opt-in de cuenta y sincronización; desactivada inicialmente.
2. Se registra en Room el cambio local y una operación pendiente en **la misma transacción**.
3. Enviar lotes pequeños solo con conexión, reintentos con backoff exponencial y límites.
4. El servidor valida propiedad, tamaño, esquema, cuotas y unicidad de `operationId`.
5. Al conflicto, no sobreescribir anotaciones sin avisar. Usar revisiones y conservar ambos contenidos cuando la combinación automática no sea segura.
6. La app sigue leyendo incluso con timeout, Render en reposo, sesión expirada o error HTTP.
7. Respetar borrados mediante tombstones durante una ventana de retención definida para impedir reapariciones.

### Privacidad y licencias

No enviar textos íntegros ni fragmentos subrayados a proveedores de IA sin una confirmación específica y transparente; ofrecer función local como predeterminada. No descargar ni distribuir obras con derechos sin autorización. El usuario puede exportar y borrar sus notas. Las portadas y demás recursos alojados necesitan derechos adecuados y límites de tamaño.

## 4. Requisitos de aceptación antes de conectar producción

- Modo avión: abrir, paginar, anotar, narrar con voz sin red instalada y cerrar/reabrir.
- Reabrir después de cambiar letra/márgenes sin perder el pasaje.
- Cambiar de libro y pantalla bloqueada: audio y progreso no se mezclan.
- Cuenta ausente, backend dormido o API indisponible: **no bloquear nunca el lector**.
- No hay llamadas automáticas que transmitan EPUB/PDF o texto resaltado.
- Restauración en segundo dispositivo sin archivo: mostrar metadatos y pedir localizar el libro, nunca una página en blanco.
- Probar conflictos, borrados, datos de otros usuarios y rotación de tokens antes de habilitar sync.

## 5. Coste operacional

Con Render/Neon/Cloudinary gratuitos, limitar llamadas, volumen de payloads, frecuencia y almacenamiento. Los límites cambian: verificar los planes vigentes antes de publicar. No usar el servidor para renderizar páginas, convertir todos los PDF ni generar TTS por cada lectura. Toda funcionalidad de IA externa debe tener presupuesto, límites, protección contra abuso y exclusión voluntaria.
