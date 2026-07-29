# MANAGER — MVP 1 Three-League Dataset Final Independent Audit

Fecha: 2026-07-29

Commit auditado: `ad34a103 Complete MVP 1 three-league runtime acceptance`

## 1. Veredicto

`APPROVED WITH ISSUES`

El pipeline de dataset MVP 1 para tres ligas está implementado y validado en tests: importa España, Argentina y Brasil en una base temporal, genera 70 clubes, 70 equipos, 1680 jugadores, 3360 relaciones de rasgos especiales, prueba idempotencia, prueba rollback ante conflicto y permite crear carreras con auto-select en las tres ligas.

No corresponde `APPROVED` pleno porque la auditoría encontró issues importantes:

- los archivos fuente de clubes no contienen jugadores; los 1680 jugadores son generados por código, no versionados como dataset explícito;
- la base local principal `football_manager` no contiene el dataset de tres ligas importado;
- `git diff --check 2e7df918..ad34a103` detecta una línea vacía extra al final de `ThreeLeagueDatasetImporter.java`;
- el importador está en application pero depende directamente de `JdbcTemplate`, Spring `@Transactional` y JDBC, lo que es práctico para la tarea pero no es una separación hexagonal limpia;
- la clase `ThreeLeagueDatasetImporter` concentra lectura, generación, validación, persistencia, upserts y validación global en 521 líneas;
- las fuentes/licencias documentan identidad pública mínima de clubes/ligas, pero no demuestran redistribución fuerte para planteles reales porque, correctamente, no se usan planteles reales.

## 2. Commits

Commits declarados y encontrados:

- `f02c4291 Design MVP 1 three-league data import pipeline`
- `0e93b68f Add MVP 1 league and player source datasets`
- `a88f9d1f Implement transactional three-league importer`
- `58798ba5 Validate MVP 1 three-league dataset integrity`
- `ad34a103 Complete MVP 1 three-league runtime acceptance`

Cambios entre `2e7df918..ad34a103`:

- documentación nueva en `docs/data`;
- dataset fuente en `src/main/resources/data/mvp1`;
- importador JDBC transaccional;
- configuración/runner de importación;
- tests de importador y runtime acceptance;
- ajustes menores en `PlayerSpecialAttributeSelectionValidator`, `WorldTeamPostgresWriter` y `LeagueEntity`;
- aparece también `docs/database/MVP1_DATABASE_BASELINE_FINAL_INDEPENDENT_AUDIT.md` dentro del rango revisado.

## 3. Estado Git

Estado observado antes de este informe:

- el working tree no estaba limpio porque existía un reporte no versionado previo: `docs/database/MVP1_DATABASE_BASELINE_FINAL_INDEPENDENT_AUDIT.md`;
- esta auditoría agrega únicamente `docs/data/MVP1_THREE_LEAGUE_FINAL_INDEPENDENT_AUDIT.md`;
- no se modificó código, SQL, dataset, tests ni documentación existente;
- no se hicieron commits.

`git diff --check 2e7df918..ad34a103`:

- falla por `src/main/java/com/footballmanager/application/service/world/importer/ThreeLeagueDatasetImporter.java:521: new blank line at EOF`.

## 4. Inventario real

Archivos bajo `src/main/resources/data/mvp1`:

- `countries.json`, 249 bytes;
- `leagues.json`, 475 bytes;
- `catalogs/player-attributes.json`, 68 bytes;
- `catalogs/special-attributes.json`, 2127 bytes;
- `clubs/spain.json`, 2040 bytes;
- `clubs/argentina.json`, 3277 bytes;
- `clubs/brazil.json`, 2105 bytes;
- `players/generated-player-policy.json`, 247 bytes.

Inventario fuente recalculado:

- países: 3;
- ligas: 3;
- clubes fuente: 70;
- jugadores fuente en JSON: 0;
- atributos numéricos catalogados en JSON: 6;
- rasgos especiales catalogados: 10.

El dataset de jugadores se genera determinísticamente en `ThreeLeagueDatasetImporter`, no está listado jugador por jugador en recursos JSON.

## 5. Ligas

Ligas declaradas:

- Spanish Primera Division, país `ESP`, 20 clubes, temporada según `leagues.json`;
- Argentine Primera Division, país `ARG`, 30 clubes, temporada según `leagues.json`;
- Brazilian Serie A, país `BRA`, 20 clubes, temporada según `leagues.json`.

La cantidad esperada de clubes coincide con la cantidad de clubes fuente por archivo.

## 6. Clubes

Clubes fuente recalculados:

- España: 20;
- Argentina: 30;
- Brasil: 20;
- total: 70.

No se detectaron duplicados de código dentro de cada archivo según la validación del importador.

Issue importante: la auditoría no hizo navegación web externa para validar contra fuentes oficiales actuales; verificó consistencia interna del dataset y la documentación versionada.

## 7. Planteles

Los archivos fuente de clubes tienen cero jugadores embebidos.

El importador genera 24 jugadores por club con la siguiente distribución fija:

- 2 GK;
- 7 DEF;
- 7 MID;
- 4 WINGER;
- 4 ATT.

En tests de DB temporal, el reporte del importador valida:

- 70 clubes;
- 70 equipos;
- 1680 jugadores;
- 3360 relaciones de rasgos.

Clasificación: planteles completos en runtime importado; no completos como dataset fuente explícito.

## 8. Jugadores

Los jugadores son:

- ficticios;
- determinísticos;
- generados desde país, club, índice y reputación;
- marcados con `source_system = manager-mvp1-generated`;
- documentados como no oficiales.

No hay evidencia de que se presenten como planteles reales. Esto reduce riesgo legal, pero también significa que el MVP 1 no contiene jugadores reales completos.

## 9. Fuentes y licencias

Documento auditado: `docs/data/MVP1_DATA_SOURCES_AND_LICENSING.md`.

Fuentes documentadas:

- LaLiga official clubs page para identidad pública de clubes de España;
- AFA para formato/clubes de Argentina;
- CBF para clubes de Brasil;
- openfootball como referencia de filosofía open data, sin importación automática.

La documentación declara explícitamente:

- no se copian planteles reales completos;
- no se copian ratings comerciales;
- jugadores, edades, alturas, atributos, valores de mercado y rasgos son generados.

Clasificación: aceptable para dataset ficticio distribuible, con issue importante porque la redistribución de identidad pública de clubes no está acompañada por licencias formales detalladas por entidad.

## 10. Atributos numéricos

Catálogo fuente:

- `attack`;
- `defense`;
- `technique`;
- `speed`;
- `stamina`;
- `mentality`.

El importador genera además:

- `height_cm`;
- `age` a partir de `birth_date`;
- `market_value`;
- `weekly_salary`;
- `skill_levels_json`;
- posición primaria;
- posiciones secundarias;
- pie dominante;
- dorsal.

El test runtime verifica presencia de:

- `baseAttack`;
- `baseDefense`;
- `baseTechnique`;
- `baseSpeed`;
- `baseStamina`;
- `baseMentality`.

## 11. Distribución

La distribución de atributos no proviene de datos reales. Se genera con:

- reputación del club;
- varianza determinística por club/índice;
- modificadores por posición;
- clamps de rango.

Esto produce diversidad funcional suficiente para tests y auto-select, pero no representa scouting real.

Issue menor: no hay reporte estadístico versionado con percentiles reales por liga/club/posición; la diversidad se infiere del algoritmo y tests.

## 12. Rasgos especiales

Catálogo fuente: 10 rasgos.

Rasgos:

- `clutch_finisher`;
- `press_resistant`;
- `aerial_specialist`;
- `line_breaker`;
- `leader`;
- `workhorse`;
- `speedster`;
- `set_piece_specialist`;
- `one_on_one_keeper`;
- `sweeper_keeper`.

El importador asigna dos rasgos por jugador mediante `traitsFor(position, i)` y valida cada selección con `PlayerSpecialAttributeSelectionValidator`.

Tests focalizados confirman cero jugadores importados con cantidad distinta de dos rasgos y cero relaciones huérfanas.

## 13. Importador

Archivo auditado: `src/main/java/com/footballmanager/application/service/world/importer/ThreeLeagueDatasetImporter.java`.

Fortalezas:

- importación explícita, no automática;
- `@Transactional`;
- upserts determinísticos;
- IDs determinísticos;
- validación de input;
- validación de plantel;
- validación de rasgos;
- validación global post-import;
- segunda importación idempotente en test;
- rollback ante conflicto en test.

Issues importantes:

- clase de 521 líneas con demasiadas responsabilidades;
- depende directamente de `JdbcTemplate`;
- application conoce SQL/JDBC;
- `@Transactional` e infraestructura Spring/JDBC están dentro del servicio de aplicación;
- no hay separación clara entre lector, generador, validador y writer.

## 14. Idempotencia

Test auditado: `ThreeLeagueDatasetImporterTest.secondImportIsIdempotent`.

Evidencia:

- primera importación en DB temporal;
- captura de counts;
- segunda importación;
- comparación de counts;
- validación de rasgos.

Resultado focalizado: verde.

Clasificación: demostrada por tests de DB temporal.

## 15. Rollback

Tests auditados:

- `ThreeLeagueDatasetRuntimeAcceptanceE2ETest.importerRollsBackWhenWriteFails`.

Evidencia:

- borra datos generados en test;
- introduce jugador conflictivo con el mismo UUID que un jugador generado;
- ejecuta importador;
- espera excepción;
- confirma cero clubes, cero equipos y cero jugadores generados.

Clasificación: rollback demostrado ante conflicto de escritura. No se probaron todas las variantes corruptas pedidas, como JSON temporal con rasgo inexistente, un solo rasgo o atributo fuera de rango.

## 16. DB

Base local principal `football_manager`:

- países: 0;
- ligas: 0;
- clubes: 0;
- equipos: 0;
- jugadores existentes: 1006 de seed anterior;
- rasgos en catálogo: 6;
- relaciones `player_special_attributes`: 0.

La base principal no está importada con el dataset MVP 1 de tres ligas. La evidencia positiva de importación proviene de DBs temporales usadas por tests.

## 17. Runtime

Test auditado: `ThreeLeagueDatasetRuntimeAcceptanceE2ETest`.

Cubre para España, Argentina y Brasil:

- importación;
- liga visible por API;
- cantidad exacta de equipos por liga;
- primer equipo con al menos 24 jugadores;
- atributos jugables completos en payload;
- creación de carrera;
- lectura de squad;
- auto-select de 11.

No cubre en esta fase:

- simulación de ronda completa para las tres ligas;
- partido detallado y eventos para cada liga importada;
- reinicio real de backend;
- segunda lectura post-reinicio.

## 18. Frontend

Validación ejecutada:

- `npm run build -- --configuration development`: verde;
- `npm run build`: verde;
- `npm test -- --watch=false --browsers=ChromeHeadless`: `TOTAL: 1016 SUCCESS`, 2 skipped.

Issue importante: no se encontró evidencia de que la UI muestre explícitamente rasgos especiales de jugadores importados; el propio reporte runtime declara que puede requerir iteración visual posterior.

## 19. Tests backend

Comandos ejecutados:

- `mvn -q -DskipTests test-compile`;
- `mvn -q test`;
- `mvn -q -Dtest='ThreeLeagueDatasetImporterTest,ThreeLeagueDatasetRuntimeAcceptanceE2ETest' test`.

Resultados:

- backend completo: 2444 tests, 0 failures, 0 errors, 4 skipped, 260 XML Surefire;
- focalizados: 5 tests, 0 failures, 0 errors, 0 skipped.

Delta contra `2e7df918`:

- métodos `@Test`: 2209 -> 2214, delta +5;
- archivos nuevos:
  - `ThreeLeagueDatasetImporterTest.java`;
  - `ThreeLeagueDatasetRuntimeAcceptanceE2ETest.java`.

## 20. Tests frontend

Resultado:

- build development verde;
- build production verde;
- Karma ChromeHeadless: 1016 success;
- failures: 0;
- skipped: 2.

## 21. Documentación

Documentos auditados:

- `MVP1_DATA_SOURCES_AND_LICENSING.md`;
- `MVP1_THREE_LEAGUE_IMPORT_DESIGN.md`;
- `MVP1_THREE_LEAGUE_DATASET_VALIDATION_REPORT.md`;
- `MVP1_THREE_LEAGUE_RUNTIME_ACCEPTANCE_REPORT.md`.

Issues:

- los documentos tienen mojibake visible en consola para acentos;
- algunos veredictos documentales son más optimistas que la evidencia independiente;
- el runtime report no deja suficientemente fuerte que el test no cubre simulación/detailed match/reinicio para las tres ligas;
- el dataset source no contiene jugadores, aunque esto sí está documentado como generación determinística.

## 22. Hallazgos críticos

No hay hallazgos críticos que indiquen que el pipeline sea inutilizable o que los tests declarados fallen.

## 23. Hallazgos importantes

1. La base principal no está importada con el dataset MVP 1 de tres ligas; solo las DB temporales de tests demuestran importación completa.
2. Los jugadores no son datos fuente versionados uno por uno; se generan por código. Esto es válido si el producto acepta jugadores ficticios, pero no equivale a “todos los jugadores reales”.
3. `git diff --check 2e7df918..ad34a103` falla por línea vacía extra al final de `ThreeLeagueDatasetImporter.java`.
4. El importador concentra demasiadas responsabilidades y mezcla application con JDBC/Spring transaction infrastructure.
5. Runtime acceptance no prueba simulación de ronda, partido detallado, eventos, estadísticas ni reinicio backend para las tres ligas.
6. Frontend verde, pero sin evidencia de visualización completa de rasgos especiales importados.
7. Licencias/fuentes son aceptables para identidad pública mínima y jugadores ficticios, pero no demuestran una licencia redistribuible robusta para planteles reales.

## 24. Hallazgos menores

- Falta reporte estadístico detallado de distribución de atributos por liga/club/posición.
- Los documentos existentes muestran encoding corrupto en la salida de consola.
- El catálogo fuente de atributos numéricos es muy pequeño y el resto de campos jugables se infiere del generador/importador.

## 25. Preparación para MVP 1

Clasificación:

- tres ligas completas: sí, a nivel clubes y runtime importado;
- todos los clubes: sí, según conteo esperado interno;
- planteles completos: sí en runtime generado, no como planteles fuente reales;
- todos los jugadores necesarios: sí para jugabilidad, no para realismo oficial;
- atributos completos: sí para campos mínimos consumidos por tests/runtime;
- exactamente dos rasgos por jugador: sí en importación temporal probada;
- datos legales y distribuibles: parcial, seguro para jugadores ficticios, menos fuerte para identidad pública de clubes sin licencia formal granular;
- importación idempotente: sí, probada;
- rollback: sí ante conflicto de escritura, parcial para corrupción variada;
- runtime jugable en tres ligas: sí para creación de carrera y auto-select, parcial para simulación/detailed match/reinicio.

Resultado general: parcialmente preparado para MVP 1. Preparado como dataset ficticio jugable inicial; no preparado como dataset realista/oficial completo.

## 26. Conclusión

El trabajo implementa un pipeline útil y testeado para arrancar carreras en España, Argentina y Brasil con planteles ficticios generados de forma determinística. Es suficiente para una primera versión jugable si se acepta explícitamente que los jugadores no son reales.

El cierre queda en `APPROVED WITH ISSUES`, no `APPROVED`, porque la evidencia aún tiene límites reales: importador demasiado concentrado, mezcla de capas, DB principal sin importar, `diff --check` fallando, runtime no cubre todo el ciclo de partido para las tres ligas y la fuente de jugadores es generación por código, no dataset versionado jugador por jugador.
