# MANAGER — MVP 1 Database Baseline Final Independent Audit

Fecha: 2026-07-29

Commit auditado: `2e7df918 Close MVP 1 database baseline audit`

## 1. Veredicto

`APPROVED WITH ISSUES`

El baseline MVP 1 de base de datos está estable para continuar desarrollo: compila, la suite backend completa está verde, Flyway valida contra la base local, el esquema V1 aplica como baseline único y las correcciones principales quedaron implementadas.

No corresponde `APPROVED` pleno porque queda una brecha importante de enforcement funcional: la regla de exactamente dos atributos especiales por jugador está validada en aplicación y protegida parcialmente por constraints de unicidad, pero la base todavía permite persistir cero o un atributo especial si un flujo productivo no pasa por el validador. Además, las tablas normalizadas para el modelo de tres ligas están preparadas estructuralmente, pero todavía no hay evidencia completa de uso runtime/importador end-to-end para todo el dataset objetivo.

## 2. Commits revisados

- `78fd26c2 Fix MVP 1 persistence model inconsistencies`
- `e45d103d Enforce MVP 1 player special attribute requirements`
- `36ce8cf7 Strengthen MVP 1 database integration tests`
- `2e7df918 Close MVP 1 database baseline audit`

Comparación principal revisada: `63750cf1..2e7df918`.

## 3. Estado Git

Antes de crear este reporte, el repositorio estaba limpio.

Este informe es el único archivo agregado por esta auditoría independiente final.

## 4. Esquema V1

Archivo auditado: `src/main/resources/db/migration/V1__create_manager_schema.sql`

Resultado:

- V1 mantiene un baseline único.
- No hay migraciones incrementales adicionales en `src/main/resources/db/migration`.
- `flyway_schema_history` registra una sola migración aplicada:
  - rank: `1`
  - version: `1`
  - script: `V1__create_manager_schema.sql`
  - checksum: `1699405799`
  - success: `true`
- `mvn -DskipTests flyway:validate` contra `football_manager` finalizó con `BUILD SUCCESS` y validó correctamente una migración.

## 5. Inventario de tablas reales

La base local `football_manager` expone 26 tablas públicas incluyendo `flyway_schema_history`.

Tablas reales:

- `club_division_memberships`
- `clubs`
- `contracts`
- `countries`
- `divisions`
- `flyway_schema_history`
- `games`
- `league_teams`
- `leagues`
- `match_events`
- `matches`
- `player_attribute_catalog`
- `player_match_statistics`
- `player_season_statistics`
- `player_secondary_positions`
- `player_special_attributes`
- `players`
- `season_competitions`
- `seasons`
- `special_attributes`
- `stadiums`
- `standings`
- `team_squad`
- `teams`
- `transfers`
- `users`

Tablas de aplicación excluyendo Flyway: 25.

No existen tablas `tournament` ni `tournaments`.

## 6. `SeasonEntity`

Archivo auditado: `src/main/java/com/footballmanager/infrastructure/persistence/entity/SeasonEntity.java`

Resultado:

- La entidad apunta a `seasons`.
- Usa `seasonYear` mapeado a `season_year`.
- No conserva campo ambiguo `year`.
- Incluye `leagueId`, `status`, `startsAt`, `endsAt`, `createdAt` y `updatedAt`.

Base local:

- `seasons.season_year`: `integer`, `NOT NULL`.
- Columna `seasons.year`: inexistente.

Veredicto del punto: correcto.

## 7. `TournamentEntity`

Resultado:

- `TournamentEntity` fue eliminado de producción.
- No hay tabla `tournament`.
- No hay tabla `tournaments`.
- No se detectaron repositorios o entidades persistentes productivas apuntando a esas tablas.

Persisten referencias de dominio/runtime a conceptos de torneo no persistentes, como `TournamentState`, que no son equivalentes a una tabla eliminada y no bloquean este baseline.

Veredicto del punto: correcto.

## 8. `height_cm`

Resultado:

- `players.height_cm` permite `NULL`.
- El constraint de base rechaza valores fuera de rango cuando existe valor.
- La política vigente es `NULL` o rango `160..210`.
- Se eliminó el fallback productivo a `0` en el writer de seed.
- El writer batch propaga `null` cuando no hay altura.
- El path por fila usa bind nulo explícito para `height_cm`.

Tests contractuales cubren:

- `NULL` válido.
- `180` válido.
- `0`, `159` y `211` inválidos.

Veredicto del punto: correcto.

## 9. Dos atributos especiales por jugador

Resultado:

- Existe `PlayerSpecialAttributeSelectionValidator`.
- El validador de aplicación exige exactamente dos códigos existentes y diferentes.
- La tabla `player_special_attributes` contiene:
  - FK a `players`.
  - FK a `special_attributes`.
  - `slot` limitado a `1` o `2`.
  - unicidad `(player_id, special_attribute_id)`.
  - unicidad `(player_id, slot)`.
- El test contractual valida duplicados, slot inválido y referencias inexistentes.
- El test de aplicación valida cero, uno, tres, duplicado y código desconocido.

Hallazgo importante:

La base por sí sola no puede exigir exactamente dos filas por jugador sin un mecanismo adicional, y no se observó todavía un flujo productivo de importación/persistencia que demuestre end-to-end que todos los jugadores persistidos pasan obligatoriamente por el validador. La solución actual es suficiente como protección de aplicación, pero no cierra por completo la invariante ante escrituras directas o futuros writers que omitan el validador.

Veredicto del punto: parcialmente correcto, con issue importante.

## 10. Tablas conectadas y preparación para tres ligas

El baseline contiene estructuras explícitas para:

- países;
- ligas;
- divisiones;
- clubes;
- membresías club-división;
- equipos;
- temporadas;
- competiciones de temporada;
- planteles;
- contratos;
- jugadores;
- posiciones secundarias;
- catálogo de atributos;
- atributos especiales;
- partidos;
- eventos;
- estadísticas de partido;
- estadísticas de temporada;
- standings;
- transferencias.

Esto permite modelar España, Argentina y Brasil con equipos A/B o estructuras equivalentes sin depender de una tabla `tournament`.

Hallazgo importante:

La preparación estructural existe, pero la auditoría no encontró evidencia completa de ejecución importadora end-to-end para todas las ligas objetivo con validación de integridad de dataset completo. Por eso se aprueba la estructura con issues, no como cierre funcional total del dataset.

## 11. Test contractual de baseline

Archivo auditado: `src/test/java/com/footballmanager/infrastructure/persistence/Mvp1DatabaseBaselineContractTest.java`

Cobertura observada:

- aplica Flyway en base PostgreSQL temporal limpia;
- valida tabla `seasons` y columna `season_year`;
- valida inexistencia de `tournament`/`tournaments`;
- valida política `height_cm`;
- valida constraints de `player_special_attributes`;
- valida el validador de exactamente dos atributos especiales;
- valida claves e índices críticos del baseline.

Veredicto del punto: correcto.

## 12. Conteo de tests backend

Suite backend ejecutada:

- comando: `mvn -q test`
- tests: `2439`
- failures: `0`
- errors: `0`
- skipped: `4`
- archivos XML Surefire: `258`

Diferencia contra `63750cf1` por conteo `@Test`:

- antes: `2205`
- actual: `2209`
- diferencia: `+4`
- archivos de test tipo `*Test.java` / `*Tests.java`: `223 -> 223`

La diferencia observada coincide con un fortalecimiento de tests existentes, no con una expansión indiscriminada de archivos.

## 13. Flyway

Validación ejecutada contra la base local:

`mvn -DskipTests flyway:validate -Dflyway.url=jdbc:postgresql://localhost:5432/football_manager -Dflyway.user=postgres -Dflyway.password=...`

Resultado:

- `Successfully validated 1 migration`
- `BUILD SUCCESS`

Veredicto del punto: correcto.

## 14. Base local

Consultas directas contra `football_manager` confirmaron:

- una migración Flyway aplicada;
- 26 tablas públicas incluyendo Flyway;
- 25 tablas de aplicación;
- `seasons.season_year` existe y es `NOT NULL`;
- `seasons.year` no existe;
- `tournament`/`tournaments` no existen;
- existe constraint asociado a `players.height_cm`;
- existen las dos constraints únicas esperadas en `player_special_attributes`.

Veredicto del punto: correcto.

## 15. Runtime smoke

No se modificó ni recreó la base durante esta auditoría.

La evidencia documental previa declara smoke runtime sobre el baseline actual. Esta auditoría final no ejecutó acciones destructivas ni de reseed porque la consigna lo prohibía explícitamente.

Veredicto del punto: sin regresión observada por tests y Flyway; evidencia runtime aceptada como previa, no reejecutada destructivamente.

## 16. Frontend

Validación frontend ejecutada en `front-ciber/project`:

- build development: verde.
- build production: verde.
- tests Karma ChromeHeadless: `TOTAL: 1016 SUCCESS`.
- skipped: `2`.

Veredicto del punto: correcto.

## 17. Documentación

Archivos revisados:

- `docs/database/MVP1_DATABASE_MODEL_AND_BASELINE.md`
- `docs/database/MVP1_THREE_LEAGUE_DATA_REQUIREMENTS.md`
- `docs/database/MVP1_DATABASE_BASELINE_CLOSURE_REPORT.md`
- `docs/database/MVP1_DATABASE_BASELINE_INDEPENDENT_AUDIT.md`

Resultado:

- La documentación describe el baseline actual, el modelo sin `TournamentEntity`, la política de `height_cm` y la preparación de tres ligas.
- No se modificaron documentos existentes durante esta auditoría final.

## 18. Hallazgos críticos

No quedan hallazgos críticos bloqueantes observados.

Evidencia:

- backend completo verde;
- frontend verde;
- Flyway validate verde;
- schema local coherente con V1;
- no existe tabla `tournament`/`tournaments`;
- `SeasonEntity` alineada con `season_year`.

## 19. Hallazgos importantes

1. La regla “exactamente dos atributos especiales por jugador” no está garantizada por la base de datos de forma absoluta. Está garantizada por validador de aplicación y constraints parciales, pero un flujo productivo futuro podría omitir el validador y persistir cero o un atributo.

2. La preparación para tres ligas está bien modelada a nivel estructural, pero falta evidencia importadora/runtime end-to-end sobre dataset completo de España, Argentina y Brasil dentro de esta auditoría.

Estos hallazgos no impiden continuar desarrollo sobre el baseline, pero sí impiden un `APPROVED` sin issues.

## 20. Hallazgos menores

- El validador devuelve la selección como colección sin prometer orden estable. Para la regla actual de dos atributos diferentes esto no rompe comportamiento observable, pero convendría preservar orden si el slot 1/2 queda semánticamente asociado al orden de selección en futuros imports.
- Persisten nombres conceptuales de torneo en dominio/runtime no persistente. No son una regresión del baseline de base de datos, pero conviene mantener clara la diferencia entre concepto de fixture/competición y entidad persistente eliminada.

## 21. Conclusión

El baseline MVP 1 de base de datos queda técnicamente utilizable y estable. Los problemas originalmente críticos sobre `SeasonEntity`, `TournamentEntity`, `height_cm`, Flyway y test contractual fueron corregidos y validados.

El cierre queda en `APPROVED WITH ISSUES` porque la invariante de exactamente dos atributos especiales todavía depende de disciplina de aplicación/importador y porque la preparación de tres ligas no está demostrada end-to-end con dataset completo en esta auditoría.
