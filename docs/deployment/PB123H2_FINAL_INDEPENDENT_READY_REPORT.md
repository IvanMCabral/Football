# PB1.2.3H2 — Informe final independiente

## Veredicto

**COMPLETED WITH ISSUES**

La remediación es reproducible y no altera gameplay. El POST warm y el SSE
quedan por debajo de los objetivos; la navegación completa continúa por encima
del objetivo principal, pero el exceso está cuantificado en la espera previa al
POST y no en el motor.

## Versiones exactas

- Backend / Render live revision: `9a686e70` (`9a686e70c64ae422dbedab1b2f79c99fe13d3407`).
- Frontend / Firebase release source: `79f772d` (`79f772d5165007a47a240df0eb8f7a14cf1f82f2`).
- Firebase artifact: 53 archivos, 0 source maps, 0 referencias a test harness.
- URLs: `https://manager-4f952.web.app` y
  `https://manager-staging-api.onrender.com`.

## Causa exacta de los 5.278 ms

La medición H1 agrupaba dos tramos. El tramo T1→T7 quedó medido después de H2:
POST p50 302 ms, conexión SSE p50 304 ms y primer SSE p50 305 ms. El resto,
que explica el click→live externo p50 4.050 ms, ocurre antes de T1 en la ruta
de carrera: carga/confirmación de lineup, status y fixtures necesarios para
construir el view model. No hay evidencia de duplicación ni de espera del motor
fuera de ese tramo.

## E2E público

Se ejecutaron diez inicios warm, con estado inicial visible, SSE conectado,
minuto monotónico, salida a resumen, hard reload/recuperación y comienzo de la
fecha siguiente. La campaña completó dos temporadas y la tercera se utilizó
para alcanzar N=10 sin reutilizar un start idempotente como muestra falsa.

## Redis y PostgreSQL

La traza backend incluye contadores y duración de Redis, serialización,
PostgreSQL y cargas de lineup/fixtures/equipos/jugadores. En el test de
idempotencia esas etapas son `-1` porque los puertos están simulados; no se
inventan tiempos de infraestructura. En staging, readiness respondió
`{"status":"UP","database":"UP","redis":"UP"}` después del wake-up.

## Validación

- Backend: **2.586 tests, 0 failures, 0 errors, 4 skipped**; `test-compile`
  verde. El suite se ejecutó con `-Djava.io.tmpdir=D:\Temp\manager-tests`
  porque el disco temporal del sistema no tenía espacio suficiente.
- Frontend: **1.053 SUCCESS, 0 failures, 2 skipped**.
- Builds development y production: verdes.
- `npm audit --omit=dev`: 0 critical, 0 high, 0 moderate, 0 low.
- Liveness y readiness públicas: 200 después de calentar Render.
- Sin cambios de probabilidades, fixtures, reglas ni resultados.

## Riesgos y backlog P2

El único pendiente H2 es reducir la espera Angular previa a T1 para que el
click→live externo alcance el objetivo de 1.500 ms. No es un P0 ni una ventana
de inconsistencia: el motor ya está registrado y el stream disponible antes de
responder.
