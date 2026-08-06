# PB1.2.3H7.4 — Integración Redis real

La matriz se ejecutó contra Redis efímero real y serializers productivos:

| Caso | Resultado |
|---|---|
| índice 255/256/257 | PASS |
| saves concurrentes | PASS; uno de dos desde 255 |
| compensación de mapping tokenizada | PASS en rechazo de cardinalidad |
| ownership touch con TTL | PASS |
| generación stale después de reset | PASS |
| cleanup root-last y aislamiento A/B | PASS |
| cleanup batch máximo 100 y accounting/DBSIZE | PASS |
| timeout `Flux.never` focal | PASS con adapter configurable |
| registries owner/career | PASS en focales existentes |

Mocks se reservaron para errores deterministas del driver y publishers que nunca
terminan. No se accedió a Upstash ni a otros proveedores.
