## Qué cambia

<!-- Una frase. Si corrige un hallazgo de la auditoría, cita su ID: A-05, P-02... -->

## Hallazgo / tarea

- [ ] Referencia: `A-__` / `T_.__` / ninguno (feature nueva)

## Cómo se verificó

- [ ] `./gradlew testDebugUnitTest :core:domain:test` en verde
- [ ] `assembleDebug` en verde, sin advertencias nuevas
- [ ] Probado en dispositivo/emulador (indicar API y modelo) — si aplica
- [ ] Migración Room con test instrumentado — si aplica
- [ ] Textos en `strings.xml` (ES + EN) — si aplica

## Riesgos y reversión

<!-- Qué puede romper, en qué dispositivos, y cómo se revierte. -->

## Capturas

<!-- Antes/después si hay cambio visual. -->

## Seguridad y privacidad

- [ ] No se exportaron componentes sensibles sin justificación.
- [ ] Los servicios y receptores tienen la protección requerida.
- [ ] No se añadió permiso INTERNET ni acceso general al almacenamiento.
