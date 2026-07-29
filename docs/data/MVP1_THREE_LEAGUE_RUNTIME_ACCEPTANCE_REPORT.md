# MVP 1 Three-League Runtime Acceptance Report

## Veredicto

`APPROVED FOR THREE-LEAGUE DATASET PHASE`.

Este informe no declara el MVP completo terminado; cierra la fase de dataset/importación/runtime de España, Argentina y Brasil.

## Runtime cubierto por esta fase

- Backend compila con el importador incluido.
- Importador protegido por flag, sin ejecución automática normal.
- Tests de integración contra PostgreSQL validan importación, idempotencia y rollback.
- Runtime API permite listar ligas/equipos/jugadores y crear carreras en las tres ligas.
- Auto-select arma un once inicial desde squads importados.
- La baseline Flyway V1 se mantiene intacta.

## Comando operativo

```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--app.world.import.three-league=true"
```

Requiere DB recreada/migrada previamente y credenciales locales configuradas.

## Evidencia backend

```bash
mvn -q -DskipTests test-compile
mvn -q test
```

Resultado: verde, 2444 tests, 0 fallos, 0 errores, 4 omitidos.

## Evidencia focalizada

```bash
mvn -q -Dtest='ThreeLeagueDatasetImporterTest,ThreeLeagueDatasetRuntimeAcceptanceE2ETest' test
```

Resultado: verde, 5 tests, 0 fallos, 0 errores, 0 omitidos.

## Evidencia frontend

```bash
npm run build -- --configuration development
npm run build
npm test -- --watch=false --browsers=ChromeHeadless
```

Resultado:

- build development verde;
- build production verde;
- `TOTAL: 1016 SUCCESS`;
- 0 fallos;
- 2 skipped.

## Datos runtime aceptados

| Liga | País | Equipos esperados | Resultado |
| --- | --- | ---: | --- |
| Spanish Primera Division | ESP | 20 | OK |
| Argentine Primera Division | ARG | 30 | OK |
| Brazilian Serie A | BRA | 20 | OK |

## Riesgos fuera de esta fase

- Dataset de jugadores ficticio: correcto para MVP jugable/licencia segura, no para realismo de planteles oficiales.
- La UI puede necesitar campos adicionales para mostrar rasgos especiales de forma explícita.
- Esta fase no modifica la lógica de lesiones, fatiga o scouting.
