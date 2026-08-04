# PB1.2.3D — Evidencia de ejecución de temporada completa

## Ejecución pública reproducible

Se creó una cuenta efímera nueva contra `https://manager-staging-api.onrender.com` y una carrera de cuatro equipos por división (seis fechas). La simulación se inició mediante el contrato público `POST /api/v1/match-engine/rounds/start` usando las fixtures devueltas por la API; no se modificaron PostgreSQL ni Redis manualmente.

| Fecha | Fixtures | Estado observado | Minuto | Marcador observado |
|---:|---:|---|---:|---:|
| 1 | 2 | FINISHED | 90 | 0–0 |
| 2 | 2 | FINISHED | 90 | 0–0 |
| 3 | 2 | FINISHED | 90 | 1–1 |
| 4 | 2 | FINISHED | 90 | 1–0 |
| 5 | 2 | FINISHED | 90 | 1–0 |
| 6 | 2 | FINISHED | 90 | 2–0 |

Después de la sexta fecha, `/api/v1/career/status` informó `season=1`, `careerPhase=FINISHED` y `currentRound=6`. Los marcadores 0–0 fueron observados como resultados del motor; esta ejecución no recolectó una cuenta de eventos por fixture suficiente para certificar que cada empate sin goles no sea sintético, por lo que ese invariante queda abierto.

## Transición mínima a temporada 2

`POST /api/v1/career/continue` respondió correctamente. El estado posterior informó `season=2`, `careerPhase=PRE_MATCH` y `currentRound=1`. La primera fecha de temporada 2 se inició y terminó en minuto 90; el estado posterior informó `careerPhase=WAITING_USER` y `currentRound=2`.

## Evidencia no capturada por este harness

- Tabla final completa con PJ/G/E/P/GF/GC y posición del usuario.
- Historial visual completo después de logout/login.
- Validación de cada fixture individual contra el detalle persistido y sus eventos.
- Refresh durante una ronda live y recuperación posterior.

## Veredicto

`PARTIAL — SEASON EXECUTED, NOT FULLY CERTIFIED`. La temporada 1, la transición y la fecha 1 de temporada 2 se ejecutaron públicamente; la certificación definitiva permanece abierta por los invariantes de detalle y recuperación indicados arriba.
