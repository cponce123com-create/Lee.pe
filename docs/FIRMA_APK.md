# Firma persistente de actualizaciones

El workflow `Release bundle` usa una clave persistente cuando la configuración
de firma está completa y verifica el certificado del APK. Si no hay ninguna
configuración de firma, genera un APK de prueba con firma debug y lo publica como preliminar. Una configuración parcial detiene el flujo.

## Configuración del repositorio

En GitHub → Settings → Secrets and variables → Actions, conservar estos valores:

| Tipo | Nombre | Contenido |
|---|---|---|
| Secret | `LEE_PE_KEYSTORE_BASE64` | Base64 del almacén de claves privado existente |
| Secret | `LEE_PE_KEYSTORE_PASSWORD` | Contraseña del almacén |
| Secret | `LEE_PE_KEY_ALIAS` | Alias de la clave |
| Secret | `LEE_PE_KEY_PASSWORD` | Contraseña de la clave |
| Variable | `LEE_PE_SIGNING_CERT_SHA256` | SHA-256 del certificado, con o sin dos puntos |

La clave privada y las contraseñas nunca se guardan en Git ni en una conversación.
Mantener una copia privada del almacén y sus credenciales fuera de GitHub. Para
consultar la huella de la clave existente, usar `keytool -list -v -keystore RUTA -alias ALIAS`
y escribir la contraseña en el prompt, sin ponerla en la línea de comandos.

No sustituir una clave de distribución existente por otra nueva: la misma
aplicación solo admite actualizaciones con una firma compatible. Esta configuración
no recupera la clave debug de runners anteriores ni vuelve compatibles APK ya
firmados con certificados distintos. Conservar los respaldos antes de cualquier
cambio de instalación; no hace falta desinstalar para comprobar una huella.

## Publicación y comprobación

Para publicar, actualiza `version.properties` y empuja una etiqueta `vMAJOR.MINOR.PATCH`
que coincida con `versionName`. El flujo publica el APK, `version.properties`
y `SHA256SUMS`. Con firma también incluye el AAB y `signing.json`. Sin firma,
publica una versión preliminar con `lee-pe-debug-preview.apk`.

Después de descargar los assets, ejecuta `sha256sum --check SHA256SUMS` en su
directorio. La verificación de firma comprueba la huella del certificado fijada
en el repositorio. El keystore temporal se elimina aunque falle el build.

Para acreditar la firma estable, comparar `certificate_sha256` de dos ejecuciones
sucesivas y probar una actualización conservando progreso y anotaciones. Las
pruebas del script validan rechazo de huellas diferentes/ausentes y de múltiples
firmantes; no sustituyen una ejecución con la clave real.

## Estado de este lote

Preparación y controles implementados. No se ha configurado, leído ni generado
una clave privada ni se ha ejecutado la publicación firmada: la conexión disponible
no permite administrar secretos/variables. La activación y la validación de la
clave real siguen pendientes de la configuración del propietario.
