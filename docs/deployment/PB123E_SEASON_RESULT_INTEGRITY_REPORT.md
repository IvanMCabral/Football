# PB1.2.3E — Integridad de resultados de temporada

Fecha de ejecución: 2026-08-04
Cuenta efímera: `pb123e_8d51f4ddf0`
Liga: Spanish Primera Division
Equipo: Real Madrid
Formato: 4 equipos, 6 fechas, 12 partidos

## Evidencia por fecha

Cada fecha se inició contra el endpoint público de ronda y se consultaron estados, fixtures, standings, minuto a minuto y detalle persistido. Todas las fechas tuvieron dos partidos, estado final `FINISHED`, minuto 90 y detalle HTTP 200.

| Fecha | Partidos | Resultado de los dos partidos | Estado | Minuto |
|---:|---:|---|---|---:|
| 1 | 2 | 0-1, 0-1 | FINISHED | 90 |
| 2 | 2 | 2-1, 0-0 | FINISHED | 90 |
| 3 | 2 | 0-0, 0-3 | FINISHED | 90 |
| 4 | 2 | 0-0, 1-1 | FINISHED | 90 |
| 5 | 2 | 0-0, 0-0 | FINISHED | 90 |
| 6 | 2 | 0-0, 1-2 | FINISHED | 90 |

No hubo fechas repetidas ni omitidas. Los 12 partidos tienen fixture, estado de ronda y detalle persistido.

## Tabla final y recomputación independiente

El verificador independiente recalculó PJ, G, E, P, GF, GC, DG y puntos desde los 12 fixtures completados. El resultado coincidió campo por campo con `GET /api/v1/career/standings` (`mathEqual=true`). La suma global fue GF=13 y GC=13.

| Pos | Equipo | PJ | G | E | P | GF | GC | DG | PTS |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | Real Madrid | 6 | 2 | 3 | 1 | 5 | 2 | 3 | 9 |
| 2 | Real Sociedad | 6 | 2 | 2 | 2 | 5 | 7 | -2 | 8 |
| 3 | Atletico de Madrid | 6 | 1 | 4 | 1 | 2 | 2 | 0 | 7 |
| 4 | FC Barcelona | 6 | 0 | 5 | 1 | 1 | 2 | -1 | 5 |

El usuario terminó 1.º: W/D/L 2/3/1, GF/GC 5/2, DG +3 y 9 puntos.

## Verificación de 0-0

Se verificaron seis marcadores 0-0. En todos los casos:

- el estado de ronda fue `FINISHED` y el minuto final 90;
- el fixture quedó `COMPLETED` con ambos goles en cero;
- `minute-by-minute` devolvió HTTP 200 y una secuencia no vacía cuyo último estado fue minuto 90, 0-0, `FINISHED`;
- el detalle persistido devolvió HTTP 200, timeline no vacío y `homeGoals=0`, `awayGoals=0`;
- la tabla posterior registró el empate con un punto por equipo.

No se usó un fallback sintético de 0-0.

## Transición a temporada 2

Tras la fecha 6, el estado público fue `season=1`, `currentRound=6`, `careerPhase=FINISHED`. `POST /api/v1/career/continue` devolvió éxito y produjo temporada 2, `currentRound=1`, `PRE_MATCH`, standings nuevos en cero y fixtures distintos. El equipo y el lineup persistieron. La fecha 1 de temporada 2 se ejecutó con dos partidos, ambos `FINISHED`, minuto 90 y detalle/minuto a minuto HTTP 200; resultados 0-0 y 0-1.

## Recovery

En una ronda pública en vivo se observó minuto 32+ y luego minuto 56 antes de recargar. Después del hard reload, la misma ruta recuperó la fecha, llegó a minuto 90 y conservó los marcadores 0-0 y 1-0. No se creó una segunda ronda ni se observaron errores propios de la SPA. La transición de carga mostró brevemente “Cargando fecha...”/“Por iniciar” mientras se rehidrataba el estado y luego convergió al estado final correcto.

## Evidencia reproducible

Los datos crudos sanitizados quedaron en `C:\Users\ichu_\AppData\Local\Temp\pb123e-season-evidence.json` y `pb123e-season2-round1.json`. El cálculo independiente se ejecutó con Python sobre esos JSON, sin alterar PostgreSQL, Redis, probabilidades ni calendario.
