# MANAGER - Redis Runtime Strategy

Fecha: 2026-08-01

Objetivo: clasificar qué vive en Redis y qué pasa si Redis desaparece durante PB1.1.

## Veredicto

Para PB1.1, Redis no puede tratarse como cache descartable.

Redis contiene estado runtime de carrera y partido que hoy es necesario para continuidad operacional. Por eso, para una beta pública, Redis debe tratarse como almacenamiento runtime crítico hasta que una fase posterior migre toda durabilidad a PostgreSQL.

## Clasificación

| Estructura | Uso observado | Clasificación | Si Redis desaparece |
| --- | --- | --- | --- |
| Career/session save | Carrera activa, equipos de sesión, torneo, lineup y estado de usuario | DURABLE | Se pierde continuidad de carrera si no hay snapshot externo. |
| Standings Redis | Tablas de carrera/temporada | DURABLE / RECONSTRUCTIBLE parcial | Puede reconstruirse solo si existen fixtures/resultados completos en PostgreSQL; debe probarse por carrera. |
| Detailed match detail | Eventos, timeline, stats y detalle de partido | DURABLE / RECONSTRUCTIBLE parcial | Puede degradar vista de detalle si no está persistido en PostgreSQL. |
| LiveSession active state | Partido/ronda en vivo | EPHEMERAL crítico | Se debe poder pausar/reiniciar o recuperar desde último estado persistido; si no, un partido activo puede perderse. |
| Baseline/comparison state | Comparación/harness/diff | CACHE / RECONSTRUCTIBLE parcial | En producción no debe ser dato funcional crítico. |
| Test harness/lab state | Diagnóstico | No productivo | No carga en `prod`. |

## Clasificación PB1.1 por familia de key

| Familia | Clasificación | Restore PB1.1 | Nota |
| --- | --- | --- | --- |
| `career:{userId}` | DURABLE / NOT RECONSTRUCTIBLE | Snapshot Redis requerido | Contiene continuidad de carrera y no debe tratarse como cache. |
| `standing:{userId}:*` | DURABLE / RECONSTRUCTIBLE parcial | Redis snapshot o reconstrucción validada | Requiere fixtures/resultados completos para reconstrucción. |
| `career:{careerId}:match-detail:{matchId}` | DURABLE / RECONSTRUCTIBLE parcial | Redis snapshot recomendado | La vista detallada puede degradarse si falta. |
| `match:state:{userId}:{matchId}` | EPHEMERAL runtime | No garantizado | Estado de partido activo; pérdida debe cortar readiness. |
| `runtime:match:{userId}:{matchId}` | EPHEMERAL / NOT RECONSTRUCTIBLE | No garantizado | Live runtime en curso; PB1.2 debe probar reconexión/pérdida. |
| `career:{careerId}:match-baseline:{matchId}` | CACHE / RECONSTRUCTIBLE parcial | No crítico | Comparación/harness no productivo. |
| World snapshot keys | DURABLE / RECONSTRUCTIBLE parcial | Redis snapshot o reload desde SQL probado | Debe validarse por usuario/carrera. |
| Command/live session keys | EPHEMERAL / NOT RECONSTRUCTIBLE | No garantizado | Deben rechazarse o drenarse durante shutdown. |

## Política PB1.1

- Redis debe estar autenticado.
- Redis debe usar TLS en `prod` cuando el proveedor lo requiere.
- Redis debe tener persistencia gestionada o snapshot/export del proveedor.
- Ninguna beta pública debe ejecutarse sobre Redis efímero sin plan de backup/restore.
- Si Redis se pierde, el sistema debe declararse degradado y no afirmar readiness `UP`.

## Configuración gestionada soportada en PB1.1

- `REDIS_HOST` y `REDIS_PORT` obligatorios en `prod`.
- `REDIS_USERNAME` opcional para ACL de proveedores gestionados.
- `REDIS_PASSWORD` obligatorio en `prod`.
- `REDIS_SSL` activado por defecto en `prod`.
- `spring.data.redis.database` respetado por la fábrica Redis.
- Timeout de conexión/comando definido por configuración Lettuce/Spring.
- Health fail-fast: si Redis no responde, readiness custom devuelve HTTP 503.

## Health/readiness

El endpoint `/api/v1/health` delega en readiness y valida:

- PostgreSQL con consulta `SELECT 1`.
- Redis con una operación efímera `set/get/delete` sobre una key única `__manager_healthcheck__:<uuid>` con TTL corto.

Si Redis cae o no responde, readiness devuelve `DOWN` y HTTP 503.

## Test isolation

El profile `test` puede iniciar un Redis local aislado en puerto aleatorio cuando `redis-server` está disponible en PATH. Usa contraseña efímera, database 15 y persistencia deshabilitada. Esto evita depender de credenciales reutilizables del entorno local y evita ensuciar Redis principal.

## Estrategia de recuperación

Para PB1.1:

1. Restaurar Redis desde snapshot/export del proveedor cuando esté disponible.
2. Si Redis no tiene snapshot válido:
   - mantener backend fuera de readiness;
   - reconstruir standings/detalles solo donde PostgreSQL tenga datos suficientes;
   - informar pérdida de carreras afectadas si career/session save no puede reconstruirse.

## Requisito antes de beta pública abierta

Antes de abrir usuarios externos:

- Elegir proveedor Redis con persistencia compatible.
- Ejecutar prueba de pérdida/recovery.
- Documentar qué datos se recuperan desde PostgreSQL y cuáles dependen del snapshot Redis.

## Gates PB1.2

Redis sigue siendo crítico. Antes de beta pública abierta se requiere:

- proveedor Redis persistente;
- política TTL por familia;
- backup/export si el proveedor lo admite;
- restore drill;
- prueba de pérdida y reconexión;
- documentación de qué se pierde si una LiveSession cae a mitad de partido.

PB1.2 debe mover la durabilidad primaria de carreras, standings y detalles críticos a PostgreSQL, dejando Redis como cache/session accelerator.

---

## 2026-08-01 definitive test runtime closure

The blocker closure rejection has been remediated in a later change set. See:

- `docs/deployment/PB1_TEST_RUNTIME_REPRODUCIBILITY_REMEDIATION.md`
- `docs/deployment/PB1_TEST_RUNTIME_REPRODUCIBILITY_FINAL_REVIEW.md`

Current reproducibility evidence:

- Backend compile: PASS.
- Backend full suite run 1: 2564 tests, 0 failures, 0 errors, 4 skipped.
- Backend full suite run 2: 2564 tests, 0 failures, 0 errors, 4 skipped.
- Frontend: encoding guard PASS, development build PASS, production build PASS, 1029 SUCCESS, 0 failures, 2 skipped.
- Test infrastructure: no `.env`, no manually started PostgreSQL, no manually started Redis; local PostgreSQL and Redis binaries are launched as ephemeral test processes.
- Redis durability remains a PB1.2 infrastructure gate, not a closed PB1.1 durability claim.
