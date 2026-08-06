# PB1.2.3H7.4 — Contrato root-last y retry

`RedisCareerDataCleanupRepository` es el único dueño del borrado del career
root. El orden es: discovery y ownership, proyecciones, runtime/state/commands,
detail/baseline, generación, mapping/token, índice y root.

Si falla cualquier fase antes del root, el root y el tombstone de discovery se
conservan. Si falla el unlink final del root, el resultado es
`PARTIAL_RETRYABLE` y no se emite éxito HTTP. `CareerSessionService` no invoca
un segundo `deleteById`.

Tras un error, el retry reutiliza el mismo owner y revalida mappings. Un reset
completado elimina el tombstone y un reset repetido es idempotente.
