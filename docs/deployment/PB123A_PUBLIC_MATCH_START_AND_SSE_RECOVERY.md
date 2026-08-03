# PB1.2.3A — Public match start and SSE recovery

Fecha de validación: 2026-08-03 20:47 ART  
Frontend: `https://manager-4f952.web.app`  
Backend: `https://manager-staging-api.onrender.com`  
Rama: `feat/v25d99.20.3.1-runtime-fixes`

## Incidente

La pantalla pública presentaba dos síntomas relacionados con el flujo de
partido:

1. El cliente SSE enviaba `Cache-Control: no-cache`. Ese header no pertenecía
   a la allowlist productiva de CORS y provocaba un preflight que no coincidía
   con el request real.
2. El cliente trataba los errores de CORS, autenticación y ronda inexistente
   como errores transitorios. Eso podía abrir un ciclo de reconexión indefinido
   y multiplicar conexiones para una misma ronda.
3. El dashboard mostraba `currentRound + 1`. El backend ya devuelve la próxima
   fecha canónica pendiente; después de completar la fecha 2, la UI podía
   mostrar “Jugar Fecha 4”.
4. Dos POST concurrentes de arranque podían crear motores y callbacks
   duplicados para la misma ronda.

## Correcciones aplicadas

### CORS y SSE

- Se eliminó `Cache-Control` de los headers del `fetch` SSE. Se conserva
  `Accept: text/event-stream` y el bearer token.
- La allowlist común de backend contiene `Authorization`, `Content-Type`,
  `Accept`, `Origin` y `X-Requested-With`.
- `CorsWebFilter` y las respuestas de seguridad 401/403 usan la misma
  allowlist.
- HTTP 400/401/403/404/409 y fallos `TypeError`/CORS terminan la suscripción
  de forma controlada. Solo los fallos transitorios 5xx/red usan backoff
  acotado.
- Las suscripciones simultáneas a una misma URL comparten un único transporte
  SSE y el último estado emitido.
- La liberación de la suscripción cancela el `AbortController` y todos los
  timers asociados.

### Arranque idempotente

- `RoundController` coordina arranques concurrentes con un `Mono` cacheado por
  `roundId`.
- Si la ronda ya está registrada, el segundo POST devuelve el último estado
  del motor existente sin crear scheduler, motor o callbacks adicionales.
- `RoundEngine` conserva el último `RoundState` observable para esa respuesta.
- La ausencia de un motor para un stream devuelve 404, no un SSE 200 vacío que
  invite a reintentos inútiles.

### Fecha mostrada en dashboard

El dashboard ahora consume `currentRound` como fecha pendiente canónica. Las
etiquetas son:

- `Jugar Fecha N` para una fecha pendiente;
- `Continuar Fecha N` y `Partido en curso` cuando la ronda está viva;
- `Continuar Temporada N` al finalizar la temporada.

No se modificaron probabilidades, reglas deportivas ni el motor de simulación.

## Evidencia de código y tests

Commits de implementación:

- Backend `531839bb` — `Prevent duplicate round starts and SSE retry loops`.
- Backend `c301601a` — `Align CORS headers across security responses`.
- Frontend `1b7325d` — `Stabilize public round streaming lifecycle`.

Validación local:

| Suite | Resultado |
| --- | --- |
| Backend `mvn -q -DskipTests test-compile` | OK |
| Backend suite completa | 2.580 tests, 0 failures, 0 errors, 4 skipped |
| Backend focalizada CORS/idempotencia | OK |
| Frontend tests | 1.038 SUCCESS, 0 failures, 2 skipped |
| Frontend development build | OK |
| Frontend production build | OK |
| Inspección del artefacto production | OK; 52 archivos inspeccionados |

## Evidencia de despliegue

### Backend / Render

El dashboard de Render muestra el deployment `c301601a02ece1fc7160f7e72806b7cd2e499cf0`
como `live` para `manager-staging-api` en plan Free.

Checks públicos posteriores al deployment:

- `GET /api/v1/health/liveness` → HTTP 200, `{"status":"UP"}`.
- `GET /api/v1/health/readiness` → HTTP 200,
  `{"status":"UP","database":"UP","redis":"UP"}`.
- Preflight SSE con origin exacto `https://manager-4f952.web.app`, método GET
  y header `authorization` → HTTP 200, origin exacto, credentials habilitadas,
  método GET y header `authorization` permitido.
- Preflight heredado solicitando `authorization,cache-control` → HTTP 200,
  pero solo devuelve los headers permitidos. El frontend ya no solicita
  `cache-control`.
- GET SSE con bearer inválido → HTTP 401, body público controlado
  `{"code":"UNAUTHORIZED","message":"No autenticado.",...}` y CORS exacto;
  no se expone stack trace ni causa interna. El header de allowlist observado
  después del deployment fue:
  `Authorization,Content-Type,Accept,Origin,X-Requested-With`.

### Frontend / Firebase Hosting

- Hosting público: `https://manager-4f952.web.app` → HTTP 200.
- El bundle principal y el chunk de dashboard corresponden al commit
  `1b7325d`; el artefacto production no incluye el harness de debug.
- La tab abierta antes del deployment podía conservar el bundle anterior. Una
  recarga completa es necesaria para que el navegador descarte ese chunk en
  memoria; la lógica desplegada ya no suma uno a `currentRound`.

### Smoke público autenticado

Se ejecutó contra la API pública con una cuenta efímera generada para la
prueba, sin escribir directamente en PostgreSQL ni Redis y sin imprimir tokens:

- register → HTTP 200;
- login → HTTP 200;
- `/auth/me` → HTTP 200;
- creación de carrera → HTTP 201/aceptada;
- estado de carrera → HTTP 200, `currentRound=1`, `PRE_MATCH`;
- auto-select 4-4-2 → 11 jugadores;
- lineup current/confirm → 11 jugadores y `confirmed=true`;
- fixtures de fecha 1 → 2 partidos;
- standings → 4 equipos;
- POST de arranque de ronda → HTTP 200, 2 partidos;
- POST repetido para el mismo `roundId` → misma ronda compartida por la
  coordinación idempotente;
- SSE autenticado → HTTP 200, `text/event-stream`, primer estado a minuto 0 y
  siguientes estados a minuto 1 con datos `data:`.

El stream público entregó `RoundState` en tiempo real, incluyendo minuto,
marcador, posesión, formaciones, slots y ratings. La captura se detuvo con un
timeout de observación después de recibir eventos; no se dejó una conexión de
prueba abierta.

## Diagnóstico final

La discrepancia “Fecha 4” tenía dos componentes: una tab que conservaba un
bundle anterior y, en el código anterior, el incremento artificial de
`currentRound`. El backend seguía siendo la fuente canónica. El incremento fue
eliminado y el flujo SSE dejó de generar reconexiones ante errores que no se
pueden reparar reintentando.

## Estado

- P0 del incidente: cerrado.
- CORS del SSE: cerrado.
- Retry storm y conexiones duplicadas: cerrado.
- Arranque de ronda idempotente: cerrado.
- Fecha mostrada en dashboard: corregida en el frontend desplegado.
- Base de datos y Redis: readiness público UP; no se realizó mutación manual.
- No hay cambios de gameplay ni de probabilidades.

## Pendientes honestos

Los motores de ronda son estado en memoria del proceso Render. Si Render se
reinicia, una ronda que todavía no esté persistida no puede reconstruirse desde
el proceso anterior; el cliente recibe 404 y debe volver al flujo de carrera
canónico. La durabilidad completa de una LiveSession continúa siendo una
decisión de la siguiente fase de infraestructura, no se simula como resuelta en
este cierre.
