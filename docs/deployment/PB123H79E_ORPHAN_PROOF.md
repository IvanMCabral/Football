# PB1.2.3H7.9E — Orphan proof status

## Proof matrix

| Surface | Redis evidence | Canonical PostgreSQL evidence | Classification |
|---|---:|---:|---|
| Owner namespaces | 144 owners / 9,478 keys | 144 absent from retained `users` join | `INCONCLUSIVE` pending fresh full-schema authority |
| World | 102 keys | No current owner proven in retained join | `PROTECTED` |
| Career roots | 21 keys | No current owner proven in retained join | `PROTECTED` |
| User projections | 8,955 keys | Owner absence from retained `users` join | `PROTECTED` |
| Career-only namespaces | 11 / 160 keys | No mapping and no current owner in retained evidence | `INCONCLUSIVE` |
| Unknown prefixes | 0 | Not applicable | `NONE` |

## Self-destruction checks

- Wrong PostgreSQL: possible for the local `.env`; it was excluded because it
  identifies `football_manager`/PostgreSQL 15.6, while Render uses `neondb`/
  PostgreSQL 18.4.
- Different Neon branch or snapshot: not verifiable without the authenticated
  Neon console or a read-only connection to the Render database.
- Wrong Redis: ruled out; Render Redis host and Upstash `Manager` host match.
- UUID parser error: no unknown prefixes or contradictory career mappings were
  found in the global census.
- Real users deleted from PostgreSQL: cannot be ruled out from absence alone.
- Irrecoverable deletion: possible; therefore all 9,638 keys remain protected.

## Result

No owner, career, world key, root or projection is proven disposable by the
complete rule set. Cleanup authorization remains `NO`.
