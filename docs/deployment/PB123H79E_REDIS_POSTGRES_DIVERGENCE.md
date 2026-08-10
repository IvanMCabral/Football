# PB1.2.3H7.9E — Redis/PostgreSQL divergence

## Retained read-only evidence

The global Redis census reconciled `9638/9638` keys with `DBSIZE` and found:

- 144 distinct Redis owner UUIDs;
- 9,478 owner-scoped keys;
- 73 PostgreSQL users in the retained Neon `manager-staging` SELECT-only
  inventory;
- Redis owner → PostgreSQL users join: 0 present, 144 absent;
- 21 known PB123 audit owners: zero Redis keys;
- 22 career IDs: 11 valid owner mappings, 11 missing mappings, 0 contradictory
  mappings;
- unknown key prefixes: 0.

These results are retained evidence, not a new cleanup authorization. The
current Render logs identify the same Neon host and database (`neondb`) used by
the service. A fresh full-schema SELECT against that exact database was not
possible because the Neon console session is currently unauthenticated.

## Classification

The 144 owner namespaces and 11 unmapped career namespaces are currently
`INCONCLUSIVE`, not `PROVEN_ORPHAN`. PostgreSQL absence is material evidence,
but it is insufficient to delete data until the canonical runtime database,
branch and all ownership surfaces are freshly verified together.

No Redis or PostgreSQL mutation was executed.
