# PB1.2.3H — Catalog bootstrap and prefetch

## Implemented contract

The existing per-user world endpoints remain the source of truth. No global catalog endpoint was invented because the current catalog is coupled to the authenticated user's world snapshot. The frontend `WorldCatalogService` caches only reusable metadata:

- leagues for the current user world;
- teams and OVR summaries per league;
- division previews per division/league key.

Plantels, traits, detailed statistics and career snapshots are not put in this cache.

## Lifecycle

- A singleton root service prevents duplicate simultaneous requests from dashboard, career setup and selectors.
- `shareReplay({bufferSize: 1, refCount: false})` shares the resolved value with later subscribers in the session.
- Dashboard calls `prefetch()` without blocking shell rendering.
- Seed/retry calls `invalidate()` so stale metadata cannot survive a world reset.
- There is no fabricated TTL: the current policy is session lifetime plus explicit invalidation.

## Measurement status

The browser surface did not expose `PerformanceResourceTiming`, payload byte sizes or response cache headers. Therefore hit/miss counts and payload sizes are not claimed as measured evidence. The unit tests verify request de-duplication and invalidation; the public warm navigation evidence verifies that the consuming screens load after the change.

## Follow-up

If the catalog becomes global and immutable, a small authenticated bootstrap endpoint with an explicit version, ETag and server TTL can be added in a later scope. It must remain separate from private career state.
