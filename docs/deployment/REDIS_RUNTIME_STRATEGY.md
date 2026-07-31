# MANAGER - Redis Runtime Strategy

Fecha: 2026-07-31

Objetivo: clasificar qué vive en Redis y qué pasa si Redis desaparece durante PB1.1.

## Veredicto

Para PB1.1, Redis no puede tratarse como cache descartable.

Redis contiene estado runtime de carrera y partido que hoy es necesario para continuidad operacional. Por eso, para una beta pública, Redis debe tratarse como almacenamiento runtime crítico hasta que una fase posterior migre toda durabilidad a PostgreSQL.

## Clasificación

| Estructura | Uso observado | Clasificación | Si Redis desaparece |
|---|---|---|---|
| Career/session save | Carrera activa, equipos de sesión, torneo, lineup y estado de usuario | DURABLE | Se pierde continuidad de carrera si no hay snapshot externo. |
| Standings Redis | Tablas de carrera/temporada | DURABLE/RECONSTRUCTIBLE parcial | Puede reconstruirse solo si existen fixtures/resultados completos en PostgreSQL; debe probarse por carrera. |
| Detailed match detail | Eventos, timeline, stats y detalle de partido | DURABLE/RECONSTRUCTIBLE parcial | Puede degradar vista de detalle si no está persistido en PostgreSQL. |
| LiveSession active state | Partido/ronda en vivo | SESSION crítico | Se debe poder pausar/reiniciar o recuperar desde último estado persistido; si no, partido activo puede perderse. |
| Baseline/comparison state | Comparación/harness/diff | RECONSTRUCTIBLE para debug, no crítico en prod | En prod no debe ser dato funcional crítico. |
| Test harness/lab state | Diagnóstico | No productivo | No carga en `prod`. |

## Política PB1.1

- Redis debe estar autenticado.
- Redis debe usar TLS en `prod` cuando el proveedor lo requiere.
- Redis debe tener persistencia gestionada o snapshot/export del proveedor.
- Ninguna beta pública debe ejecutarse sobre Redis efímero sin plan de backup/restore.
- Si Redis se pierde, el sistema debe declararse degradado y no afirmar readiness `UP`.

## Health/readiness

El endpoint `/api/v1/health` ahora valida:

- PostgreSQL con consulta `SELECT 1`.
- Redis con operación no mutante.

Si Redis cae, el health devuelve `DOWN` y HTTP 503.

## Estrategia de recuperación

Para PB1.1:

1. Restaurar Redis desde snapshot/export del proveedor cuando esté disponible.
2. Si Redis no tiene snapshot válido:
   - mantener backend fuera de readiness;
   - reconstruir standings/detalles solo donde PostgreSQL tenga datos suficientes;
   - informar pérdida de carreras afectadas si career/session save no puede reconstruirse.

## Requisito antes de beta pública

Antes de abrir usuarios externos:

- Elegir proveedor Redis con persistencia compatible.
- Ejecutar prueba de pérdida/recovery.
- Documentar qué datos se recuperan desde PostgreSQL y cuáles dependen del snapshot Redis.

## Fase recomendada posterior

PB1.2 debe mover la durabilidad primaria de carreras, standings y detalles críticos a PostgreSQL, dejando Redis como cache/session accelerator.
