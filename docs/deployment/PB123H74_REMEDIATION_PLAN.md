# PB1.2.3H7.4 — Remediación local

Este gate cierra los P1 locales de H7.3 relacionados con carreras de ownership,
callbacks tardíos, root-last, reintentos y timeouts. No incluye Render, Upstash,
Neon, Firebase, cleanup remoto, N=10 ni una garantía distribuida.

## Alcance implementado

- Coordinación reactiva por owner y por career, con reentrada segura y timeout.
- Tombstone `career-cleanup:{ownerId}` con TTL acotado durante reset.
- Validación de ownership y generación dentro de la cola de career.
- Generación `career-generation:{careerId}` para rechazar callbacks obsoletos.
- Cleanup con root único y último; `CareerSessionService` no repite el delete.
- Cardinalidad máxima de 256 y compensación con token Redis compare-and-delete.
- Renovación coordinada de root, mapping e índice en writers derivados y TTL.

## Decisión operacional

El contrato actual es **single-instance**. El startup de producción rechaza un
modo declarado multi-instance mientras no exista un lock distribuido con fencing.

## Validación

La evidencia está en `PB123H74_REAL_REDIS_INTEGRATION.md` y
`PB123H74_FINAL_LOCAL_REVIEW.md`. Los servicios remotos permanecen sin tocar.
