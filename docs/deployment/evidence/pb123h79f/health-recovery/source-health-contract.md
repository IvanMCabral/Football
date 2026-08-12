# Source health contract inspected

- `HealthController.liveness()` returns an immediate reactive 200 response.
- `DatabaseHealthProbe` executes `SELECT 1`, applies a two-second timeout and
  returns false on errors.
- `RedisHealthProbe` uses a five-second ephemeral probe key, applies a
  two-second timeout and cleans up the key; failures are fail-closed.
- No source files were modified by the H7.9F recovery gate.
