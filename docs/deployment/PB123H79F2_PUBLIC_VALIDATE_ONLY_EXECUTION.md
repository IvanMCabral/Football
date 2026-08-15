# PB1.2.3H7.9F.2 — Public VALIDATE_ONLY execution

## Result

`PB1.2.3H7.9F.2 PUBLIC VALIDATE_ONLY FAILED`

The independently approved runtime commit
`24560a2b69fd94fce273ebb6aa7fe41b9e81c7ed` was pushed and deployed exactly to
the Render service `manager-staging-api` (`srv-d9nvldtaeets73coqiog`). Render
reported the same full commit as `Live`. The service remained on the Free plan
in Oregon, with one instance and autoscaling disabled.

The dedicated runner was started once with profile `world-v2-canary`, explicit
enablement and mode `VALIDATE_ONLY`. No execution confirmation was supplied.
The process failed closed with `ORCHESTRATOR_FAILED`, exit code 1,
`sourceSha=null`, `currentStorage=-1` and `orchestratorInvocations=0`.

## Root cause

The dedicated process was launched locally because Render Free does not provide
one-off jobs or a web shell. Its Redis and Upstash Management API inputs were
the production read paths, but its PostgreSQL configuration was deliberately a
non-production placeholder with Flyway disabled. The product source probe
rebuilds the canonical catalog through `DurableCanonicalWorldCatalogSource`,
which loads the durable PostgreSQL authorities. That reconstruction therefore
could not complete and the runner converted the precondition error to the
sanitized fail-closed result above.

The failed Maven command that preceded the real launch was a command-line
quoting error and never started a JVM or instantiated the runner. The corrected
command started exactly one runner instance. The gate forbids a second runner,
so no retry with production database credentials was attempted.

## Activation and call accounting

| Field | Observation |
|---|---|
| Profile | `world-v2-canary` |
| Enabled | `true` |
| Mode | `VALIDATE_ONLY` |
| Execute confirmation present | No |
| Runner result | `ORCHESTRATOR_FAILED` |
| Exit code | 1 |
| Source-probe attempts | 1 |
| Capacity-provider calls by runner | 0 |
| Orchestrator migration calls | 0 |
| `PREPARED` observed | No |
| `COMMITTED` observed | No |

## Zero-migration proof

The exact owner key stayed a Redis string with PTTL `-1`. Its SHA-256 remained
`2fe7dff53f2c6f07d222337cdda0a6963841e229aef721d2e11a395ba3d4d68f`,
its state remained `LEGACY`, the certified catalog key remained absent and
`DBSIZE` remained 9,555. Provider `current_storage` was unchanged between the
fresh T0 and T1 samples. No Redis, PostgreSQL or catalog write occurred.

## Normal service isolation

The deployed web service started under profile `prod`. A Render log search for
the canary-runner marker returned no matching application log, proving that
normal service startup did not activate the dedicated runner.

No owner identifier, provider credential, Authorization header or raw world
payload is present in this report or its evidence.
