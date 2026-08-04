# PB1.2.3F — Informe del piloto público controlado

## Resultado

**COMPLETED WITH ISSUES** — el recorrido público terminó sin pérdida de estado, sin 500, sin doble avance y sin loops. Se mantiene un P2 histórico de nombres genéricos en un endpoint de fixture no canónico.

## Perfiles

- **Novato:** completado de registro a resumen y reingreso.
- **Táctico:** completado; el editor mostró 12 formaciones, química, roles, zonas, banco y edición visual.
- **Móvil (390 × 844):** completado; el editor se ajustó al viewport sin overflow propio. Se corrigió el ancho del status bar en el commit frontend `2e730f6`; el hosting público observado durante el piloto todavía sirve `d444e69` hasta que Firebase publique el commit exacto.

## Resiliencia

- Lesión real de Jude Bellingham en 29'/pausa visible en 31'; descartar dejó el partido pausado y `Reanudar todos` permitió finalizar.
- Refresco/retorno al resumen no duplicó fecha ni resultado.
- El resumen mostró los mismos resultados y tabla que el partido final.
- El despertar de Render produjo una espera lenta, pero liveness/readiness volvieron a 200 y la aplicación continuó.

## Métricas de estado

- Sin puntuación sintética ni 0-0 inesperado fuera del partido observado.
- Sin doble clic efectivo, doble guardado, navegación corrupta ni datos de alineación perdidos.
- Sin error interno expuesto en la interfaz.

## Revisión de UX

Los estados existentes `Cargando datos...`, `Cargando equipos...`, `Cargando carrera...` y `Partidos pausados` son comprensibles. El editor visual y el modal de lesión explican la acción disponible. Se detectó y corrigió una carrera de subscriptions en `Squad` que podía mostrar transitoriamente “No hay una carrera activa” junto a una plantilla válida; el estado de carrera ahora se comparte entre las vistas.

## Limitaciones

La captura del stream se limita a estado renderizado por el navegador; no se inspeccionaron cookies, tokens, almacenamiento ni payloads privados. La evidencia sanitizada queda en `docs/deployment/evidence/pb123f/pb123f_sse_archive.json`.

## Validación local de esta corrección

- Backend: `mvn -q -DskipTests test-compile` OK; `mvn -q test` OK — 2585 tests, 0 fallos, 0 errores, 4 skipped.
- Frontend: encoding guard OK; build development OK; build production OK; `npm test -- --watch=false --browsers=ChromeHeadless` OK — 1046 SUCCESS, 0 fallos, 2 skipped.
- El build production no generó el chunk de `test-harness`.
- `git diff --check` sin salida en ambos repositorios antes del commit.
