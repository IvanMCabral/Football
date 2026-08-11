# World Seed Idempotency Timeout Root Cause

Classification: **PRODUCT_REGRESSION — DETERMINISTIC_SLOW_PATH**.

The second and third `/seed/all` calls rewrote ten complete seed datasets to PostgreSQL and Redis even when the stored snapshot already exactly matched the final seed material. The five-second blocking read therefore timed out reproducibly; increasing that timeout would only hide redundant work.

`WorldSeedCompletenessChecker` now builds the exact final expected seed state using the same last-writer-wins semantics as the existing ordered seed passes. `WorldSeedService` compares the current snapshot before persistence and returns the normal per-league result when it is already complete. The initial seed path is unchanged; incomplete or divergent state still executes the existing persistence pipeline.

Fresh stability evidence:

- isolated test N=10: 10/10 PASS; Maven wall time 44.376–47.436 s, mean 45.331 s (includes isolated Spring/PostgreSQL/Redis startup);
- surrounding class N=5: 5/5 PASS; 54.443–54.603 s, mean 54.534 s;
- immediately after the focused World V2 suite: PASS, 46.262 s Maven wall time;
- full backend suite: PASS with 0 failures and 0 errors.

The fixed five-second WebTestClient contract was retained.
