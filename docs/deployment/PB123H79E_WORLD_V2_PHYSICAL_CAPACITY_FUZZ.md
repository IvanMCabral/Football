# World V2 physical capacity fuzz

`WorldStorageV2CapacityFuzzIntegrationTest` now contains a fresh physical
Redis fuzz path. The seed is generated at runtime and can be replayed with
`-Dworld.capacity.seed=<seed>`.

Fresh run:

| Metric | Result |
|---|---:|
| Seed | 2562823066121781444 |
| Cases | 1000 |
| Admitted | 750 |
| Blocked | 250 |
| Near-boundary cases | 500 |
| Unsafe admissions | 0 |
| Max physical minus planned | -388 bytes |
| Worst planned | 63113162 bytes |
| Worst physical | 63112774 bytes |
| Blocked mutations | 0 |

Each admitted case writes a legacy payload to ephemeral Redis, runs the
production migration path, measures legacy/prepared/catalog/committed keys
with `MEMORY USAGE`, and compares the measured transition peak with the
planned peak. Each blocked case verifies source byte identity and absence of a
prepared key. The first 500 cases deliberately operate near the quota
boundary; the generated world dimensions vary teams, players, custom entities,
aliases, removals, deltas, traits, skills and Unicode text through the existing
fuzz fixture.

The earlier model-only seed runs remain historical evidence; this document
records the new physical Redis gate. The final complete-suite replay used the
seed shown above.
