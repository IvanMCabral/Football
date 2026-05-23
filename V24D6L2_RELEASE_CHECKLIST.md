# V24D6L2 — Release Checklist / Flag Rollout Plan

**Status:** V24D6L2 COMPLETE — committed at `38c80f8`. L3 docs/status close pending this update.
**Branch:** `mvp-1-performance-cleanup`
**Date:** 2026-05-22
**Based on:** V24D6L1 release readiness audit (`22b650c`), V24D6K complete (K1–K8: `fc47ab7`), full suite 723/0 failures

---

## Purpose

This document defines the release checklist and flag rollout plan for V24D6. It specifies exact flag combinations for each release tier, go/no-go criteria, rollback procedure, monitoring requirements, and beta communication guidance.

**Constraint:** No feature flag defaults are changed in this document. All flag values shown are configuration examples for specific tiers, not default modifications.

---

## 1. Executive Summary

### Release Recommendation

| Tier | Name | Flags | Recommendation |
|------|------|-------|---------------|
| 0 | Safe General Release | All V24/mutation flags `false` | **Default — ship as-is** |
| 1 | V24 Detail Read-Only Beta | V24 detail enabled, mutation off | **Safe to enable for selected testers** |
| 2 | Mutation Internal Beta | All V24 + mutation enabled | **Only after frontend visibility confirmed** |
| 3 | Full Rollout | All flags `true` | **Not recommended yet** |

### Rationale

- **Tier 0** is zero-risk: all V24/mutation code is present but completely inert. No production behavior changes.
- **Tier 1** is low-risk: V24 simulation quality improvement without any career state side effects. Detail data is isolated.
- **Tier 2** is medium-risk: mutation pipeline becomes active. Requires frontend visibility so users understand why players become unavailable.
- **Tier 3** is deferred: injury pressure (12.3/team in reduced diagnostic) and missing player season stats are not blocking but should be resolved before broad mutation rollout.

---

## 2. Flag Combinations by Release Tier

### Tier 0 — Safe General Release

```
app.simulation.league.use-v24-detailed-engine: false
app.simulation.v24.persist-detail: false
app.simulation.v24.expose-detail-api: false
app.simulation.v24.mutate-career-state: false
app.simulation.v24.persist-injuries: false
app.simulation.v24.persist-fatigue: false
app.simulation.v24.persist-discipline: false
app.simulation.v24.persist-form: false
```

**Behavior:** Zero change from current production. V23 `DefaultMatchSimulator` handles all matches. No V24 code executes.

**Risk:** None.

**Requires:** Nothing — ships as the default build.

---

### Tier 1 — V24 Detail Read-Only Beta

```
app.simulation.league.use-v24-detailed-engine: true
app.simulation.v24.persist-detail: true
app.simulation.v24.expose-detail-api: true
app.simulation.v24.mutate-career-state: false
app.simulation.v24.persist-injuries: false
app.simulation.v24.persist-fatigue: false
app.simulation.v24.persist-discipline: false
app.simulation.v24.persist-form: false
```

**Behavior:** V24DetailedMatchEngine runs for each match, producing richer match data (shot locations, xG per shot, per-minute events, real player attribution). Detail data is persisted to Redis and queryable via `GET /api/careers/{id}/matches/{id}/detail`. **No career state mutations occur.** Players' injury/suspension/energy/form values are unchanged.

**Risk:** Low. V24 simulation replaces V23 simulation for match quality, but career state is untouched. Redis storage grows with detail data — monitor capacity.

**Requires:**
- Frontend match detail route functional (`/careers/{id}/matches/{id}/detail`)
- Redis storage capacity monitored
- Beta users informed that this shows richer match data without gameplay changes

---

### Tier 2 — Mutation Internal Beta

```
app.simulation.league.use-v24-detailed-engine: true
app.simulation.v24.persist-detail: true
app.simulation.v24.expose-detail-api: true
app.simulation.v24.mutate-career-state: true
app.simulation.v24.persist-injuries: true
app.simulation.v24.persist-fatigue: true
app.simulation.v24.persist-discipline: true
app.simulation.v24.persist-form: true
```

**Behavior:** Full mutation pipeline active. Players accumulate injuries, lose energy, receive cards, and see form changes over matches. Injured/suspended players are blocked from selection via `isPlayerAvailable()`. Energy recovery applies to non-participating players. Injury/suspension durations decrement post-round.

**Risk:** Medium. Career state is mutated. Players may become unavailable due to injury/suspension. Users must understand why.

**Requires:**
- Frontend shows injury/suspension status prominently
- Frontend shows energy levels in squad/lineup view
- Frontend shows form values (at minimum in player detail)
- Beta users understand lineup blocking behavior
- Beta careers can be reset if mutation data causes issues
- Monitoring for lineup blocking complaints

**Known concern:** Injury rate 12.3/team in reduced diagnostic. In a full season with real rotation, this may be acceptable. Monitor beta feedback.

---

### Tier 3 — Full Rollout (Not Recommended Yet)

```
app.simulation.league.use-v24-detailed-engine: true
app.simulation.v24.persist-detail: true
app.simulation.v24.expose-detail-api: true
app.simulation.v24.mutate-career-state: true
app.simulation.v24.persist-injuries: true
app.simulation.v24.persist-fatigue: true
app.simulation.v24.persist-discipline: true
app.simulation.v24.persist-form: true
# Plus all feature flags for detail, mutation, etc.
```

**Recommendation:** Defer full rollout until:
- Tier 2 beta is stable with no major user confusion
- Full 20-team/38-round diagnostic confirms injury rate acceptable under real-season conditions
- User feedback on injury pressure is acceptable
- Player season stats aggregation is implemented ( UX gap)

---

## 3. Go/No-Go Criteria

### Tier 0 — Safe General Release

| Criterion | Go | No-Go |
|----------|----|-------|
| Full suite | 723 tests, 0 failures | Any failures |
| Flag defaults | All `false` (verified in `application.yaml`) | Any flag default changed from `false` |
| V23 path | Works in production | Not tested before release |
| Backend compiles | Clean build | Compilation errors |

**Decision:** Always Go for Tier 0 — it is the default build state.

---

### Tier 1 — V24 Detail Read-Only Beta

| Criterion | Go | No-Go |
|----------|----|-------|
| Full suite | 723 tests, 0 failures | Any failures |
| V24 detail endpoint | Returns valid JSON for test career | 404, 500, or error response |
| Redis save | No frequent errors in logs | >10% failure rate on save attempts |
| Frontend route | Match detail page loads in dev/staging | Route broken or returns 404 |
| Detail data quality | Shot/xG/player attribution populated | Empty timeline or null fields |
| Beta users | Selected and informed | Not selected yet |

---

### Tier 2 — Mutation Internal Beta

| Criterion | Go | No-Go |
|----------|----|-------|
| Frontend injury badge | Visible on injured player cards | Not displayed |
| Frontend suspension badge | Visible on suspended player cards | Not displayed |
| Energy display | Squad/lineup view shows energy | Energy not visible |
| Form display | Player detail shows form value | Form not visible |
| User comprehension | Beta users understand lineup blocking | Beta users confused by unavailable players |
| Beta feedback | Injury/suspension rate acceptable to users | Majority of beta users complain |
| Career reset path | Users can start fresh career if needed | No reset mechanism |
| Injury rate | Beta users not overwhelmed by injuries | Injury rate causes roster impossibility |

---

### Tier 3 — Full Rollout

| Criterion | Go | No-Go |
|----------|----|-------|
| Tier 2 stable | 30+ days with no critical bugs | Issues still surfacing |
| Full season diagnostic | Injuries/team within target range | Sustained >12/team |
| User feedback | No major complaints about injury/fatigue pressure | Users asking for difficulty reduction |
| Player season stats | Implemented | Not available |
| Rollback tested | Tier 2 rollback confirmed working | Not tested |

---

## 4. Rollback Plan

### Tier 0 → Tier 1 Rollback

If Tier 1 (V24 detail read-only beta) causes issues:

1. Set `app.simulation.league.use-v24-detailed-engine: false` — immediately returns to V23 simulation path
2. Set `app.simulation.v24.persist-detail: false` — stops new detail data from being saved
3. Set `app.simulation.v24.expose-detail-api: false` — disables detail endpoint (returns 404)

**Effect:** System returns to pure V23 path. Existing detail data in Redis remains (safe, harmless).

**Does not clear existing data:** Redis keys for `career:{id}:match-detail:{id}` remain. They are never automatically deleted.

---

### Tier 1 → Tier 2 Rollback

If Tier 2 (mutation beta) causes issues:

1. Set `app.simulation.v24.mutate-career-state: false` — immediately stops mutation appliers from being called
2. Individual persist flags (`persist-injuries`, etc.) become irrelevant when `mutate-career-state` is `false`

**Effect:** Mutations stop. **Existing mutation data is not cleared.**

---

### Rolling Back Mutation Data (If Needed)

If a beta career has accumulated mutation data that the user wants cleared:

**Option A — Career Reset (Recommended):**
- User creates a new career
- Old career with mutation data is abandoned
- No data migration required

**Option B — Manual Database Cleanup (Not Implemented):**
- Direct Redis/database cleanup of mutation fields on SessionPlayer
- Not built into the application — requires manual ops action
- Not recommended for MVP

**Important:** Mutation data (injury/suspension/energy/form) is stored on `SessionPlayer` in Redis. There is no application-level "clear mutations" operation. If a career has bad mutation data and cannot be abandoned, manual Redis cleanup would be required (out of scope for MVP).

---

### Redis Detail Data Rollback

If Redis detail storage grows too large or detail endpoint causes issues:

1. Set `app.simulation.v24.persist-detail: false` — new detail data not saved
2. Set `app.simulation.v24.expose-detail-api: false` — endpoint returns 404
3. **Redis data remains** — not automatically deleted

To clean up existing detail data (optional, not required):
- Delete Redis keys matching `career:*:match-detail:*`
- This is an ops action, not an application feature

---

## 5. Monitoring Checklist

### Pre-Release (Tier 0)

- [ ] Full suite passes: 723 tests, 0 failures
- [ ] `application.yaml` flag defaults verified unchanged: all V24/mutation flags `false`
- [ ] V23 simulation path confirmed working in staging
- [ ] No new compilation warnings introduced

### Post-Tier-1-Enablement

- [ ] Monitor logs for `V24DetailedMatchEngine` errors
- [ ] Monitor `V24DetailedMatchRedisAdapter` save failures (threshold: >5% failure rate)
- [ ] Monitor Redis key count/capacity growth
- [ ] Verify detail endpoint `GET /api/careers/{id}/matches/{id}/detail` returns valid JSON
- [ ] Check frontend match detail page renders in staging
- [ ] No spike in error reports from existing users

### Post-Tier-2-Enablement

- [ ] Monitor `V24CareerMutationService` warnings in logs
- [ ] Monitor average injury count per round in beta careers
- [ ] Monitor lineup blocking complaints (support tickets)
- [ ] Verify injured player badge visible on player cards
- [ ] Verify suspended player badge visible on player cards
- [ ] Verify energy visible in squad/lineup view
- [ ] Verify form visible in player detail view
- [ ] Check no unexpected `NullPointerException` in mutation applier calls
- [ ] Monitor `V24InjuryRecoveryLifecycleApplier` recovery events firing
- [ ] Monitor `V24EnergyRecoveryLifecycleApplier` recovery events firing
- [ ] User feedback: is injury pressure acceptable? Too high? Too low?
- [ ] User feedback: is lineup blocking behavior understood?

---

## 6. Beta Tester Communication Template

### Tier 1 (Detail Read-Only) Beta Message

```
You're invited to try our V24 Match Detail beta!

What this means:
- When you open a finished match, you'll see a new "Match Detail" button (📊)
- The detail page shows minute-by-minute events, shot locations, xG values, and real player names
- Your career gameplay is unchanged — no injuries, energy, or form changes
- This is purely a richer match recap view

How to try it:
1. Play a match in your career
2. Tap "📊 Detalle" on the completed match
3. Explore the new timeline view

Report any issues: [support channel]
```

### Tier 2 (Mutation) Beta Message

```
You're invited to try our V24 Career Mutation beta!

What this means:
- Players in your career now accumulate fatigue, injuries, yellow/red cards, and form changes over matches
- A player's energy drains when they play a full match; they recover when they don't play
- Players with injuries or suspensions will be blocked from your starting XI
- Form changes based on performance — good players improve, poor players decline

Important notes:
- This beta affects a single career — you can start a new career to get a fresh slate
- If a player is injured or suspended, auto-select will avoid picking them
- You can always substitute tired players to preserve energy

What to watch for:
- Does injury pressure feel right? Too many? Too few?
- Is the energy/fatigue system understandable?
- Are lineup blocks clear when a player is unavailable?

Report feedback: [support channel]
```

---

## 7. Release Checklist

### General (All Tiers)

- [ ] Full suite passes: `mvn test` shows 723 tests, 0 failures
- [ ] No `src/main` code changes since last release
- [ ] No flag default changes in `application.yaml`
- [ ] Redis capacity reviewed and acceptable for detail data
- [ ] Frontend repo synced with backend version
- [ ] Support team briefed on V24 feature flags
- [ ] Rollback plan documented and tested in staging

### Tier 1 Specific

- [ ] `use-v24-detailed-engine: true` deployed to staging
- [ ] Detail endpoint verified functional in staging: `GET /api/careers/{id}/matches/{id}/detail`
- [ ] Frontend match detail route verified in staging
- [ ] Redis detail save success rate >95%
- [ ] Beta users selected and informed
- [ ] Beta announcement sent

### Tier 2 Specific

- [ ] Frontend injury badge verified visible on player cards
- [ ] Frontend suspension badge verified visible on player cards
- [ ] Energy display verified in squad/lineup view
- [ ] Form display verified in player detail view
- [ ] `mutate-career-state: true` deployed to staging
- [ ] Staging career mutation data reviewed for expected behavior
- [ ] Lineup blocking behavior verified (unavailable players excluded)
- [ ] Beta users informed of mutation behavior
- [ ] Beta users informed of career reset option

---

## 8. Recommended Next Phase: V24D6L3

### V24D6L3 — Docs/Status Update Closing V24D6L Release-Readiness Package

**Scope:** Update V24D5_PRODUCTION_INTEGRATION_PLAN.md and V23_SIMULATION_ENGINE_STATUS.md to reflect:
- L1 release readiness audit committed (`22b650c`)
- L2 release checklist complete (this doc)
- V24D6L is the closure of the V24D6 release-readiness package
- Flag rollout tiers documented
- No new code changes

**Deliverables:**
- Updated V24D5_PRODUCTION_INTEGRATION_PLAN.md header with L1/L2 status
- Updated V23_SIMULATION_ENGINE_STATUS.md header with L1/L2 status
- Updated V24D6L phase summary in roadmap docs

**Non-deliverables:**
- No code changes
- No flag defaults changed
- No new tests

---

*V24D6L2 release checklist complete. Awaiting user signal to proceed with L3 or commit L2 document.*