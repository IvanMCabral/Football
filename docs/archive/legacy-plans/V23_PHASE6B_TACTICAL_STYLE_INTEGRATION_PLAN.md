# V23 Phase 6B: Tactical Style Simulation Integration — Audit & Decision

**Status:** DECISION MADE — Option B implemented (Phase 6B complete)
**Latest commit:** `2eaa41a` (feat: add experimental style-aware match simulation overload)
**Created:** 2026-05-05
**Phase 6B completed:** 2026-05-05

---

## Executive Summary

Phase 6A delivered `TeamStyle` enum and `MatchQualityComputer.computeLambdas(int, int, TeamStyle, TeamStyle)`. Phase 6B must decide whether and how `MatchEngineImpl` should consume style in simulation. Five options are presented. Audit findings are below, followed by option comparison and recommendation.

---

## Audit Findings

### 1. Team Aggregate (`domain/model/aggregate/Team.java`)

- PostgreSQL-backed entity with `TeamId`, `UserId`, name, country, budget, `Formation`, `Set<PlayerId>`
- **No `TeamStyle` field** — style would require adding a new field
- `Formation` field exists but is **not used** in simulation OVR calculation
- OVR computation: `70 + min(20, squadSize/2)` — formation-agnostic
- Adding style here would require a **database migration** and API changes
- **Risk: HIGH** for persistence/API impact

### 2. SessionTeam (`domain/model/entity/SessionTeam.java`)

- Redis-only JSON blob (NOT PostgreSQL) — comment: "NO se persiste en base de datos real"
- Has: sessionTeamId, baseTeamId, worldTeamId, name, country, budget, formation, morale, reputation, origin
- **No `TeamStyle` field**
- `formation` is a String (e.g., "4-3-3"), not a tactical style enum
- Adding style here would affect Redis serialization of `CareerSave`
- Existing Redis data has no style field — **backward-compatible addition possible** if nullable
- **Risk: MEDIUM** (no PostgreSQL migration, but Redis compat and API mapping needed)

### 3. WorldTeam (`domain/model/entity/WorldTeam.java`)

- Immutable snapshot entity used for AI teams in WorldSnapshot
- Has: worldTeamId, realTeamId, realLeagueId, name, country, city, baseBudget, baseFormation, origin
- **Not used directly in simulation** — `MatchEngineImpl` works with `Team` domain aggregate
- WorldSnapshot provides teams for career; simulation works with Team/SessionTeam
- **No direct simulation impact**

### 4. CareerSave Serialization (`domain/model/entity/CareerSave.java`)

- Redis JSON blob with key `career:{userId}`
- Contains: `CareerData`, `CareerTeamManager` (SessionTeams), `CareerPlayerManager`, `CareerSeasonManager`, `teamStarting11`, `TournamentState`
- SessionTeams stored inside `CareerTeamManager` → `Map<String, SessionTeam>`
- Adding `TeamStyle` to `SessionTeam` changes the JSON schema
- Existing career saves have no style field
- **Backward-compatible if field is nullable** (Jackson ignores unknown properties by default with `@JsonIgnoreProperties(ignoreUnknown = true)`)
- No PostgreSQL alter needed — Redis JSON is schema-flexible
- **Risk: MEDIUM** if adding style (needs migration logic for existing career saves)

### 5. Team Creation/Update APIs

- `Team` entity is managed via standard CRUD repositories (TeamRepository)
- Adding a style field to `Team` would require:
  - Database migration (PostgreSQL ALTER TABLE)
  - API DTO changes (new field in request/response)
  - Frontend changes to display/edit style
- SessionTeam has no dedicated API endpoint — managed via career commands
- **No API for team style editing currently exists**

### 6. MatchEngineImpl Simulation Entry Points (`application/service/domain/MatchEngineImpl.java`)

**Current methods:**
```java
// Package-private — not part of MatchEngine port interface
public Mono<MatchResult> simulate(Team homeTeam, Team awayTeam)     // uses new Random()
public Mono<MatchResult> simulate(Team homeTeam, Team awayTeam, long seed)  // uses new Random(seed)
```

**Current lambda computation:**
```java
MatchQualityComputer.MatchQualityLambdas lambdas =
    MatchQualityComputer.computeLambdas(homeOverall, awayOverall);  // NO style
```

**Key observations:**
- `simulate(Team, Team, long seed)` is **package-private** (no `public` modifier) — not exposed via port
- Both existing methods use the no-style `computeLambdas(int, int)` overload
- `TeamStyle` is **never referenced** in `MatchEngineImpl`
- Adding a style-aware overload would NOT require modifying existing simulate methods
- `MatchEngineImpl` is marked `@Component` — injectable as a bean

### 7. MatchEngine Port Interface (`domain/ports/out/match/MatchEngine.java`)

```java
public interface MatchEngine {
    Mono<MatchResult> simulate(Team homeTeam, Team awayTeam);
}
```

- Only one method — no seed overload in the port
- The seeded `simulate(Team, Team, long seed)` in `MatchEngineImpl` is an extension beyond the port
- **Option B does NOT require changing the port interface** — new method can live on `MatchEngineImpl` only
- If Option B exposes a new port method later, the interface would need updating

### 8. Database Migration for Team Aggregate

- `Team` is a PostgreSQL aggregate in `domain/model/aggregate/Team`
- No `TeamStyle` field exists on `Team`
- Adding it requires: `ALTER TABLE teams ADD COLUMN style VARCHAR(20)` (or similar)
- JPA/repositories would need updating
- **This is a non-trivial migration** — out of scope for Phase 6B unless approved
- **Risk: HIGH**

### 9. Redis/ SessionTeam Compatibility

- SessionTeam stored in Redis as part of CareerSave JSON
- Current CareerSave has no style field in SessionTeam
- Adding `TeamStyle` as nullable field to SessionTeam:
  - Existing career saves deserialize correctly (Jackson ignores missing fields)
  - New career saves would have the field
  - **Backward-compatible** — no migration required for Redis JSON
- However: no API exists to set/get SessionTeam style currently
- **Risk: MEDIUM** for API/frontend mapping

### 10. Experimental Overload Without Persistence

**Option B is fully viable without any persistence or API changes:**
- `MatchEngineImpl` has package-private seeded overload already
- New method `simulateWithStyle(Team home, Team away, TeamStyle homeStyle, TeamStyle awayStyle, long seed)` is a simple addition
- Uses `MatchQualityComputer.computeLambdas(int, int, TeamStyle, TeamStyle)` (already exists)
- No change to existing `simulate(Team, Team)` or `simulate(Team, Team, long seed)`
- No change to `MatchEngine` port interface
- No Team/SessionTeam/Redis/API/frontend changes
- Useful for: tests, admin tooling, balance experimentation, future integration

---

## Option Comparison

### Option A — Keep Phase 6A Only (No Simulation Integration)

**Description:** `TeamStyle` and style-aware `computeLambdas()` remain as analytics utilities only. `MatchEngineImpl` unchanged forever.

| Dimension | Impact |
|-----------|--------|
| Files affected | None |
| API impact | None |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | Unchanged |
| Test impact | None |
| Risk level | **NONE** |
| Rollback plan | N/A — nothing to revert |

**Assessment:** Lowest risk. Style stays as unused code. Phase 6B is "never". Valid choice if tactical style is not a priority.

---

### Option B — Experimental Simulation Overload Only

**Description:** Add package-private style-aware method to `MatchEngineImpl`. Uses existing `computeLambdas(int, int, TeamStyle, TeamStyle)`. Normal simulate() unchanged.

**Proposed method:**
```java
public Mono<MatchResult> simulateWithStyle(
    Team homeTeam, Team awayTeam,
    TeamStyle homeStyle, TeamStyle awayStyle,
    long seed
)
```

**Behavior:**
- Uses `MatchQualityComputer.computeLambdas(homeOvr, awayOvr, homeStyle, awayStyle)`
- Returns deterministic `MatchResult` for same inputs
- BALANCED+BALANCED → identical to `simulate(Team, Team, long seed)`
- All other styles → bounded deterministic differences (validated in tests)

| Dimension | Impact |
|-----------|--------|
| Files affected | `MatchEngineImpl.java` only |
| API impact | None (port unchanged, method is package-private) |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | New method exists; existing methods unchanged |
| Test impact | New tests for: BALANCED/BALANCED equivalence, ATTACKING/DEFENSIVE bounded differences, quality gate still passes |
| Risk level | **LOW** |
| Rollback plan | Delete new method; no migration needed |

**Feasibility check:**
- `simulate(Team, Team, long seed)` is package-private — same visibility works for new method
- `MatchQualityComputer.computeLambdas(int, int, TeamStyle, TeamStyle)` already exists and is public static
- `TeamStyle` enum is in same package (`application.service.domain`) as `MatchEngineImpl`
- No need to change port interface
- No Team/SessionTeam/Redis/API changes

**Assessment:** Cleanest path. Zero production risk. Enables experimentation and future decision. Recommended.

---

### Option C — Add Style to SessionTeam Only

**Description:** Add nullable `TeamStyle` field to `SessionTeam`. Store in career. `MatchEngineImpl` gets style from SessionTeam when simulating.

**Changes required:**
1. Add `private TeamStyle style;` + getter/setter to `SessionTeam`
2. Set default style when creating SessionTeam (e.g., `BALANCED`)
3. Add style mapping in CareerTeamManager / CareerSave deserialization
4. Modify `simulate` or add new method to pass style from SessionTeam → computeLambdas
5. API endpoint to allow user to set style (CareerCommandService?)

| Dimension | Impact |
|-----------|--------|
| Files affected | `SessionTeam.java`, `CareerTeamManager.java`, simulation entry point |
| API impact | New endpoint for style editing (or deferred) |
| Persistence impact | Redis JSON schema change (backward-compatible with nullable) |
| Frontend impact | Style selector UI needed (or deferred) |
| Simulation behavior | MatchEngineImpl uses style from SessionTeam |
| Test impact | Tests for style serialization/deserialization, default style |
| Risk level | **MEDIUM** |
| Rollback plan | Remove field from SessionTeam, revert simulation changes |

**Assessment:** Middle ground. Requires Redis compat work and API/frontend investment. Not suitable for Phase 6B without explicit approval.

---

### Option D — Add Style to Team Aggregate

**Description:** Add `TeamStyle` field to `Team` PostgreSQL entity. Requires migration. Full integration into simulation.

**Changes required:**
1. `ALTER TABLE teams ADD COLUMN style VARCHAR(20)`
2. Add field + getter/setter to `Team.java`
3. Update repositories and mappers
4. Add style to API DTOs
5. Frontend team management UI
6. Migration script for existing teams

| Dimension | Impact |
|-----------|--------|
| Files affected | Team.java, repositories, mappers, DTOs, API endpoints, frontend |
| API impact | Significant |
| Persistence impact | PostgreSQL migration required |
| Frontend impact | Style selector in team management |
| Simulation behavior | MatchEngineImpl reads Team.style |
| Test impact | Integration tests for style persistence |
| Risk level | **HIGH** |
| Rollback plan | Revert migration, remove field, rollback deploy |

**Assessment:** Not recommended for Phase 6B. PostgreSQL migration and broad API impact. Would be Phase 6C if ever approved.

---

### Option E — Derive Style from Formation

**Description:** Map `Formation` string (e.g., "4-3-3") to `TeamStyle` automatically. No new field. Heuristic mapping.

**Proposed mapping:**
- "4-3-3", "3-5-2", "4-2-3-1" → `ATTACKING`
- "5-3-2", "5-4-1" → `DEFENSIVE`
- "4-4-2", "4-1-4-1" → `BALANCED`
- others → `BALANCED`

| Dimension | Impact |
|-----------|--------|
| Files affected | MatchEngineImpl (or MatchQualityComputer) — mapping logic |
| API impact | None |
| Persistence impact | None |
| Frontend impact | None |
| Simulation behavior | Style derived from formation at simulation time |
| Test impact | Mapping tests, validation |
| Risk level | **LOW** (code only) |
| Rollback plan | Remove mapping logic |

**Assessment:** Not recommended. Formation does not equal tactical intent — a team with "4-3-3" formation may play counter-attack. Confuses the model. Only viable if there is explicit design reason.

---

## Recommendation

**Prefer Option B.**

Option B is feasible with current architecture:
1. `MatchEngineImpl` is in `application.service.domain` — same package as `TeamStyle`
2. `computeLambdas(int, int, TeamStyle, TeamStyle)` is `public static` — already callable
3. Existing `simulate(Team, Team, long seed)` is package-private — same visibility works for new method
4. No port interface change needed
5. No Team/SessionTeam/Redis/API/persistence changes
6. Zero production risk — existing simulate() behavior is untouched

**Option B delivers:**
- Proved equivalence: `simulateWithStyle(t, t, BALANCED, BALANCED, seed)` == `simulate(t, t, seed)`
- Bounded style effects: ATTACKING/DEFENSIVE produces deterministic but different lambda values
- Quality gate compatibility: full regression suite still passes
- Path to future decision: if Phase 6B experiments prove valuable, Option C/D can be pursued later with evidence
- Admin/test utility: non-destructive style experimentation without affecting production matches

**If Option B is approved**, Phase 6B implementation should:
1. Add `simulateWithStyle(Team, Team, TeamStyle, TeamStyle, long seed)` to `MatchEngineImpl`
2. Add tests proving:
   - `simulate` unchanged behavior
   - `simulateWithStyle` with BALANCED/BALANCED equals `simulate` for same seed
   - ATTACKING/DEFENSIVE produces bounded differences (within quality gate ranges)
   - Full quality gate (`V23SimulationQualityGateTest`) passes
3. NOT change `MatchEngine` port interface
4. NOT change `Team`, `SessionTeam`, or any persistence layer

---

## Hard Constraints (Non-Negotiable)

These must hold regardless of which option is chosen:

1. **`simulate(Team, Team)` behavior unchanged** — existing production path must produce identical results
2. **`simulate(Team, Team, long seed)` behavior unchanged** — determinism contract must hold
3. **`MatchQualityComputer.computeLambdas(int, int)` unchanged** — baseline formula never modified
4. **Full regression gate passes** — all 81 tests must continue to pass
5. **No persistence or API changes in Phase 6B** — unless explicitly approved separately
6. **No Team/SessionTeam/Redis/persistence changes** — unless explicitly approved separately
7. **No frontend changes** — not in scope

---

## Decision Required

**Decision: Option B approved and implemented.**

**Implemented:** `simulateWithStyle(Team, Team, TeamStyle, TeamStyle, long seed)` in `MatchEngineImpl` (commit `2eaa41a`). All constraints held:
1. `simulate(Team, Team)` behavior unchanged
2. `simulate(Team, Team, long seed)` unchanged
3. `computeLambdas(int, int)` unchanged — baseline formula preserved
4. All 81 tests pass
5. No persistence or API changes
6. No Team/SessionTeam/Redis changes
7. No frontend changes

**Validation results:**
- BALANCED/BALANCED equals seeded baseline (proved by test)
- All 25 style combinations produce valid results
- Style effects bounded over 1000 seeded matches
- Deterministic: same style + same seed = identical result
- Full quality gate passes

**Remaining work (Phase 6C):** User-configurable style via SessionTeam/API/frontend — not yet approved.

---

## Files Reference

| File | Role |
|------|------|
| `src/main/java/.../domain/model/aggregate/Team.java` | PostgreSQL team entity — no style field |
| `src/main/java/.../domain/model/entity/SessionTeam.java` | Redis-only session team — no style field |
| `src/main/java/.../domain/model/entity/WorldTeam.java` | WorldSnapshot team — not used in simulation |
| `src/main/java/.../domain/model/entity/CareerSave.java` | Redis career blob — contains SessionTeams |
| `src/main/java/.../domain/ports/out/match/MatchEngine.java` | Port interface — only `simulate(Team, Team)` |
| `src/main/java/.../application/service/domain/MatchEngineImpl.java` | Simulation implementation — no style consumption |
| `src/main/java/.../application/service/domain/MatchQualityComputer.java` | Has style-aware `computeLambdas(int, int, TeamStyle, TeamStyle)` |
| `src/main/java/.../application/service/domain/TeamStyle.java` | Enum: BALANCED, ATTACKING, DEFENSIVE, COUNTER, POSSESSION |

---

*This document is the authoritative Phase 6B decision record. No code implementation should begin until this document is approved and the decision is recorded.*