# PB1.2.3F — Revisión de nombres de fixture

## Resultado

El flujo público canónico (`partidos`, partido en vivo, resumen y tabla) mostró nombres reales: Real Madrid, Real Oviedo, FC Barcelona, RCD Mallorca, Villarreal CF, Elche CF y el resto de la Primera División. No apareció `Team` en el recorrido del piloto.

## Hallazgo histórico

El análisis de `FixtureQueryHelper` conserva un fallback al ID cuando un mapa de nombres no contiene el equipo. El endpoint histórico/individual de fixture puede presentar el nombre genérico `Team` fuera del flujo canónico. Es un **P2 no bloqueante**, documentado para una futura corrección de consulta y cobertura de integración; no se alteró gameplay ni se hizo un cambio riesgoso durante el piloto.

## Criterio de aceptación

La corrección futura debe cubrir división completa, fixture individual y detalle, con nombres reales o un fallback controlado que no use `Team`. La evidencia PB1.2.3F clasifica el comportamiento residual sin presentarlo como resuelto.
