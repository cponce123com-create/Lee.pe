# Migraciones de Room

La versión actual de LeeDatabase es **6**. `ALL_MIGRATIONS` registra la cadena
completa; no se utiliza reconstrucción destructiva como sustituto de migración.

| Transición | Cambio |
|---|---|
| 1 → 2 | Anotaciones e índice por libro/fecha |
| 2 → 3 | Sesiones de lectura por día y libro |
| 3 → 4 | Identidad estable, URI única, colecciones y relaciones muchos a muchos |
| 4 → 5 | Cola persistente `pending_book_cleanup` |
| 5 → 6 | Índice por `bookUri` para consultas de sesiones de lectura |

`LeeDatabaseMigrationTest` comprueba restauración desde versiones 1, 2 y 3.
El caso directo 4 → 5 construye la base desde el esquema 4 exportado y comprueba
identidad, título, favorito, progreso, colecciones y funcionamiento de la cola nueva.
Room valida el esquema al abrir cada base migrada. Ejecutar en API 26 y 35:

```bash
./gradlew :feature:library:connectedDebugAndroidTest --stacktrace
```

Al cambiar entidades, exporta el esquema nuevo, conserva los anteriores y añade
un caso con datos persistidos. No inventes JSON históricos ausentes.
