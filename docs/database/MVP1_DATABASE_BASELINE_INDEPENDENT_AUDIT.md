# MVP 1 Database Baseline Independent Audit

## 1. Veredicto

`APPROVED WITH ISSUES`

La baseline `V1__create_manager_schema.sql` es ejecutable, está limpia como primera migración técnica de Flyway, conserva las columnas agregadas por las migraciones históricas `V13` a `V16`, y el backend/frontend validan en verde. No se detectó eliminación de tests ni de código productivo dentro de los tres commits auditados.

No corresponde `APPROVED` porque el soporte de MVP 1 para "exactamente dos atributos especiales por jugador" todavía no está garantizado por flujo productivo ni tests de integración: la DB impide duplicados y slots fuera de `1..2`, pero permite cero o un atributo por jugador y no hay evidencia de importador/servicio productivo que lo complete y valide. Además, varias tablas normalizadas nuevas están preparadas para futuro pero no están conectadas a los consumidores actuales.

## 2. Estado Git

- Working tree inicial de la auditoría: limpio.
- Commits auditados presentes:
  - `939a9113 Design MVP 1 database model and three-league data requirements`
  - `513bf1e3 Create clean MVP 1 database baseline`
  - `63750cf1 Validate MVP 1 database baseline and minimal fixtures`
- `git diff --check 939a9113^..63750cf1`: sin errores.
- Cambios auditados:
  - agregados: dos documentos de base de datos, `V1__create_manager_schema.sql`, `Mvp1DatabaseBaselineContractTest.java`;
  - eliminados: `V13__add_player_height_skills.sql`, `V14__add_division_to_teams.sql`, `V15__add_league_id_to_teams.sql`, `V16__per_league_division.sql`;
  - tests eliminados: ninguno;
  - clases productivas eliminadas: ninguna.

## 3. Inventario de tablas reales

La DB local tiene 26 tablas públicas contando `flyway_schema_history`. La baseline de aplicación crea 25 tablas y Flyway crea la tabla técnica restante.

| Tabla | Propósito | PK | Relaciones/constraints principales | Riesgos |
| --- | --- | --- | --- | --- |
| `countries` | catálogo de países | UUID | `code` y `name` únicos | No exige ISO estricto ni es usado por todos los consumidores actuales. |
| `users` | autenticación/usuario | UUID | `email`, `username` únicos; FK opcional a `teams` | Relación circular con `teams`; resuelta por `ALTER TABLE`. |
| `leagues` | liga/competición | UUID | FK a `countries`, `teams` ganador, `seasons.external_id`; `code` único | `season_id` usa UUID externo mientras `seasons.id` es entero; requiere disciplina de mapeo. |
| `divisions` | divisiones por liga | UUID | FK a `leagues`; únicos `(league_id, code)` y `(league_id, tier)` | No consumida por los flujos principales actuales. |
| `stadiums` | estadios | UUID | FK opcional a `countries`; `capacity > 0` | Preparada para futuro; sin consumidor actual observado. |
| `clubs` | catálogo normalizado de clubes | UUID | FK a `countries`, `stadiums`; único `(source_system, source_id)` | `source_system/source_id` nullable permite múltiples filas no deduplicables si faltan ambos. |
| `teams` | equipo jugable actual | UUID | FK a `users`, `leagues`, `clubs`; checks de presupuesto | Tabla principal usada; no tiene unique por nombre/liga. |
| `seasons` | temporadas | serial int | FK a `leagues`; `external_id` UUID único | `year` es `VARCHAR(20)`, pero `SeasonEntity` declara `int year`. |
| `season_competitions` | relación temporada/liga/división | UUID | FK a `seasons`, `leagues`, `divisions`; unique compuesto | Preparada para futuro; poco o nada consumida. |
| `club_division_memberships` | club por división/temporada | UUID | FK a club, season, league, division; unique `(club_id, season_id)` | No permite que un club participe en dos competiciones de la misma temporada. |
| `players` | jugadores | UUID | FK opcional a `countries`; unique `(source_system, source_id)`; checks atributos `1..99`, energía `0..100`, altura/peso | `height_cm` permite null o `150..220`, pero algunos writers usan fallback `0`, potencial choque si faltara altura. |
| `player_secondary_positions` | posiciones secundarias | UUID | FK a `players`; unique `(player_id, position)` | No hay evidencia de integración productiva actual. |
| `player_attribute_catalog` | metadatos de atributos | `code` | check de rango catálogo | Catálogo metadata; los valores reales siguen en columnas de `players`. |
| `special_attributes` | catálogo de rasgos | UUID | `code` único | Catálogo cargado con datos mínimos en DDL. |
| `player_special_attributes` | rasgos por jugador | UUID | FK a `players` y catálogo; slot `1,2`; únicos `(player_id, special_attribute_id)` y `(player_id, slot)` | No fuerza exactamente dos filas por jugador. |
| `team_squad` | plantilla activa | bigserial | FK a `teams`, `players`; unique `(team_id, player_id)` | Correcta para roster actual; no modela vigencia temporal. |
| `league_teams` | liga-equipo actual | compuesta | PK `(league_id, team_id)` | Conserva relación actual; sin timestamps. |
| `games` | carrera/game entry point | UUID | FK a `users`, `teams` | No modela estado completo de carrera; Redis sigue siendo fuente runtime. |
| `matches` | fixtures/resultados | UUID | FK a `games`, home/away `teams`; checks de round, goles, posesión, tiros | No persiste detalle completo minuto a minuto. |
| `match_events` | eventos básicos | UUID | FK a `matches`; minuto `0..130` | Guarda nombre de jugador, no FK a jugador/equipo. |
| `standings` | tabla de posiciones | serial int | FK a `seasons`, `teams`; unique `(season_id, team_id)` | Duplica columnas `won/drawn/lost` y `wins/draws/losses`; riesgo de inconsistencia. |
| `contracts` | contratos básicos | serial int | FK a `players`, `teams`; duración `1..10` | No hay unique de contrato activo por jugador. |
| `transfers` | transferencias básicas | varchar | FK a jugador/equipos; oferta >= 0 | Requiere equipos origen/destino no nulos; no cubre jugador libre. |
| `player_match_statistics` | stats por partido | UUID | FK a match/player/team; unique `(match_id, player_id)` | No cubre tarjetas, lesiones, pases, xG, stamina final. |
| `player_season_statistics` | stats por temporada | UUID | FK a season/player/team; unique `(season_id, player_id, team_id)` | Preparada, pero no cubre todos los datos del motor. |
| `flyway_schema_history` | técnica Flyway | installed_rank | versión `1`, descripción `create manager schema`, success true | No es tabla de dominio. |

## 4. Comparación esquema anterior / V1

| Elemento anterior | Estado en V1 | Conservado | Modificado | Perdido | Riesgo |
| --- | --- | ---: | ---: | ---: | --- |
| `players.height_cm` de `V13` | Columna en `players`, con check nullable `150..220` | Sí | Sí | No | Fallback productivo `0` puede violar check si un jugador sin altura llega al writer. |
| `players.skill_levels_json` de `V13` | Columna en `players` | Sí | No | No | Sigue como `TEXT`, sin validación JSON en DB. |
| `teams.division` de `V14` | Columna en `teams`, default `PRIMERA`, índice | Sí | Sí | No | No se conserva la redistribución de datos previa porque la DB se recrea limpia. |
| `teams.league_id` de `V15` | Columna en `teams`, FK e índice | Sí | Sí | No | `TeamEntity` no expone `leagueId`; algunos flujos usan SQL directo. |
| Redistribución por liga de `V16` | No aplica a baseline vacía | Parcial | Sí | Datos locales previos no preservados | Aceptable pre-MVP si la DB local es regenerable. |
| Tablas históricas existentes antes de `V13` | V1 recrea tablas activas observadas: users, teams, players, league_teams, games, matches, standings, etc. | Mayormente | Sí | No se detectó pérdida por los commits auditados | El historial anterior completo no estaba en source al rango auditado, por lo que la comparación profunda se hizo contra entidades/queries actuales. |

Conclusión de comparación: la consolidación no elimina tests ni clases productivas, y conserva las columnas de `V13` a `V16`. El riesgo principal no es pérdida por la compactación sino brecha entre el modelo aspiracional nuevo y los consumidores actuales.

## 5. Entidades, repositorios y SQL frente al esquema

Consumidores directos observados:

- `PlayerEntity`, `PlayerR2dbcRepository`, `WorldSeedBatchWriter`: consumen `players`, `team_squad`, `height_cm`, `skill_levels_json`, atributos y energía.
- `TeamEntity`, `TeamR2dbcRepository`, `WorldTeamPostgresWriter`: consumen `teams`.
- `LeagueEntity`, `LeagueR2dbcRepository`, `LeagueTeamR2dbcRepository`: consumen `leagues`, `league_teams`.
- `GameEntity`, `MatchEntity`, `StandingEntity`, `UserEntity`, `TeamSquadEntity`: mapean tablas creadas en V1.
- `DetailedMatchRedisAdapter`, `BaselineStateRedisAdapter` y runtime de carrera mantienen detalle en Redis.

Inconsistencias/riesgos:

- `TournamentEntity` apunta a `@Table("tournament")`, pero V1 no crea `tournament`. No se detectó repository productivo que lo use, por lo que no es crítico inmediato, pero sí es deuda de coherencia.
- `SeasonEntity.year` es `int`, mientras `seasons.year` es `VARCHAR(20)`. Si se usa R2DBC contra esa entidad puede haber conversión frágil.
- `LeagueEntity` no mapea `season_id`, `country_id`, `code`, `tier`, `team_count` ni `rules_json`; V1 los agrega como preparación.
- `TeamEntity` no mapea `league_id` ni `club_id`; el seed usa SQL directo para `league_id`.
- `player_special_attributes`, `player_secondary_positions`, `clubs`, `stadiums`, `season_competitions`, `club_division_memberships`, `player_attribute_catalog` no tienen integración completa observada.

## 6. Modelo de jugador

V1 soporta persistencia básica y útil para MVP 1:

- id estable UUID;
- `source_system` + `source_id`;
- `name`, `display_name`;
- `age`, `birth_date`;
- `country_id`;
- posición principal;
- posiciones secundarias;
- pierna dominante;
- camiseta;
- market value, weekly salary, contract status;
- energía e injured boolean;
- altura, peso;
- attack, defense, technique, speed, stamina, mentality;
- stats por partido y temporada.

Brechas:

- lesiones/suspensiones/moral/forma no tienen modelo relacional rico. Solo existe `players.injured` boolean y `energy`.
- El motor usa detalles de carrera/fixtures en Redis; PostgreSQL no persiste snapshots detallados ni cambios minuto a minuto.
- `skill_levels_json` no tiene validación DB de estructura/rango.
- No hay tabla para historial de stamina/energía por partido.
- No hay relación de jugador con club por temporada salvo `team_squad` actual y `contracts`; no hay vigencia temporal de plantel.

## 7. Dos atributos especiales

Evidencia positiva:

- Existe catálogo `special_attributes`.
- Existe tabla intermedia `player_special_attributes`.
- FK a `players` y `special_attributes`.
- `slot SMALLINT NOT NULL CHECK (slot IN (1, 2))`.
- Unique `(player_id, special_attribute_id)` evita duplicar el mismo rasgo.
- Unique `(player_id, slot)` evita dos rasgos en el mismo slot.

Evidencia negativa:

- La DB permite cero atributos por jugador.
- La DB permite un solo atributo por jugador.
- No se observó importador/servicio productivo que inserte y valide los dos rasgos.
- No se observó integración con motor ni frontend.
- El test contractual solo busca texto en SQL; no ejecuta inserts ni casos inválidos.

Conclusión: soporta la estructura para dos atributos especiales, pero no garantiza "exactamente dos" en comportamiento productivo. Esto impide `APPROVED`.

## 8. Preparación para tres ligas completas

Preparado: parcial.

Capacidades presentes:

- países, ligas, divisiones y temporadas;
- clubes y estadios;
- equipos actuales;
- source ids para clubes/jugadores;
- planteles mediante `team_squad`;
- fixtures/resultados básicos;
- stats iniciales por jugador/partido/temporada;
- volumen esperado razonable: 3 ligas, ~60 clubes, 1080-1800 jugadores, ~2160-3600 rasgos especiales.

Brechas:

- transferencias desde/hacia libres no están modeladas porque `transfers.from_team_id` y `to_team_id` son `NOT NULL`;
- múltiples nacionalidades no están modeladas;
- membership temporal de jugador a club no está modelado más allá de `team_squad` actual/contratos;
- deduplicación con `(source_system, source_id)` permite múltiples `(NULL, NULL)`;
- falta índice en `players(source_system, source_id)` explícito más allá del unique, útil pero no suficiente para patrones parciales;
- no hay integración de importador tres-ligas con validación de exactamente dos rasgos.

## 9. Calidad de la V1 única

Conclusión: baseline correcta pero corregible.

Aspectos correctos:

- Orden de creación consistente, con `ALTER TABLE` para ciclos.
- Repetible sobre DB vacía.
- Sin instrucciones destructivas automáticas.
- Sin datos masivos mezclados con DDL; solo catálogos mínimos.
- Sin referencias V23/V24 en nombres de schema.
- V1 es apropiado para pre-MVP público si no hay DB pública que preservar.

Aspectos corregibles:

- El documento de diagrama contiene mojibake visible en la salida (`â”œ`, `â”€`), probablemente por encoding de caracteres de árbol.
- El baseline mezcla tablas activas con tablas futuras no consumidas; aceptable si está documentado, pero no debe confundirse con funcionalidad ya integrada.
- Algunos defaults/checks pueden chocar con writers existentes (`height_cm = 0` en fallback).

## 10. Test contractual

`Mvp1DatabaseBaselineContractTest` valida:

- presencia textual de tablas principales;
- presencia textual de catálogo de atributos y constraints de slots;
- presencia textual de algunos checks e índices.

No valida:

- ejecución real de Flyway;
- tablas/columnas vía metadata;
- inserts válidos/ inválidos;
- FK reales;
- exactamente dos atributos especiales;
- round trips de repositorios;
- compatibilidad de `SeasonEntity`/`TournamentEntity`;
- ausencia de migraciones pendientes.

Conclusión: útil como guardrail mínimo, pero superficial.

## 11. Investigación 3915 vs 2435

Evidencia actual:

- Archivos de test antes de `939a9113`: 222.
- Archivos de test en `63750cf1`: 223.
- Diferencia: se agregó `Mvp1DatabaseBaselineContractTest.java`.
- Métodos/anotaciones `@Test` antes: 2202.
- Métodos/anotaciones `@Test` actuales: 2205.
- Cambios en `pom.xml` dentro del rango auditado: ninguno.
- Cambios de tests dentro del rango: solo un test agregado.
- XML Surefire actuales: 258 archivos.
- Conteo actual desde XML Surefire: 2435 tests, 0 failures, 0 errors, 4 skipped.

Conclusión: la cifra 3915 anterior era incorrecta, acumulada o provenía de reportes stale/duplicados. No hay evidencia de pérdida de descubrimiento ni de eliminación de cobertura por los commits auditados. La suite real actual, recalculada desde XML después de ejecutar `mvn test`, es 2435.

## 12. Flyway y DB local

Consulta no destructiva:

- `flyway_schema_history`: una fila.
- `version`: `1`.
- `description`: `create manager schema`.
- `script`: `V1__create_manager_schema.sql`.
- `checksum`: `-124542148`.
- `success`: true.
- `flyway:info`: `Schema version: 1`, state `Success`.
- Tablas públicas: 26 contando `flyway_schema_history`.

No se recreó ni borró la base durante esta auditoría.

## 13. Backend

Comandos ejecutados:

- `mvn -q -DskipTests test-compile`: passed.
- `mvn -q test`: passed.

Resultado real desde XML Surefire:

- tests: 2435;
- failures: 0;
- errors: 0;
- skipped: 4;
- XML files: 258.

## 14. Frontend

Desde `front-ciber/project`:

- `npm run build -- --configuration development`: passed.
- `npm run build`: passed.
- `npm test -- --watch=false --browsers=ChromeHeadless`: passed.

Resultado:

- `TOTAL: 1016 SUCCESS`;
- ejecutados: 1016 de 1018;
- skipped: 2;
- failures: 0.

## 15. Runtime smoke

No se repitió un smoke destructivo ni se crearon carreras nuevas. La evidencia documental declara un smoke completo posterior a recrear DB. La auditoría sí verificó que la DB local usada actualmente tiene solo V1 y que tests E2E/backend vuelven a ejercer seeds, carreras y round flows durante la suite.

Limitación: el smoke declarado no quedó como script versionado ni como test automatizado específico de baseline. La evidencia es razonable para cierre manual, pero debería convertirse en test o runbook reproducible.

## 16. Documentación

Los documentos existen y describen el modelo y requisitos tres-ligas. Hay coherencia general con la SQL, con estas salvedades:

- "exactamente dos atributos especiales" está correctamente aclarado en el documento como validación de importador pendiente, pero todavía no está implementado.
- "players with no club" está documentado y DB lo permite por ausencia en `team_squad`, pero `transfers` no modela free-agent moves.
- El diagrama textual del documento principal muestra mojibake en caracteres de árbol.
- La documentación final dice `runtime smoke: passed`, pero el procedimiento no quedó automatizado en repo.

## 17. Hallazgos críticos

Ninguno crítico para compilar, migrar y ejecutar la app actual.

## 18. Hallazgos importantes

1. Exactamente dos atributos especiales no está garantizado por comportamiento productivo. La DB evita duplicados y slots inválidos, pero permite 0/1 rasgos y no hay importador/servicio/test de integración.
2. El test contractual es textual y superficial; no ejecuta Flyway ni prueba constraints reales.
3. Tablas normalizadas nuevas para clubes/divisiones/temporadas/atributos están parcialmente desconectadas de entidades y servicios actuales.
4. `TournamentEntity` referencia tabla `tournament` inexistente en V1. No parece usada por repositorio actual, pero es inconsistencia de modelo.
5. `SeasonEntity.year` (`int`) no coincide con `seasons.year` (`VARCHAR(20)`).
6. `WorldSeedBatchWriter` puede insertar `height_cm = 0` como fallback, incompatible con el check `height_cm BETWEEN 150 AND 220` cuando falte altura.

## 19. Hallazgos menores

1. Mojibake en el diagrama textual de `MVP1_DATABASE_MODEL_AND_BASELINE.md`.
2. Duplicidad en `standings`: columnas `won/drawn/lost` y `wins/draws/losses`.
3. `clubs.source_system/source_id` y `players.source_system/source_id` son nullable; los unique constraints no evitan múltiples entradas sin fuente.
4. Falta modelo relacional para lesiones detalladas, suspensiones, moral y forma.
5. Faltan índices/constraints específicos para algunos futuros flujos masivos, por ejemplo búsquedas por source ids con nulos controlados y planteles por temporada.

## 20. Recomendaciones concretas

1. Agregar un validador/importador productivo que inserte y verifique exactamente dos `player_special_attributes` por jugador, con test de integración.
2. Convertir el test contractual textual en test Flyway real contra DB efímera o Testcontainers, con inserts válidos/ inválidos para FK/check/unique.
3. Alinear `SeasonEntity.year` con el tipo SQL o viceversa.
4. Resolver `TournamentEntity`: crear tabla si se usa o eliminar entidad si es obsoleta.
5. Cambiar fallback de altura faltante a `NULL` o ajustar el check si se acepta desconocido.
6. Decidir si las tablas normalizadas nuevas quedan como preparación explícita o se integran al importador tres-ligas antes de declarar `APPROVED`.
7. Corregir mojibake documental.

## 21. Conclusión

La baseline es una buena compactación pre-MVP: migra limpia, conserva lo relevante de `V13`-`V16`, pasa backend y frontend, y deja una estructura razonable para crecer. El estado final no merece rechazo porque no hay regresión comprobada ni pérdida de tests. Sin embargo, tampoco merece aprobación total: el requisito de dos atributos especiales y la preparación tres-ligas están todavía parcialmente documentados/preparados, no cerrados end-to-end.

## 22. Post-audit remediation status

Veredicto posterior: `APPROVED`.

Correcciones aplicadas:

- `TournamentEntity` fue eliminado porque era una entidad persistente huérfana apuntando a una tabla `tournament` inexistente. No tenía repositorio productivo asociado. El estado de torneo sigue siendo parte del runtime/domain model y no una tabla PostgreSQL MVP 1.
- `SeasonEntity` quedó alineado con `seasons`: la columna canónica es `season_year INTEGER`; el alias ambiguo `year` fue eliminado del baseline.
- `height_cm` quedó alineado entre Java y SQL: `NULL` representa dato desconocido; el rango válido es `160..210`; el writer de seed ya no usa fallback `0`.
- Se agregó `PlayerSpecialAttributeSelectionValidator` para que la importación final de dataset exija exactamente dos atributos especiales existentes y diferentes por jugador antes de escribir datos.
- `Mvp1DatabaseBaselineContractTest` fue reemplazado por una prueba real contra PostgreSQL temporal con Flyway desde DB vacía, validación de metadata, constraints, inserciones válidas, rechazos inválidos, round-trip de temporada y reglas de atributos especiales.
- La documentación principal incluye matriz de conexión de tablas normalizadas y separa explícitamente tablas activas de catálogos/relaciones estructurales para el importador de tres ligas.

Evidencia de cierre:

- Flyway aplica una única `V1__create_manager_schema.sql` sobre una DB PostgreSQL vacía.
- Todas las entidades persistentes restantes apuntan a tablas existentes en V1.
- `tournament`/`tournaments` no existen ni son requeridas por entidades persistentes.
- La preparación para tres ligas queda aprobada a nivel de modelo/base/import validation, sin cargar todavía clubes ni jugadores reales.
