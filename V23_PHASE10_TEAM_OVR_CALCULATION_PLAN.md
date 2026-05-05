# V23 Phase 10: Team OVR Calculation Improvement — Audit & Decision

**Status:** COMPLETED — Phase 10A implemented as Option D
**Branch:** `mvp-1-performance-cleanup`
**Latest commit:** `f75afe1` (feat: add experimental explicit OVR match simulation overload)
**Created:** 2026-05-05

---

## Executive Summary

Phase 10 targets improving the team overall (OVR) calculation currently used in `MatchEngineImpl.simulate()`. The existing formula `70 + min(20, squadSize/2)` is squad-size based only — it does not use real player attributes, formation, or starting XI. This document audits the current state, available player data, and five options for improvement.

---

## Audit Findings

### 1. Team Aggregate (`domain/model/aggregate/Team.java`)

- PostgreSQL entity with `TeamId`, `UserId`, name, country, budget, `Formation`, `Set<PlayerId>`, `createdAt`, `updatedAt`
- No `overall` field — OVR is computed at simulation time, not stored
- `Formation` exists but is not used in any OVR calculation anywhere
- Has `getSquadPlayerIds()` returning `Set<PlayerId>` — not a list, no ordering
- **Key constraint:** `Team` only stores `Set<PlayerId>` — `Player` entity is not accessible from here at simulation time

### 2. Player Entity/Model

**`SessionPlayer`** (Redis-only, in `domain/model/entity/SessionPlayer.java`):
- Has `attack`, `defense`, `technique`, `speed`, `stamina`, `mentality` (all `Integer`)
- `position` is a String: `"GK"`, `"DEF"`, `"MID"`, `"WINGER"`, `"ATT"`, or others
- `calculateOverall()` method already exists — position-weighted average:
  - GK: defense 40%, technique 20%, mentality 20%, stamina 10%, speed 5%, attack 5%
  - DEF: defense 35%, technique 15%, mentality 15%, stamina 15%, speed 10%, attack 10%
  - MID: technique 30%, stamina 20%, mentality 15%, defense 15%, speed 10%, attack 10%
  - WINGER: speed 30%, attack 25%, technique 20%, stamina 15%, mentality 5%, defense 5%
  - ATT: attack 40%, technique 20%, speed 15%, mentality 10%, stamina 10%, defense 5%
- `calculateOverall()` returns 50 if any attribute is null
- **Player attribute data IS available** in SessionPlayer

**`Player`** (base entity in `domain/model/entity/Player.java`) — not the same as SessionPlayer.

### 3. SessionTeam (`domain/model/entity/SessionTeam.java`)

- Redis-only JSON blob (comment: "NO se persiste en base de datos real")
- Has `sessionTeamId`, `baseTeamId`, `worldTeamId`, name, country, budget, formation, morale, reputation, origin
- **No `TeamStyle` field** (Phase 6B added experimental overload but not persistence)
- **No player list** — squad stored separately in `CareerTeamManager.teamSquads`
- Formation stored as String (e.g., "4-3-3") — not the `Formation` enum used in Team aggregate

### 4. CareerSave and teamStarting11

**`CareerSave`** (`domain/model/entity/CareerSave.java`):
- Redis JSON blob with key `career:{userId}`
- Contains: `CareerData`, `CareerTeamManager`, `CareerPlayerManager`, `CareerSeasonManager`, `teamStarting11`, `TournamentState`
- `teamStarting11` is `Map<String, List<String>>` — sessionTeamId → list of sessionPlayerIds (starting XI)
- Starting XI IS stored and available

**`CareerTeamManager`**:
- `getSquadPlayerIds(sessionTeamId)` → `List<String>`
- `getSessionTeam(sessionTeamId)` → `SessionTeam`
- `calculateTeamOVR(sessionTeamId, playerProvider)` — exists and works

**`CareerPlayerManager`**:
- `getSessionPlayer(sessionPlayerId)` → `SessionPlayer`
- `getTeamSquad(playerIds)` → `List<SessionPlayer>`

### 5. Player Attribute Availability

- `SessionPlayer` has real per-player attributes (`attack`, `defense`, `technique`, `speed`, `stamina`, `mentality`)
- `SessionPlayer.calculateOverall()` already computes position-weighted OVR per player
- Career squad data (player IDs) is accessible via `CareerTeamManager.getSquadPlayerIds()`
- Starting XI is accessible via `CareerSave.getTeamStarting11()`
- Energy, form, injured status available on `SessionPlayer` — could be used for further refinement

### 6. Where Teams Are Converted/Mapped for Simulation

**`MatchEngineImpl`** (`application/service/domain/MatchEngineImpl.java`):
- `simulate(Team homeTeam, Team awayTeam)` and `simulate(Team, Team, long seed)`
- `performSimulation()` calls `calculateTeamOverall(Team team)`:
  ```java
  private int calculateTeamOverall(Team team) {
      return 70 + Math.min(20, team.getSquadSize() / 2);
  }
  ```
- `Team` is the PostgreSQL aggregate — does not contain player data
- `MatchEngineImpl` has no access to `CareerSave`, `CareerPlayerManager`, or `SessionTeam`
- OVR is computed from squad size only

**`LeagueSimulator`** (`application/service/simulation/LeagueSimulator.java`):
- Uses `DefaultMatchSimulator.simulateQuick(String homeTeamId, String awayTeamId, int homeOvr, int awayOvr)`
- Calculates OVR externally using `CareerSave` + `CareerPlayerManager`:
  ```java
  private int calculateTeamOVR(CareerSave career, String sessionTeamId) {
      List<String> squadPlayerIds = career.getTeamManager().getTeamSquads()
              .getOrDefault(sessionTeamId, List.of());
      // sums player.calculateOverall() / count
  }
  ```
- This is a different simulation path — not V23's `MatchEngineImpl`

**Key finding:** `LeagueSimulator` already does real OVR calculation for its own simulation path. V23's `MatchEngineImpl.simulate(Team, Team)` does not have access to this data at simulation time.

### 7. MatchEngineImpl.calculateTeamOverall() Current Behavior

```java
private int calculateTeamOverall(Team team) {
    return 70 + Math.min(20, team.getSquadSize() / 2);
}
```

- Maximum OVR bonus from squad size is +20 (squad size 40+)
- Minimum squad size (0) → OVR 70
- 11 players → OVR 75
- 20 players → OVR 80
- **No player quality, no formation, no starting XI, no energy/form**

### 8. MatchEngineImpl Context — Does It Have Enough to Compute Real OVR?

**Short answer: NO, not directly.**

`MatchEngineImpl` receives `Team` (PostgreSQL aggregate) only. It has no `CareerSave`, no `CareerPlayerManager`, no `SessionTeam`. The `Team` aggregate stores `Set<PlayerId>` — but `Player` entity with attributes is not directly accessible from `Team` without a repository lookup.

The `Team.squadPlayerIds` field is a `Set<PlayerId>` — not a list, no ordering, no player attribute data embedded.

**Context available at simulation time:**
- `Team.getId()`, `Team.getName()`, `Team.getSquadPlayerIds()` (Set only)
- Nothing about player quality

**Context NOT available:**
- Player attributes (attack, defense, etc.)
- Player position
- Starting XI vs bench distinction
- Player energy, form, injury status

### 9. Options for Passing Real OVR Data

**Option D via explicit OVR:** Add `simulateWithStrength(Team, Team, int homeOvr, int awayOvr, long seed)` — caller computes OVR externally and passes it. MatchEngineImpl just uses the passed values. No persistence/API changes. Similar pattern to `simulateWithStyle()`.

**Option C via calculator service:** Create a `TeamOverallCalculator` service that takes `Team` + player context and computes real OVR. Called before simulation, OVR passed to new overload.

### 10. Compatibility Risks with Existing Tests

All 81 existing tests use `Team.create()` with `addPlayer(PlayerId)` to build teams. This produces squads with `squadSize` based OVR.

If the OVR calculation formula changes in `calculateTeamOverall()`, all 81 tests would need their expected values updated — or the change must be additive (new method only).

**Critical constraint:** Changing `calculateTeamOverall()` formula breaks all 72 quality gate + determinism tests. Only safe path is additive: new method(s), old method unchanged.

**Strategy to avoid test breakage:**
- Option B/D: Add new overload with explicit OVR parameters — old `simulate()` unchanged
- Option C: Add `TeamOverallCalculator` that callers use to pre-compute OVR, then pass to new overload
- Only change `calculateTeamOverall()` if explicitly approved after Phase 10A validation

---

## Option Comparison

### Option A — Keep Current OVR, Document Limitation

**Description:** Accept the current formula. Document the limitation in V23 status doc. No code change.

| Dimension | Impact |
|-----------|--------|
| Files affected | None |
| API impact | None |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | Unchanged |
| Test impact | None |
| Risk level | **NONE** |
| Rollback plan | N/A |

**Assessment:** No work. Engine remains shallow. Not recommended as sole path forward.

---

### Option B — Improve Formula Based on Squad Size

**Description:** Change `70 + min(20, squadSize/2)` to a slightly better heuristic using squad size only. E.g., `60 + min(30, squadSize)` or similar.

| Dimension | Impact |
|-----------|--------|
| Files affected | `MatchEngineImpl.calculateTeamOverall()` |
| API impact | None |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | Changes all existing match results slightly |
| Test impact | **HIGH** — all 72 quality gate tests would need re-validation of expected metrics |
| Risk level | **MEDIUM** |
| Rollback plan | Revert single method — full regression required |

**Assessment:** Low complexity but breaks established quality gate baselines. Would need full re-validation. Not recommended unless formula change is justified by evidence.

---

### Option C — TeamOverallCalculator Service (External Computation)

**Description:** Create `TeamOverallCalculator` in `application/service/domain/` that computes real team OVR from player data. Add a new experimental method that accepts computed OVRs rather than computing them inside `MatchEngineImpl`.

**Proposed new method:**
```java
public Mono<MatchResult> simulateWithStrength(
    Team homeTeam, Team awayTeam,
    int homeOvr, int awayOvr,
    long seed)
```

**Calculator interface:**
```java
public interface TeamOverallCalculator {
    int calculateFromTeam(Team team);  // fallback using squad size
    int calculateFromSessionTeam(SessionTeam team, CareerPlayerManager players);
}
```

**Implementation path:**
1. Create `TeamOverallCalculator` that can compute OVR from SessionTeam + CareerPlayerManager
2. Add `simulateWithStrength(Team, Team, int, int, long seed)` to `MatchEngineImpl`
3. Old `simulate(Team, Team)` and `simulate(Team, Team, long seed)` unchanged
4. Optionally add `TeamOVRQueryService`-style logic for world/WorldPlayer computation

| Dimension | Impact |
|-----------|--------|
| Files affected | New `TeamOverallCalculator.java`, `MatchEngineImpl` new method |
| API impact | None (port unchanged) |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | New method uses passed OVR; old methods unchanged |
| Test impact | New tests for `simulateWithStrength`; existing tests unchanged |
| Risk level | **LOW** |
| Rollback plan | Delete new method + calculator; no migration needed |

**Assessment:** Cleanest incremental path. OVR is computed externally where player data is available, passed explicitly to simulation. Matches the pattern used by `LeagueSimulator.calculateTeamOVR()`.

---

### Option D — Experimental Overload with Explicit OVR

**Description:** Similar to Option C but without a dedicated calculator service. Just add the experimental overload that accepts raw OVR integers.

**Proposed method:**
```java
public Mono<MatchResult> simulateWithStrength(
    Team homeTeam, Team awayTeam,
    int homeOvr, int awayOvr,
    long seed)
```

- Uses the passed `homeOvr` and `awayOvr` directly in `computeLambdas()`
- `calculateTeamOverall()` is not called in this path
- Null/negative OVRs default to baseline formula
- No `TeamOverallCalculator` needed — caller computes OVR however they want

| Dimension | Impact |
|-----------|--------|
| Files affected | `MatchEngineImpl` — one new method |
| API impact | None (port unchanged) |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | New method uses passed OVR; old methods unchanged |
| Test impact | New tests for `simulateWithStrength`; existing tests unchanged |
| Risk level | **LOW** |
| Rollback plan | Delete new method; no migration needed |

**Assessment:** Simplest Option C variant. Follows same pattern as `simulateWithStyle()`. Caller is responsible for computing OVR before calling. Matches `LeagueSimulator`'s already-established pattern.

---

### Option E — Inject Repositories/Player Lookup Into MatchEngineImpl

**Description:** Add `CareerPlayerManager` or `SessionTeam` lookup directly into `MatchEngineImpl` to compute OVR from real player data.

**Example:**
```java
public void setCareerContext(CareerSave career) { ... }
```

**Why this is not recommended:**
- Breaks separation of concerns — MatchEngineImpl becomes coupled to CareerSave
- Makes `simulate(Team, Team)` non-deterministic without external context
- Repository injection adds complexity and testing difficulty
- Breaks the pure-functional contract of MatchEngineImpl (no side effects, no external state)
- Risk of null pointer if career not set
- **Hard constraint violated:** "No repository injection into MatchEngineImpl"

| Dimension | Impact |
|-----------|--------|
| Files affected | `MatchEngineImpl`, potentially CareerSave |
| API impact | Possible |
| Persistence impact | Indirect |
| Frontend impact | None |
| Simulation behavior | Changes existing simulate() contract |
| Test impact | HIGH — existing tests break |
| Risk level | **HIGH** |
| Rollback plan | Complex — would need to remove career context from multiple places |

**Assessment:** Not recommended. Violates separation of concerns and hard constraints.

---

## Recommended Direction

**Phase 10A IMPLEMENTED as Option D.**

**Prefer Option D — Experimental Overload with Explicit OVR (same pattern as Option B in Phase 6B).**

Rationale:
1. **Same pattern as Phase 6B**: `simulateWithStyle()` already exists as an experimental overload. `simulateWithStrength()` follows identical structure.
2. **No existing behavior change**: `simulate(Team, Team)` and `simulate(Team, Team, long seed)` remain unchanged
3. **No persistence/API/frontend changes**: pure addition
4. **Matches existing pattern**: `LeagueSimulator.calculateTeamOVR()` already computes OVR externally and passes integers to `DefaultMatchSimulator.simulateQuick()`. This is the established pattern in the codebase.
5. **Flexible caller**: any caller can compute OVR however they want (full squad, starting XI only, weighted by energy/form, etc.)
6. **Lowest risk**: new method only, old method unchanged, full regression not required

**Phase 10A implementation (if Option D approved):**
1. Add `simulateWithStrength(Team home, Team away, int homeOvr, int awayOvr, long seed)` to `MatchEngineImpl`
2. New method calls `MatchQualityComputer.computeLambdas(homeOvr, awayOvr)` directly — no `calculateTeamOverall()` involvement
3. Null/negative OVR defaults to baseline formula using `calculateTeamOverall(team)`
4. Add tests proving: seeded determinism, bounded metrics, equivalence when homeOvr/awayOvr equals baseline
5. Do NOT change `MatchEngine` port interface
6. Do NOT change `calculateTeamOverall()`

**Caller responsibility example (for future Phase 10B or integration):**
```java
// In LeagueSimulator or a future CareerTeamOverallService:
int homeOvr = career.getTeamManager().calculateTeamOVR(
    sessionTeamId,
    pid -> career.getSessionPlayer(pid)
);
int awayOvr = career.getTeamManager().calculateTeamOVR(
    awaySessionTeamId,
    pid -> career.getSessionPlayer(pid)
);
engine.simulateWithStrength(homeTeam, awayTeam, homeOvr, awayOvr, seed);
```

---

## Hard Constraints (Non-Negotiable)

These must hold regardless of which option is chosen:

1. **`simulate(Team, Team)` behavior unchanged** — existing production path must produce identical results
2. **`simulate(Team, Team, long seed)` behavior unchanged** — determinism contract must hold
3. **Full regression gate passes** — all 89 tests must continue to pass without modification
4. **No repository injection into `MatchEngineImpl`** — violates separation of concerns
5. **No persistence or API changes in Phase 10A** — unless explicitly approved separately
6. **No `calculateTeamOverall()` formula change in Phase 10A** — would break established quality gate baselines
7. **No frontend changes** — not in scope

---

## Decision Required

**Phase 10A APPROVED and IMPLEMENTED as Option D.**

Option D selected and committed as `f75afe1`. Phase 10B (TeamOverallCalculator) next.

- **Option A** — Keep current OVR formula, document limitation (no implementation)
- **Option B** — Improve the squad-size heuristic formula (not recommended — breaks quality gate)
- **Option C** — Add `TeamOverallCalculator` service + explicit OVR overload (medium complexity)
- **Option D** — Add explicit OVR overload only, no calculator service (recommended, lowest risk)
- **Option E** — Inject repositories into MatchEngineImpl (not recommended, violates constraints)

**Phase 10A is complete. Phase 10B requires separate approval before implementation.**

---

## Files Reference

| File | Role |
|------|------|
| `src/main/java/.../domain/model/aggregate/Team.java` | PostgreSQL team — `getSquadPlayerIds()` only, no player attribute access |
| `src/main/java/.../domain/model/entity/SessionPlayer.java` | Redis player — `calculateOverall()` with position-weighted attributes |
| `src/main/java/.../domain/model/entity/SessionTeam.java` | Redis session team — no style field, formation stored as String |
| `src/main/java/.../domain/model/entity/career/CareerTeamManager.java` | Squad management — `calculateTeamOVR(sessionTeamId, playerProvider)` |
| `src/main/java/.../domain/model/entity/career/CareerPlayerManager.java` | Player management — `getSessionPlayer()`, `getTeamSquad()` |
| `src/main/java/.../domain/model/entity/CareerSave.java` | Redis career blob — `getTeamStarting11()`, `getTeamManager()`, `getPlayerManager()` |
| `src/main/java/.../application/service/domain/MatchEngineImpl.java` | Simulation — `calculateTeamOverall()` uses `70 + min(20, squadSize/2)` |
| `src/main/java/.../application/service/query/TeamOVRQueryService.java` | OVR query service for WorldView |
| `src/main/java/.../application/service/simulation/LeagueSimulator.java` | Already computes real OVR from CareerSave |

---

*This document is the authoritative Phase 10 audit. No code implementation should begin until this document is approved and the decision is recorded.*