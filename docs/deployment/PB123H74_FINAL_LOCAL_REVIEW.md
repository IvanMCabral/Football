# PB1.2.3H7.4 — Revisión local final

## Veredicto

**PB1.2.3H7.4 LOCAL REMEDIATION COMPLETE WITH ISSUES**

Los P1 locales de ownership, stale writers, root-last, compensación,
coordinación, TTL y timeouts están implementados y cubiertos por pruebas
focales. El contrato explícito sigue siendo single-instance.

## Evidencia

- `mvn -q -DskipTests test-compile`: PASS.
- Focal H7.4 (cleanup mock/real, lifecycle, registries, HTTP y adapters): PASS.
- Redis real: 9 escenarios en `RedisCareerDataCleanupRepositoryRealIntegrationTest`, PASS.
- Timeout focal con `Flux.never`: PASS.
- Tres ejecuciones de `ShotCoordinateAttachmentTest`: se repetirán tras el commit final.
- Suite completa: se ejecutará después de cerrar los cambios de código.
- `git diff --check`: PASS en el working tree.
- Validación final: `mvn -q -DskipTests test-compile` PASS.
- Suite completa: 2619 tests, 0 failures, 0 errors, 4 skipped (264 reports).
- `ShotCoordinateAttachmentTest`: 3 ejecuciones independientes PASS (3/3, exit 0).

## Issues no locales

- No se ejecutó Render, Upstash, Neon, Firebase ni cleanup público.
- No se implementó lock distribuido; no se declara garantía multi-instance.
- La persistencia de comandos conserva un fallback owner-scoped cuando el estado
  histórico no aporta careerId; el camino normal ya usa validación career-scoped.
- Los adapters legacy de detail mantienen rutas históricas `KEYS` fuera de este
  cleanup.
- N=10, SSE público y restore drill permanecen fuera de este gate.
