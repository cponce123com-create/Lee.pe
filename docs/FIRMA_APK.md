# Firma persistente de actualizaciones

El workflow `Release bundle` exige una clave persistente y comprueba el certificado
del APK antes de subir artefactos. Falla si falta configuración, si el APK no está
firmado o si la huella no coincide. El workflow Android de pruebas sigue generando
APK debug con la clave efímera del runner: esos APK no constituyen una cadena de
actualizaciones con firma estable.

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

Ejecutar `Release bundle` manualmente desde una revisión revisada, o crear una
etiqueta `v*` cuando se decida publicar. El flujo ejecuta pruebas y lint de release,
genera APK/AAB firmados, verifica el APK con `apksigner`, compara la huella fijada y
publica `lee-pe-signed-apk` con `signing.json` (commit, SHA-256 del APK y certificado).
Solo las etiquetas crean/actualizan una release de GitHub. El almacén temporal se
elimina incluso si falla el build; las contraseñas no se escriben en `GITHUB_ENV`.

Para acreditar la firma estable, comparar `certificate_sha256` de dos ejecuciones
sucesivas y probar una actualización conservando progreso y anotaciones. Las
pruebas del script validan rechazo de huellas diferentes/ausentes y de múltiples
firmantes; no sustituyen una ejecución con la clave real.

## Estado de este lote

Preparación y controles implementados. No se ha configurado, leído ni generado
una clave privada ni se ha ejecutado la publicación firmada: la conexión disponible
no permite administrar secretos/variables. La activación y la validación de la
clave real siguen pendientes de la configuración del propietario.
