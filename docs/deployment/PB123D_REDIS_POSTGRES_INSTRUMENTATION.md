# PB1.2.3D — Redis/PostgreSQL instrumentation

## Principios

La instrumentación mide comportamiento observable sin registrar secretos ni datos sensibles. No incluye claves Redis, valores serializados, tokens, contraseñas ni payloads de usuario. Los identificadores operativos existentes en logs no se amplían.

## Operaciones medidas

| Fuente | Operaciones |
|---|---|
| Redis | `redis.career.save`, `load`, `exists`, `delete`, `ttl` |
| PostgreSQL | `postgres.user.save`, `create`, `findById`, `findByEmail`, `findByUsername`, `existsByEmail`, `existsByUsername`, `delete` |
| HTTP/application | `http.dashboard.userStats`, `worldStatus`, `reloadWorld`, `http.career.status`, `fixtures`, `standings` |

Cada observación registra duración, éxito/error y un agregado periódico en memoria. `Mono.defer` evita ejecutar operaciones al construir el pipeline; los errores se propagan sin silenciarse.

## Uso operativo

La métrica permite comparar revisiones warm y detectar regresiones de lectura/escritura. Debe exportarse a un backend de métricas antes de escalar horizontalmente: el agregado actual es local a la instancia y no pretende ser un sistema de observabilidad durable.

## Limitaciones honestas

- No se registran bytes de payload ni TTL efectivo por clave; el contador `ttl` solo mide la operación de extensión.
- No hay dashboard externo ni alertas configuradas en PB1.2.3D.
- La latencia de readiness puede incluir wake-up de Render Free y no debe atribuirse exclusivamente a PostgreSQL o Redis.

## Veredicto

`PARTIAL — SAFE BASIC INSTRUMENTATION`. La medición interna es real y segura; la observabilidad de producción distribuida queda pendiente para el siguiente gate de infraestructura.
