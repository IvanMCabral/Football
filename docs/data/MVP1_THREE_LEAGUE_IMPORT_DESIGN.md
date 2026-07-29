# MVP 1 Three-League Import Design

## Veredicto de diseño

`APPROVED FOR DATASET PIPELINE` para la fase de dataset/importación de tres ligas.

## Alcance

- Países: Spain (`ESP`), Argentina (`ARG`), Brazil (`BRA`).
- Ligas: Spanish Primera Division, Argentine Primera Division, Brazilian Serie A.
- Clubes: 70 en total: 20 España, 30 Argentina, 20 Brasil.
- Jugadores: 24 generados por club, 1680 total.
- Rasgos especiales: exactamente 2 por jugador, 3360 relaciones.

## Formato fuente

Recursos versionados en `src/main/resources/data/mvp1/`:

- `countries.json`
- `leagues.json`
- `clubs/spain.json`
- `clubs/argentina.json`
- `clubs/brazil.json`
- `catalogs/player-attributes.json`
- `catalogs/special-attributes.json`
- `players/generated-player-policy.json`

Los archivos son UTF-8 y revisables en Git.

## Importador

`ThreeLeagueDatasetImporter` carga los recursos, valida el input, genera planteles determinísticos y persiste con upsert transaccional. Corre únicamente si se invoca explícitamente mediante:

```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--app.world.import.three-league=true"
```

No se ejecuta en arranque normal.

## Transacción e idempotencia

El importador usa un transaction manager JDBC dedicado. Las pruebas cubren:

- DB vacía recreada con Flyway V1;
- primera importación completa;
- segunda importación idempotente sin duplicar counts;
- rollback transaccional ante conflicto de escritura a mitad del proceso.

## Posiciones y zonas tácticas

Los jugadores se guardan con posiciones concretas compatibles con el dominio (`GK`, `LB`, `CB`, `RB`, `LWB`, `RWB`, `CDM`, `CM`, `CAM`, `LM`, `RM`, `LW`, `RW`, `CF`, `ST`). Para balance y rasgos se agrupan tácticamente como `GK`, `DEF`, `MID`, `WINGER` y `ATT`.

Cada club contiene:

- 2 arqueros;
- 7 defensores;
- 7 mediocampistas;
- 4 extremos;
- 4 atacantes.

## Rasgos especiales

Toda asignación pasa por `PlayerSpecialAttributeSelectionValidator` antes de escribir `player_special_attributes`. La escritura borra y reescribe slots `1` y `2` en orden validado.

Rasgos iniciales:

- `clutch_finisher`
- `press_resistant`
- `aerial_specialist`
- `line_breaker`
- `leader`
- `workhorse`
- `speedster`
- `set_piece_specialist`
- `one_on_one_keeper`
- `sweeper_keeper`

## Matriz de atributos consumidos

| Atributo | Tipo | Rango | Consumidores | Obligatorio MVP 1 | Fuente |
| --- | --- | --- | --- | --- | --- |
| attack | numérico | 1-99 | simulación, tiros, ataque, ratings, auto-select | Sí | generado determinístico |
| defense | numérico | 1-99 | defensa, porteros, ratings, auto-select | Sí | generado determinístico |
| technique | numérico | 1-99 | pases, técnica, eventos, ratings | Sí | generado determinístico |
| speed | numérico | 1-99 | bandas, transiciones, eventos, ratings | Sí | generado determinístico |
| stamina | numérico | 1-99 | energía/fatiga, intensidad, ratings | Sí | generado determinístico |
| mentality | numérico | 1-99 | moral/forma/presión, disciplina, ratings | Sí | generado determinístico |
| height_cm | físico | 160-210 | juego aéreo, porteros, validación DB | Sí | generado determinístico |
| position | enum dominio | 15 posiciones concretas | lineup, roles, auto-select, motor | Sí | generado determinístico |
| secondary_positions | relación | posiciones válidas | lineup/manual, roles futuros | Sí | generado determinístico |
| dominant_foot | enum | RIGHT/LEFT | presentación y futura táctica | Sí | generado determinístico |
| energy | estado | 0-100 | fatiga y simulación | Sí | default 100 |
| injured | estado | boolean | lesiones y disponibilidad | Sí | default false |

## Riesgos no bloqueantes

- Los planteles son ficticios; no deben mostrarse como oficiales.
- El frontend puede requerir una iteración visual posterior para exponer rasgos especiales si hoy no los muestra en todas las pantallas.
