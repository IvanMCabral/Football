# PB1.2.3H7.9B — Targeted public certification

## Resultado

**REJECTED (certificación incompleta; sin P0 reproducido).**

La ejecución pública completó dos temporadas cortas y la transición a una
tercera. Los gates funcionales observables pasaron, pero no se puede emitir
`APPROVED` porque faltan mediciones físicas de viewport, crecimiento Redis
R0/R1/R2/R3 y trazas de navegador para N=10. Además, tres APIs públicas
superan los objetivos de latencia definidos.

## Identidad del runtime

- Runtime productivo y Render: `b6c413f3f8e0a6037b2f0362c6cc0a7893bf3af4`.
- Root auditado: `2b2ece36c74c6b3e822a13307bd6c151d34b6efd`.
- Diferencia runtime/root: sólo documentación y evidencia; no hay cambios de
  código productivo en ese rango.
- Render: una instancia, contrato single-instance, health warm OK.
- Frontend: `8f36ca7b66ff5519722cbd1e7a7675d430eb6f18`.
- Firebase: artefactos públicos byte-equivalentes al build local de producción
  (index 1185 bytes, main 76975 bytes, chunk principal 2722 bytes).

## Funcionalidad pública

- Cuenta efímera creada por `/auth/register` y flujo normal de carrera.
- España, división de 4 equipos, 6 rondas por temporada.
- Temporada 1: 6/6 rondas, 12/12 fixtures `COMPLETED`, SSE único por ronda,
  detalle HTTP 200 y minute-by-minute no vacío hasta minuto 90.
- Temporada 2: 6/6 rondas con los mismos invariantes.
- Temporada 3: transición 200, ronda 1 `PRE_MATCH`, seis fixtures nuevos y
  standings en cero.
- Cambio táctico y sustitución live: HTTP 200 observados en la ronda 2 de la
  temporada 1. Lesión natural: no apareció; no se fabricó ninguna.
- Reset C1: `DELETE /career/reset` 204. C2 recibió un roundId distinto y SSE
  en minutos 0, 1 y 2. No reapareció estado C1 mediante APIs públicas.
- Owner B: su carrera permaneció 200/PRE_MATCH después del reset de Owner A.
- Las carreras efímeras de la certificación fueron eliminadas mediante el
  lifecycle público.

## Hallazgos

- **P0:** 0 reproducidos.
- **P1:** 0 defectos de gameplay o aislamiento reproducidos.
- **P2:** dashboard p50/p95 1163/1952.2 ms; auto-select 2718.6/3183.3 ms;
  confirm 2538.7/2590.5 ms. Los tres exceden los objetivos declarados.
- **P2 de evidencia:** no se completaron viewports 1366, 1024, 768, 430, 390
  y 360; sólo se conserva la observación 1920 de H7.9.
- **P2 de operación:** Upstash no permitió una lectura nueva del dashboard en
  esta sesión; los puntos R1/R2/R3 no deben inferirse desde DBSIZE.
- **P2 de observabilidad:** handler→POST y handler→SSE físicos no están
  disponibles desde APIs públicas; se registraron sólo tiempos de red y SSE.

## Evidencia

Los datos sanitizados están en `docs/deployment/evidence/pb123h79b/` y los
detalles por gate en los cinco reportes complementarios de este cierre.
