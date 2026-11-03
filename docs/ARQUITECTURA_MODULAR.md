# Arquitectura modular de Lee.pe

## Estado de esta migración

La estructura ya cuenta con módulos de base y dos features en extracción:

```text
:app
├── :core:database   Room, entidades, DAOs, migraciones y esquemas
├── :core:datastore  Fachada tipada sobre las preferencias locales existentes
├── :core:domain     Modelos puros, contratos del lector y validación del progreso
├── :core:ui         Tema Compose y escala de espaciados de Lee.pe
├── :data            Repositorios y almacenamiento offline local
├── :feature:library Biblioteca y scanner local
├── :feature:notes   Exportación Markdown de anotaciones
└── :library:readium Extracción local de portadas EPUB
```

`app` conserva temporalmente las pantallas, Activities, ViewModels, creación manual
del grafo y dependencias directas con Readium y Media3. No se añadieron servicios de
red, permiso `INTERNET`, ni cambios en el formato del respaldo o en las APIs SAF.

`core:datastore` conserva por ahora los nombres de archivo y claves de
`SharedPreferences` mediante `SettingsRepository`. Esto mantiene compatibilidad con
los ajustes y respaldos existentes; aún no es una migración a Android DataStore.
`feature:library` contiene la pantalla y la lógica de biblioteca. La navegación hacia
Activities de lectura/audio se entrega desde `app` mediante una acción, y el respaldo
recibe el repositorio por constructor/parámetro. La pantalla y el selector para exportar
anotaciones siguen en `app`; el formato Markdown ya está en `feature:notes`.
`library:readium` aísla actualmente la lectura de portadas EPUB. El motor completo del
lector EPUB aún permanece en `app` y conserva dependencias Readium directas.

## Reglas de dependencias vigentes

- `:core:database` no depende de `:app` ni de `:data`.
- `:core:datastore` no depende de `:app` ni de `:data`.
- `:core:domain` es Kotlin/JVM puro; no depende de Android, Room ni Compose.
- `:core:ui` solo contiene presentación compartida y Compose.
- `:feature:notes` depende del dominio y encapsula la conversión de anotaciones a
  Markdown; no accede a Room.
- `:feature:library` no depende de otras features ni de `:app`; usa repositorios y
  conserva temporalmente entidades Room en algunos modelos de pantalla. Sus pruebas
  de búsqueda, scanner, respaldo y estadísticas viven junto al módulo.
- `:library:readium` contiene el adaptador de portadas EPUB; no expone tipos Readium en
  su API pública.
- `:data` depende de `:core:database` y `:core:domain`; implementa el acceso local y
  adapta entidades Room a modelos del dominio.
- `:app` ensambla los módulos y mantiene la composición manual actual.
- No crear dependencias entre futuras `feature:*`.

Los paquetes Kotlin existentes se conservaron durante la extracción para evitar
refactorizar imports y no alterar APIs internas mientras se estabiliza el grafo Gradle.
La ubicación física de una clase no debe usarse como sustituto de sus límites de
dependencia: las siguientes extracciones deben mover paquetes y exponer contratos
explícitos.

## Cómo añadir una feature en la siguiente extracción

1. Crear `feature/<nombre>/build.gradle.kts` como Android Library y registrarlo en
   `settings.gradle.kts`.
2. Hacer que la feature dependa de contratos de dominio y `core:ui`; no acceder a DAOs
   ni a otra feature.
3. Mantener Activities, servicios o APIs de terceros detrás de interfaces/adaptadores
   en los módulos correspondientes.
4. Conectar la feature desde `:app`, que conserva la navegación y la composición raíz.
5. Añadir pruebas del módulo y mantener CI para `lint`, pruebas y `assembleDebug`.

## Extracciones que faltan

- Migrar anotaciones, estadísticas, ajustes y biblioteca a contratos/modelos puros de
  `core:domain`; los ViewModels EPUB/PDF ya consumen `ReaderBookRepository` y
  `ReaderAnnotationRepository` y los datos de lectura usan
  `ReadingStatisticsRepository` en vez de repositorios concretos y entidades Room.
- Separar `library:readium` y `library:media3`; eliminar accesos de features a esas APIs.
- Completar la migración de modelos Room fuera de `feature:library`; extraer después
  `feature:reader`, `feature:audiobook` y `feature:settings` con contratos explícitos.
- Centralizar la navegación tipada en `:app` y evaluar Hilt. Se conserva por ahora la
  inyección manual para no combinar cambios de arquitectura con cambios funcionales.
- Migrar preferencias a DataStore solo con una migración comprobable desde las claves
  actuales y con respaldo compatible.

## Validación

Los esquemas Room versionados se guardan en `core/database/schemas/`. CI debe compilar
los módulos, ejecutar pruebas unitarias e instrumentadas y publicar dichos esquemas.
La validación local debe ejecutarse con:

```bash
./gradlew lint test assembleDebug
```
