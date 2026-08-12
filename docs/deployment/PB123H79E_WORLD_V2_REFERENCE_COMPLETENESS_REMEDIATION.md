# PB1.2.3H7.9E World V2 reference-completeness remediation

Date: 2026-08-11

## Scope

This remediation consumes the findings in `PB123H79E_WORLD_V2_FINAL_DEFINITIVE_INDEPENDENT_AUDIT.md` without changing gameplay, the frontend, public data, or provider state. The historical rejected audit remains unchanged.

## Changes

- Durable roots now come from productive `@WorldPersistedWriter` declarations discovered across the product classpath. There is no migration-owned root whitelist.
- A source guard examines all productive Redis writers and fails when a writer has no explicit classification.
- The type graph walks superclasses and resolves classes, parameterized types, generic arrays, type variables, wildcards, arrays, collections, maps, optionals, atomic references, and nested combinations.
- Durable identity leaves use product metadata from `com.footballmanager.domain.model.metadata`. Map keys, values, and nested routes are classified explicitly.
- `requireComplete()` rejects empty discovery, unresolved generic paths, unclassified identity leaves, stale classifications, missing validators, extra validators, competing validators, and unclassified writers.
- The two prior false passes are regression fixtures: inherited fields and external holders are now discovered and rejected until classified.
- Negative-control authority V2 has one canonical row per material invariant. Redis-dependent controls execute against ephemeral Redis; source-only controls are limited to deterministic properties and discovery authorities.
- Separate-JVM PREPARED recovery now reaches PREPARED through the product orchestrator and a product transition observer.

## Measured authority

| Metric | Result |
|---|---:|
| Productive persisted writers | 19 |
| Classified writers | 19 |
| Unclassified writers | 0 |
| World-reference roots | 4 |
| Reachable models | 33 |
| Reachable fields | 238 |
| Containers | 82 |
| Durable identity leaves | 126 |
| Reference paths | 57 |
| Validators | 57 |
| Unresolved generics | 0 |
| Unvalidated references | 0 |
| Extra validators | 0 |

Production models currently contribute no inherited persisted field, so the measured production inherited-field count is zero. Dedicated inheritance and generic-superclass fixtures prove that those surfaces are discovered and fail closed.

## Validation

- Focused World V2 block: 226 tests, 0 failures, 0 errors.
- Complete backend suite: 291 reports, 2889 tests, 0 failures, 0 errors, 4 skipped.
- Capacity fuzz: 500 cases, 0 unsafe admissions, maximum physical-minus-planned `-388` bytes.
- Negative authority: 33 unique controls, 27 Redis physical, 6 source-proven, 0 duplicates, 0 proxies, 0 false passes.
- PREPARED recovery: two independent JVMs, product path, recovery successful.
- Seed: isolated N5, full class N3, and post-heavy pass.
- RNG: `ShotCoordinateAttachmentTest` 3/3.

## Safety boundary

No public migration or cleanup is authorized. No Upstash, PostgreSQL, Render, billing, frontend, gameplay, probabilities, fixtures, calendar, or dataset state was changed.
