# PB1.2.3H7.9F.2.1 — Production-context recovery

Date: 2026-08-15

## Result

`PB1.2.3H7.9F.2.1 BLOCKED_PRODUCTION_CONTEXT`

Discovery found no safe, currently available process boundary that can run the
approved one-shot canary with the live service environment. No runner was
started during this gate.

## Failure lineage

The preceding H7.9F.2 process used the approved runner and failed closed with
`ORCHESTRATOR_FAILED`. It reached `ProductWorldV2CanarySourceProbe`, but the
local process did not have the production PostgreSQL configuration required to
rebuild the canonical catalog. Capacity-provider calls and migration
orchestrator calls were both zero. Redis, PostgreSQL and the catalog were not
modified.

Source tracing verifies that this is a context failure rather than a World V2
semantic failure:

- `ProductWorldV2CanarySourceProbe` inspects the Redis world, reads the Redis
  career, rebuilds the canonical catalog and reads the exact owner value;
- `DurableCanonicalWorldCatalogSource` rebuilds through
  `LoadBaseDataService.loadCanonical`, backed by the normal PostgreSQL loaders;
- `UpstashManagementCapacityProvider` separately requires the Management API;
- migration is not invoked in `VALIDATE_ONLY`.

Relevant source references are
`ProductWorldV2CanarySourceProbe.java:59-81`,
`DurableCanonicalWorldCatalogSource.java:24`,
`WorldV2CanaryRunner.java:129-165` and
`UpstashManagementCapacityProvider.java:27-29`.

## Render discovery

Authenticated, read-only inspection of `manager-staging-api` established:

| Property | Observed state |
|---|---|
| Service | `manager-staging-api` |
| Service ID | `srv-d9nvldtaeets73coqiog` |
| Runtime | Docker |
| Plan | Free |
| Branch | `feat/v25d99.20.3.1-runtime-fixes` |
| Live runtime | `24560a2b69fd94fce273ebb6aa7fe41b9e81c7ed` |
| Instances | 1 |
| Autoscaling | Off |
| Dockerfile | `./Dockerfile` |
| Build context | `.` |
| Root directory | repository root |
| Docker command override | none |
| Pre-deploy command | unavailable/disabled |
| Auto-deploy | current UI reports On Commit; no setting was changed |

The image entry point is the repository Dockerfile's `java -jar /app/app.jar`
launcher. No alternate canary image or build was created.

## Execution mechanisms

| Method | Classification | Evidence and decision |
|---|---|---|
| A. Existing-service one-off job | `SUPPORTED_BUT_UNAVAILABLE_ON_CURRENT_PLAN` | Render exposes One-Off Jobs and states that they use the service's latest build image, but the authenticated UI says they are not supported for Free instances and require Starter. |
| B. Existing-service shell | `SUPPORTED_BUT_UNAVAILABLE_ON_CURRENT_PLAN` | The Shell page is present, but the authenticated UI says Shell is not supported for Free instances and requires Starter. |
| C. Temporary dedicated Render process | `REQUIRES_INFRASTRUCTURE_CHANGE` | It would create another resource. The service has no linked or available environment group, so exact secret inheritance is not available. |
| D. Secure injected provider environment | `REQUIRES_INFRASTRUCTURE_CHANGE` | No current provider-side job or shared-secret boundary exists for this process. Establishing one would change service/environment configuration. |
| E. Local process with copied production secrets | `UNSUPPORTED` | Production credentials are not available to the local process through a supported inherited boundary, and this gate expressly forbids automatic manual secret copying. |

Chosen method: none. Options A and B are the safest because they would reuse the
already deployed image and service environment, but both are disabled by the
current Free instance type.

## Environment names

The live service exposes these names; values were neither read nor recorded:

`APP_CORS_ALLOWED_ORIGINS`, `APP_RATE_LIMIT_ENABLED`,
`APP_WORLD_IMPORT_THREE_LEAGUE`, `DB_HOST`, `DB_NAME`, `DB_PASSWORD`,
`DB_PORT`, `DB_SSL_MODE`, `DB_USER`, `JWT_EXPIRATION`,
`JWT_REFRESH_EXPIRATION`, `JWT_SECRET`, `REDIS_HOST`, `REDIS_PASSWORD`,
`REDIS_PORT`, `REDIS_SSL`, `REDIS_USERNAME`, `SERVER_ADDRESS` and
`SPRING_PROFILES_ACTIVE`.

The dedicated `VALIDATE_ONLY` process additionally needs these activation or
operational names:

`WORLD_V2_CANARY_ENABLED`, `WORLD_V2_CANARY_MODE`,
`WORLD_V2_CANARY_OWNER_ID`, `WORLD_V2_CANARY_DATABASE_ID`, `UPSTASH_EMAIL` and
`UPSTASH_API_KEY`.

Variable-name accounting for an executable provider context:

- required names: 25;
- present on the normal service: 19;
- missing canary-specific names: 6;
- PostgreSQL available to normal service: yes;
- Redis available to normal service: yes;
- Upstash Management API credential available to normal service: no;
- linked environment groups: none.

The normal `SPRING_PROFILES_ACTIVE=prod` value would also need a process-local
profile override that includes `world-v2-canary`; no service setting was
changed.

## Dependency classification

| Dependency | Required before source proof completes | Current normal service | Current dedicated process path |
|---|---:|---:|---:|
| PostgreSQL / Neon | Yes | Available | Not safely inheritable |
| Redis / Upstash runtime | Yes | Available | Not safely inheritable |
| Upstash Management API | Yes, after source proof and before admission | Not configured on service | Not safely injectable |
| Exact approved application image | Yes | Available | One-off/shell disabled |
| External dependency beyond these | No | — | — |

## Smallest safe change

The smallest provider-native recovery is a separately authorized Render plan
change that enables an existing-service One-Off Job, plus a separately
authorized secure provider-side injection of the six canary-only inputs. That
job must use the current service's latest image and inherited production
environment. This gate authorizes neither billing nor environment mutation, so
it was not performed.

Creating a temporary service and shared environment group is a larger
alternative. Copying production secrets to the local process is not an
automatic fallback.

## Safety outcome

- runner invocations: 0;
- infrastructure resources created: 0;
- infrastructure settings modified: 0;
- Redis writes/deletes: 0;
- PostgreSQL writes: 0;
- catalog writes: 0;
- secret values exposed: 0.
