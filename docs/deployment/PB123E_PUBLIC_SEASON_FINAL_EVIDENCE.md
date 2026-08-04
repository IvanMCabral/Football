# PB1.2.3E — Evidencia pública final

## Superficies

- Frontend: https://manager-4f952.web.app
- Backend: https://manager-staging-api.onrender.com
- Revisión frontend desplegada: `d444e69`
- Revisión backend auditada: `421a02de`

## Criterios comprobados

| Área | Resultado |
|---|---|
| Editor inicial | PASS |
| Swap titular↔titular | PASS |
| Titular↔banco | PASS |
| Suplente→XI | PASS |
| Formación 4-3-3 | PASS |
| Confirmación/persistencia | PASS |
| Hard reload y login | PASS |
| Desktop/laptop/móvil | PASS |
| Consola propia | PASS |
| Seis fechas públicas | PASS |
| 12 partidos finalizados | PASS |
| Minuto 90 | PASS |
| Detalle y minute-by-minute | PASS |
| Recomputación de standings | PASS |
| 0-0 reales | PASS |
| Transición temporada 2 | PASS |
| Fecha 1 temporada 2 | PASS |
| Recovery | PASS con observación de convergencia visual durante carga |

## Checks de integridad HTTP

Durante la corrida no se observaron 500 propios, 404 repetitivos para partidos existentes, bucles de retry ni respuestas HTML en lugar de JSON. Los endpoints de detalle y minuto a minuto de los 12 partidos devolvieron 200. El endpoint de fixture individual devuelve nombres genéricos `Team` en su representación histórica, pero mantiene IDs y resultados correctos; el detalle persistido contiene nombres reales y la tabla coincide matemáticamente.

## Rendimiento observado

Se conserva la evidencia pública previa de tiempos warm: career status p50/p95 209/392 ms; squad 205/259 ms; lineup 220/251 ms; auto-select 898/2276 ms; fixtures 199/257 ms; standings 211/264 ms. La ejecución completa de seis fechas finalizó sin errores de infraestructura.

## Limitación de evidencia

El recorrido UI usa el stream de la pantalla de partido y estados públicos, pero no se archivó un payload SSE independiente por cada fecha. El comportamiento observable fue monotónico y la pantalla se rehidrató correctamente. Esto queda como una limitación de observabilidad, no como una inconsistencia de resultados.
