# MANAGER - Domain Naming Consolidation Plan

## Status

Verdict: APPROVED

This plan was refreshed after the naming consolidation pass. The detailed match engine is now named as a stable domain capability instead of an internal version experiment.

## Decisions

| Topic | Decision | Evidence |
| --- | --- | --- |
| Detailed match engine | Consolidated from versioned naming to `simulation.detailed` and `DetailedMatch*` names. | Main package is `com.footballmanager.application.service.simulation.detailed`; active controller/storage/engine names are `DetailedMatchController`, `DetailedMatchRedisAdapter`, `DetailedMatchEngine`, `DetailedMatchStoragePort`. |
| Live and round services | Removed versioned service names. | `LiveMatchLifecycleService`, `LiveMatchMutationService`, `RoundLifecycleService`, `RoundMutationTracking`. |
| Formation parser | Removed internal `DetailedFormation` name. | Parser exposes `FormationParser.FormationShape`. |
| Classic engine path | Kept as active fallback, renamed internally to classic language. | `LeagueSimulator` uses `useClassicLeagueEngine` and `simulateWithClassicEngine`; old property remains as an alias. |
| Config compatibility | New detailed/classic property names added while old versioned keys remain accepted. | `app.simulation.league.detailed-enabled` aliases `app.simulation.league.detailed-enabled`; `app.simulation.detailed.*` aliases `app.simulation.detailed.*`. |
| Persistence compatibility | Existing stored `engineType: "Detailed"` is intentionally preserved. | `DetailedMatchData` and `BaselineState` keep the persisted value to read already-stored match detail and baseline snapshots. |
| Test packages | Detailed-engine tests moved off the `simulation/detailed` path. | Tests now live under `src/test/java/com/footballmanager/application/service/simulation/detailed`. |

## Remaining allowed version references

- Persisted data value `engineType = "Detailed"` remains for save/Redis compatibility.
- Legacy property aliases containing `detailed` / `classic` remain so existing local and deployment configuration keeps working.
- Historical test display names or old regression ticket labels may still mention Detailed/Classic when they identify a past bug. They are not production architecture names.

## Naming rules going forward

- Use capability names: `detailed match`, `classic engine`, `live match`, `round lifecycle`, `formation shape`.
- Do not introduce new versioned class, package, method, or field names unless the version is an external protocol/persisted contract.
- Avoid `Helper` for production services that own a real responsibility; prefer validator, resolver, assembler, mapper, policy, or lifecycle names.
- Keep `Impl` only where it is an implementation of a port/use case and not a substitute for responsibility naming.
