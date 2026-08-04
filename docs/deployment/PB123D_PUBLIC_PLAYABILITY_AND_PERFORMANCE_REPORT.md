# PB1.2.3D — Informe público de jugabilidad y rendimiento

Fecha: 2026-08-04
Rama: `feat/v25d99.20.3.1-runtime-fixes`
Frontend: https://manager-4f952.web.app
Backend: https://manager-staging-api.onrender.com

## Revisiones verificadas

- Backend/root: `421a02de` (registro seguro e instrumentación), confirmado live por health.
- Frontend: `13e426b` (validación de registro), desplegado en Firebase.
- Firebase: bundle público con SHA-256 `26DC7E1E739B16B1253A892DB2A53898506FF99332511342097E492D2D1ACEF1`.
- El bundle contiene seis assets y no contiene test harness, `_dragRef`, `_pickupPositionInElement` ni `localhost`.

## Registro seguro

La solicitud sin `username` fue reproducida contra Render y respondió `422` con código `AUTH_VALIDATION_ERROR`, `requestId` presente y mensaje público controlado. No se expusieron SQL, nombres de tablas, drivers ni stack traces. El frontend bloquea username fuera de 3–50 caracteres válidos y password fuera de 8–128 caracteres.

## Health público

- `GET /api/v1/health/liveness`: `200`, `{"status":"UP"}`.
- `GET /api/v1/health/readiness`: `200`, `{"status":"UP","database":"UP","redis":"UP"}`.

## Medición observable

La línea base histórica disponible registraba observaciones puntuales aproximadas de mundo 13 s, squad 12 s, auto-select 6 s y comienzo de ronda 4 s; no tenía muestras comparables por percentil. La nueva medición warm se tomó contra la revisión pública actual con diez muestras por operación, sin imprimir tokens.

| Operación | N | HTTP p50 | HTTP p95 | Redis p50 | DB p50 | Payload | Round trips |
|---|---:|---:|---:|---:|---:|---:|---:|
| Estado de carrera | 10 | 209 ms | 392 ms | no aislado | no aislado | no medido | 1 HTTP |
| Squad | 10 | 205 ms | 259 ms | no aislado | no aislado | no medido | 1 HTTP |
| Lineup actual | 10 | 220 ms | 251 ms | no aislado | no aislado | no medido | 1 HTTP |
| Auto-select | 10 | 898 ms | 2.276 ms | no aislado | no aislado | no medido | 1 HTTP |
| Fixtures | 10 | 199 ms | 257 ms | no aislado | no aislado | no medido | 1 HTTP |
| Standings | 10 | 211 ms | 264 ms | no aislado | no aislado | no medido | 1 HTTP |
| World leagues (cold de cuenta nueva) | 1 | 7.329 ms | 7.329 ms | no aislado | no aislado | no medido | 1 HTTP |
| Career start | 1 | 1.769 ms | 1.769 ms | no aislado | no aislado | no medido | 1 HTTP |

No se atribuye el tiempo a Redis o PostgreSQL sin trazas separadas. La instrumentación agregada del backend permite obtener esa separación en logs de la instancia, pero aún no existe exportador externo persistente. El cold start observado históricamente fue de aproximadamente 106,6 s y sigue siendo una limitación de Render Free.

## Resultado de certificación

- Registro/login: corregido y probado públicamente.
- Liveness/readiness: `200`; DB y Redis `UP`.
- SPA: `200`; artifact sin tooling debug.
- Warm status, squad, lineup, auto-select, fixtures y standings: medidos con N=10.
- Temporada pública y editor táctico: ver reportes específicos; el veredicto global permanece rechazado hasta cerrar todos los invariantes tácticos.

## Veredicto

`REJECTED` para cierre PB1.2.3D. El defecto de registro y la medición básica están resueltos, pero swap, banco/XI, persistencia tras recarga/login, responsive completo y una tabla final W/D/L reproducible no quedaron certificados con el recorrido exigido. No se convierten pendientes en PASS por inferencia.
