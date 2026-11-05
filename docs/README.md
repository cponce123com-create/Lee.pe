# Documentación de Lee.pe

Este directorio reúne guías de uso y desarrollo, especificaciones técnicas y registros de auditoría. Para una vista general del producto, sus funciones y su estado, empieza por el [README principal](../README.md).

## Guías de producto

| Documento | Tema |
|---|---|
| [Escáner local](ESCANER_LOCAL.md) | Importación y escaneo mediante el selector de carpetas SAF |
| [Biblioteca 3D](LIBRARY_3D.md) | Estantería, diseño y persistencia |
| [Interacciones de biblioteca](LIBRARY_INTERACTIONS.md) | Portadas, organización mixta y movimiento de elementos |
| [Estantería 3D](LIBRERO_3D.md) | Diseño visual del librero |
| [Libros 3D](LIBROS_3D.md) | Presentación visual de portadas |
| [Recursos de adornos](ORNAMENT_ASSETS.md) | Catálogo y archivos gráficos |
| [Compartir tarjetas](COMPARTIR_TARJETAS.md) | Selección, renderizado y exportación de citas |
| [Tarjetas](SHARECARD.md) | Implementación del módulo de tarjetas |
| [Tarjetas premium](TARJETAS_PREMIUM.md) | Diseños editoriales y especificaciones de exportación |
| [Respaldo local](RESPALDO_LOCAL.md) | Exportación e importación de metadatos |

## Arquitectura y datos

| Documento | Tema |
|---|---|
| [Arquitectura offline-first](ARQUITECTURA_OFFLINE_FIRST.md) | Operación local y tratamiento de datos |
| [Arquitectura modular](ARQUITECTURA_MODULAR.md) | Módulos y dependencias |
| [Arquitectura, fase 2](ARQUITECTURA_FASE2.md) | Evolución propuesta de la estructura |
| [Migraciones](MIGRATIONS.md) | Cambios de esquema de base de datos |

## Compilación y calidad

| Documento | Tema |
|---|---|
| [Compilación](BUILD.md) | Variantes y artefactos |
| [Validación de Gradle](GRADLE_VALIDACION.md) | Comprobaciones del build |
| [Calidad](QUALITY.md) | Lint, pruebas y verificaciones automatizadas |
| [Cobertura de pruebas](COBERTURA_PRUEBAS.md) | Cobertura y límites de las pruebas |
| [Métricas](METRICAS.md) | Métricas del proyecto |
| [Validación en Android](VALIDACION_ANDROID.md) | Pruebas manuales en dispositivos |
| [Firma del APK](FIRMA_APK.md) | Firma de paquetes Android |

## Auditorías y planes

Estos documentos registran revisiones o propuestas en un momento concreto. **No son por sí solos una especificación vigente del producto.** Antes de aplicar una recomendación, confirma la fecha, versión, rama y cambios posteriores.

| Documento | Tipo |
|---|---|
| [Auditoría técnica](AUDITORIA-TECNICA.md) | Hallazgos de una revisión técnica |
| [Auditoría de integración](AUDITORIA_INTEGRACION.md) | Hallazgos de integración |
| [Seguimiento de auditoría](AUDIT_FOLLOWUP.md) | Estado o seguimiento de hallazgos |
| [Plan de mejora](PLAN-MEJORA.md) | Propuestas de mejora |
| [Plan de trabajo](PLAN_TRABAJO.md) | Planificación registrada |

## Recursos visuales

Los archivos gráficos reutilizables están en [assets](assets/). Las capturas reales de la app, cuando estén disponibles, se organizarán en `docs/screenshots/`; el README principal indica qué vistas faltan por documentar.

## Cómo mantener esta documentación

- Actualiza la guía correspondiente cuando cambie una función o su comportamiento.
- Distingue entre “implementado en código” y “validado en dispositivo”.
- Incluye fecha y versión en auditorías, informes y planes que dependan del estado de una rama.
- En caso de discrepancia, contrasta la guía con el código y con las ejecuciones actuales de CI.
