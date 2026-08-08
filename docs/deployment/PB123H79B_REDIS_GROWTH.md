# PB1.2.3H7.9B — Redis growth

| Punto | Storage | DBSIZE | Fuente | Estado |
|---|---|---:|---|---|
| R0 H7.9 | 115 MB / 256 MB | 6227 | Dashboard Upstash retenido | observado |
| R1 temporada 1 | no disponible | no disponible | dashboard no accesible | no medido |
| R2 temporada 2 | no disponible | no disponible | dashboard no accesible | no medido |
| R3 reset H7.9B | no disponible | no disponible | dashboard no accesible | no medido |

No se ejecutaron MEMORY STATS, EVAL, FLUSHDB, FLUSHALL, SCAN global ni
escrituras manuales. No se extrapolan bytes por clave y no se declara que el
reset haya liberado storage del proveedor sin una lectura R3. La falta de
lecturas nuevas es un pendiente operativo P2.
