# PB1.2.3C — Redis, PostgreSQL y rendimiento público

Fecha: 2026-08-04

## Resultado

**REJECTED FOR PERFORMANCE-CERTIFICATION CLOSURE**

Se midieron endpoints de health y se revisaron logs de Render, pero el backend actual no expone instrumentación por operación con duración, request ID, p50/p95 Redis y PostgreSQL. No se presentan estimaciones como mediciones.

## Topología observada

- Render: `manager-staging-api`, plan Free, puerto interno 10000.
- PostgreSQL: Neon, endpoint pooler en región `sa-east-1`.
- Redis: Upstash autenticado; la región exacta no quedó registrada en la evidencia de esta sesión.
- Frontend: Firebase Hosting `manager-4f952.web.app`.

## Instrumentación existente

Los logs productivos muestran eventos lógicos como `[REDIS-LOAD]` y `[REDIS-SAVE]`, con tamaño de palmarés y estado de deserialización. No incluyen duración, p50/p95, cantidad de comandos, tamaño de payload, TTL medido ni request/correlation ID. La búsqueda de código productivo no encontró timers, Micrometer timers ni una capa de medición Redis/DB por operación.

Clasificación actual, sin inventar durabilidad:

| Operación/dato | Clasificación | Evidencia |
|---|---|---|
| Career save | DURABLE REMOTO | Redis es la fuente de recuperación observada |
| Match runtime/live state | DURABLE REMOTO CON TTL | Repositorio Redis y TTL en código |
| Match commands | EPHEMERAL/DURABLE PENDIENTE | Requiere drill de pérdida |
| Detailed match | RECONSTRUCTIBLE PARCIAL | Persistencia Redis observada |
| Standings | RECONSTRUCTIBLE | Puede derivarse de fixtures finalizados |
| World snapshot | RECONSTRUCTIBLE | También existe PostgreSQL |
| LiveSession | NOT CERTIFIED | Falta restart/recovery real |

## Muestras públicas disponibles

| Operación | N | p50 | p95 | máximo | Resultado |
|---|---:|---:|---:|---:|---|
| Liveness | 10 | 228 ms | 258 ms | 800 ms | 10×200 |
| Readiness | 10 | 776 ms | 2.062 ms | 2.259 ms | 1×503, 9×200 |
| World leagues (cuenta nueva) | 1 | 13.074 ms | — | 13.074 ms | 200, 3 ligas |
| Squad (cuenta nueva) | 1 | 12.347 ms | — | 12.347 ms | 200, 24 jugadores |
| Auto-select lineup | 1 | ~6.000 ms | — | ~6.000 ms | 200, 7.993 bytes |
| Inicio de ronda | 1 | ~4.000 ms | — | ~4.000 ms | 200 |
| Estado final de partido | 1 | — | — | — | 200, minuto 90, 24 eventos |

Las muestras de negocio son únicas y no alcanzan las diez muestras warm exigidas para dashboard, squad, lineup save, formation, fixtures, standings, SSE, substitution, finalization y transition.

## Rendimiento y jugabilidad

Con la evidencia disponible, el único veredicto responsable es:

**JUGABLE CON ESPERAS**

La cuenta nueva llega a carrera y un partido finaliza, pero la carga de mundo/plantel supera 8 s warm y readiness tuvo una respuesta 503. No hay base para afirmar que el motor completo sea `JUGABLE` bajo la tabla de 10 muestras.

## Cuellos de botella confirmados

- Arranque Spring: 106.607 s observado en Render.
- Carga de mundo: 13.074 s en una cuenta nueva.
- Carga de squad: 12.347 s en una cuenta nueva.
- Readiness depende de consultas reales y puede devolver 503 durante la recuperación.

No se afirma que Redis sea el cuello sin medición de comandos. Tampoco se afirma una latencia PostgreSQL aislada sin instrumentación.

## Gates pendientes

- Instrumentar Redis GET/SET/DELETE y serialización con tiempos agregados.
- Medir queries PostgreSQL por operación lógica.
- Capturar request/correlation ID sin keys ni payloads sensibles.
- Tomar 10 muestras warm por operación solicitada.
- Registrar TTL, tamaño y round trips.
- Completar restore/restart drill antes de clasificar LiveSession como durable.

## Conclusión

El reporte es deliberadamente conservador: hay evidencia de funcionamiento público, pero no existe todavía la instrumentación ni la cantidad de muestras necesarias para certificar rendimiento profesional. Resultado: **REJECTED**.
