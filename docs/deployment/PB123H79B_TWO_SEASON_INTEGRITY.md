# PB1.2.3H7.9B — Two-season integrity

## Formato

La carrera fue creada por el contrato público `/career/start` con
`teamsPerDivision=4`. Cada temporada tuvo 4 equipos, 6 rondas y 12 fixtures.

## Temporada 1

| Equipo | PJ | G | E | P | GF | GC | GD | Pts |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| FC Barcelona | 6 | 4 | 1 | 1 | 7 | 3 | 4 | 13 |
| Atletico de Madrid | 6 | 1 | 4 | 1 | 1 | 1 | 0 | 7 |
| Real Sociedad | 6 | 1 | 3 | 2 | 1 | 3 | -2 | 6 |
| Real Madrid | 6 | 1 | 2 | 3 | 3 | 5 | -2 | 5 |

Campeón: FC Barcelona. El recálculo independiente de todos los fixtures
coincidió campo por campo con la API; `sum(GF)=sum(GC)=12`.

## Temporada 2

| Equipo | PJ | G | E | P | GF | GC | GD | Pts |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Villarreal CF | 6 | 2 | 4 | 0 | 3 | 0 | 3 | 10 |
| CA Osasuna | 6 | 1 | 4 | 1 | 2 | 2 | 0 | 7 |
| Valencia CF | 6 | 1 | 4 | 1 | 2 | 2 | 0 | 7 |
| Real Madrid | 6 | 0 | 4 | 2 | 2 | 5 | -3 | 4 |

Campeón: Villarreal CF. El recálculo independiente coincidió con la API;
`sum(GF)=sum(GC)=9`.

## Integridad por ronda

Las 12 rondas tuvieron `POST start=200`, tres eventos SSE iniciales
observados, estado `COMPLETED`, detalle HTTP 200, timeline no vacío y último
minuto 90. Todos los 0–0 fueron fixtures completados con detalle y
minute-by-minute válidos.
