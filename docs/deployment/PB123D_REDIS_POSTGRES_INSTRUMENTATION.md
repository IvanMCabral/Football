# PB1.2.3D — Instrumentación Redis/PostgreSQL

## Implementación

`RuntimeOperationMetrics` usa publishers diferidos y agrega duración, éxitos y errores en memoria. Los logs agregados se emiten cada diez observaciones o ante error. No registra claves completas, valores serializados, payloads, tokens, passwords ni datos personales.

## Operaciones instrumentadas

| Fuente | Operaciones |
|---|---|
| Redis | `redis.career.save`, `load`, `exists`, `delete`, `ttl` |
| PostgreSQL | `postgres.user.save`, `create`, `findById`, `findByEmail`, `findByUsername`, `existsByEmail`, `existsByUsername`, `delete` |
| HTTP/aplicación | `http.dashboard.userStats`, `worldStatus`, `reloadWorld`, `http.career.status`, `fixtures`, `standings` |

## Evidencia de rendimiento relacionada

En la medición pública warm actual, N=10: status p50/p95 `209/392 ms`, squad `205/259 ms`, lineup `220/251 ms`, auto-select `898/2.276 ms`, fixtures `199/257 ms`, standings `211/264 ms`. Los tiempos Redis y PostgreSQL no se aislaron en esta ejecución; no se atribuye causalidad sin esos datos.

## Limitaciones

- El agregado es local a la instancia; no es un sistema de métricas distribuido.
- No se exportan bytes de payload ni TTL efectivo por clave; `ttl` mide la operación de extensión.
- No hay dashboard externo, alertas ni correlación persistida por request.
- Readiness puede incluir wake-up de Render Free.

## Veredicto

`PARTIAL — SAFE BASIC INSTRUMENTATION`. La capa es real y segura para comparar revisiones; la observabilidad persistente y la separación Redis/DB quedan pendientes del siguiente gate de infraestructura.
