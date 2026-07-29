# MVP 1 Three-League Dataset Validation Report

## Veredicto

`APPROVED` para integridad del dataset generado MVP 1.

## Cobertura esperada

| Métrica | Esperado |
| --- | ---: |
| Países | 3 |
| Ligas | 3 |
| Clubes | 70 |
| Equipos runtime | 70 |
| Jugadores | 1680 |
| Jugadores por club | 24 |
| Rasgos por jugador | 2 |
| Relaciones de rasgos | 3360 |

## Balance mínimo por club

Cada club contiene:

- 2 GK;
- 7 DEF;
- 7 MID;
- 4 WINGER;
- 4 ATT.

Las posiciones persistidas son posiciones concretas del dominio; el balance anterior se calcula por grupo táctico.

## Validaciones automáticas

`ThreeLeagueDatasetImporterTest` cubre:

- importación desde DB vacía recreada con Flyway V1;
- counts completos;
- segunda importación idempotente;
- cero jugadores con cantidad distinta de dos rasgos;
- cero relaciones huérfanas;
- detección de cobertura rota de rasgos.

`ThreeLeagueDatasetRuntimeAcceptanceE2ETest` cubre:

- publicación API de España, Argentina y Brasil;
- cantidad exacta de equipos por liga;
- squads con al menos 24 jugadores;
- atributos jugables completos;
- creación de carrera por liga;
- auto-select de once inicial;
- rollback transaccional ante conflicto de escritura.

## Evidencia focalizada

Comando ejecutado:

```bash
mvn -q -Dtest='ThreeLeagueDatasetImporterTest,ThreeLeagueDatasetRuntimeAcceptanceE2ETest' test
```

Resultado: verde, 5 tests, 0 fallos, 0 errores, 0 omitidos.

## Evidencia completa backend

Comandos ejecutados:

```bash
mvn -q -DskipTests test-compile
mvn -q test
```

Resultado: verde, 2444 tests, 0 fallos, 0 errores, 4 omitidos.

## Política de datos

Los clubes/ligas son identidad pública mínima. Los jugadores y atributos son generados, no oficiales. Esta fase evita copiar planteles comerciales o no licenciados.

## SQL global obligatorio

```sql
SELECT player_id
FROM player_special_attributes
GROUP BY player_id
HAVING COUNT(*) <> 2;
```

Resultado esperado para jugadores importados: cero filas.
