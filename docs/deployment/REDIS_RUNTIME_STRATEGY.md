# MANAGER - Redis Runtime Strategy

Fecha: 2026-07-31

Objetivo: clasificar qu√© vive en Redis y qu√© pasa si Redis desaparece durante PB1.1.

## Veredicto

Para PB1.1, Redis no puede tratarse como cache descartable.

Redis contiene estado runtime de carrera y partido que hoy es necesario para continuidad operacional. Por eso, para una beta p√∫blica, Redis debe tratarse como almacenamiento runtime cr√≠tico hasta que una fase posterior migre toda durabilidad a PostgreSQL.

## Clasificaci√≥n

| Estructura | Uso observado | Clasificaci√≥n | Si Redis desaparece |
|---|---|---|---|
| Career/session save | Carrera activa, equipos de sesi√≥n, torneo, lineup y estado de usuario | DURABLE | Se pierde continuidad de carrera si no hay snapshot externo. |
| Standings Redis | Tablas de carrera/temporada | DURABLE/RECONSTRUCTIBLE parcial | Puede reconstruirse solo si existen fixtures/resultados completos en PostgreSQL; debe probarse por carrera. |
| Detailed match detail | Eventos, timeline, stats y detalle de partido | DURABLE/RECONSTRUCTIBLE parcial | Puede degradar vista de detalle si no est√° persistido en PostgreSQL. |
| LiveSession active state | Partido/ronda en vivo | SESSION cr√≠tico | Se debe poder pausar/reiniciar o recuperar desde √∫ltimo estado persistido; si no, partido activo puede perderse. |
| Baseline/comparison state | Comparaci√≥n/harness/diff | RECONSTRUCTIBLE para debug, no cr√≠tico en prod | En prod no debe ser dato funcional cr√≠tico. |
| Test harness/lab state | Diagn√≥stico | No productivo | No carga en `prod`. |

## Pol√≠tica PB1.1

- Redis debe estar autenticado.
- Redis debe usar TLS en `prod` cuando el proveedor lo requiere.
- Redis debe tener persistencia gestionada o snapshot/export del proveedor.
- Ninguna beta p√∫blica debe ejecutarse sobre Redis ef√≠mero sin plan de backup/restore.
- Si Redis se pierde, el sistema debe declararse degradado y no afirmar readiness `UP`.

## Health/readiness

El endpoint `/api/v1/health` ahora valida:

- PostgreSQL con consulta `SELECT 1`.
- Redis con operaci√≥n no mutante.

Si Redis cae, el health devuelve `DOWN` y HTTP 503.

## Estrategia de recuperaci√≥n

Para PB1.1:

1. Restaurar Redis desde snapshot/export del proveedor cuando est√© disponible.
2. Si Redis no tiene snapshot v√°lido:
   - mantener backend fuera de readiness;
   - reconstruir standings/detalles solo donde PostgreSQL tenga datos suficientes;
   - informar p√©rdida de carreras afectadas si career/session save no puede reconstruirse.

## Requisito antes de beta p√∫blica

Antes de abrir usuarios externos:

- Elegir proveedor Redis con persistencia compatible.
- Ejecutar prueba de p√©rdida/recovery.
- Documentar qu√© datos se recuperan desde PostgreSQL y cu√°les dependen del snapshot Redis.

## Fase recomendada posterior

PB1.2 debe mover la durabilidad primaria de carreras, standings y detalles cr√≠ticos a PostgreSQL, dejando Redis como cache/session accelerator.

## ClasificaciÛn PB1.1 por familia de key

| Familia | ClasificaciÛn | Restore PB1.1 | Nota |
| --- | --- | --- | --- |
| `career:{userId}` | DURABLE / NOT RECONSTRUCTIBLE | Snapshot Redis requerido | Contiene continuidad de carrera y no debe tratarse como cache. |
| `standing:{userId}:*` | DURABLE / RECONSTRUCTIBLE parcial | Redis snapshot o reconstrucciÛn validada | Requiere fixtures/resultados completos para reconstrucciÛn. |
| `career:{careerId}:match-detail:{matchId}` | DURABLE / RECONSTRUCTIBLE parcial | Redis snapshot recomendado | La vista detallada puede degradarse si falta. |
| `match:state:{userId}:{matchId}` | EPHEMERAL runtime | No garantizado | Estado de partido activo; pÈrdida debe cortar readiness. |
| `runtime:match:{userId}:{matchId}` | EPHEMERAL / NOT RECONSTRUCTIBLE | No garantizado | Live runtime en curso; PB1.2 debe probar reconexiÛn/pÈrdida. |
| `career:{careerId}:match-baseline:{matchId}` | CACHE / RECONSTRUCTIBLE parcial | No crÌtico | ComparaciÛn/harness no productivo. |
| World snapshot keys | DURABLE / RECONSTRUCTIBLE parcial | Redis snapshot o reload desde SQL probado | Debe validarse por usuario/carrera. |
| Command/live session keys | EPHEMERAL / NOT RECONSTRUCTIBLE | No garantizado | Deben rechazarse o drenarse durante shutdown. |

## ConfiguraciÛn gestionada soportada en PB1.1

- `REDIS_HOST` y `REDIS_PORT` obligatorios en `prod`.
- `REDIS_USERNAME` opcional para ACL de proveedores gestionados.
- `REDIS_PASSWORD` obligatorio en `prod`.
- `REDIS_SSL` activado por defecto en `prod`.
- Timeout de conexiÛn/comando definido por configuraciÛn Lettuce/Spring.
- Health fail-fast: si Redis no responde, readiness custom devuelve HTTP 503.

## Gates PB1.2

Redis sigue siendo crÌtico. Antes de beta p˙blica abierta se requiere:

- proveedor Redis persistente;
- polÌtica TTL por familia;
- backup/export si el proveedor lo admite;
- restore drill;
- prueba de pÈrdida y reconexiÛn;
- documentaciÛn de quÈ se pierde si una LiveSession cae a mitad de partido.
