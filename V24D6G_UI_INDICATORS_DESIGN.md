# V24D6G — UI Indicators for Injured and Tired Players

**Status:** V24D6G — DESIGN + V24D6G3 + V24D6G4A + V24D6G4B + V24D6G5A + V24D6G6A + V24D6G7 AUDIT COMPLETE
**Branch:** `mvp-1-performance-cleanup`
**Latest implementation commit:** `980be03` (V24D6H4 — yellow threshold lifecycle integration; V24D6H complete; backend 623 tests, 0 failures)
**V24D6G3 implementation commit:** `3675431` (frontend squad indicators, front-ciber/project mvp-1)
**V24D6G4A implementation commit:** `362c647` (frontend lineup condition warnings, front-ciber/project mvp-1)
**V24D6G4B implementation commit:** `c4681e2` (frontend lineup confirmation warning, front-ciber/project mvp-1)
**V24D6G5A implementation commit:** `18543dc` (frontend dashboard squad condition warning, front-ciber/project mvp-1)
**V24D6G6A implementation commit:** `80ad1ed` (frontend match detail condition summary, front-ciber/project mvp-1)
**V24D6D7B1 implementation commit:** `8097ca9` (frontend suspension warnings, front-ciber/project mvp-1)
**V24D6D7B2 implementation commit:** `69bf879` (frontend suspended badges, front-ciber/project mvp-1)
**Latest docs commit:** this file (V24D6D7C documentation update)
**Tests:** 623 full suite, 0 failures
**Created:** 2026-05-13
**Updated:** 2026-05-16

---

## 1. Executive Summary

V24D6B (injury persistence) and V24D6C (fatigue/energy persistence) introduced real career consequences into the simulation. After a match where V24D6 mutation is enabled, players can now be injured or tired — permanently affecting their availability and performance in subsequent matches.

However, these career mutations are invisible to the user unless the UI surfaces them. Without visible indicators, users will encounter "unavailable players" with no explanation, feel the game is unfair, or be unable to make informed squad and lineup decisions.

**Goal:** V24D6G1 design is complete. V24D6G2 frontend DTO/data audit is complete. V24D6G3 squad indicators are implemented (frontend commit `3675431`). V24D6G4A lineup player card condition warnings are implemented (frontend commit `362c647`). V24D6G4B lineup confirmation warning is implemented (frontend commit `c4681e2`). V24D6G5A dashboard squad condition warning is implemented (frontend commit `18543dc`). V24D6G6A match detail condition summary is implemented (frontend commit `80ad1ed`). V24D6G7 UX polish/accessibility audit is complete and found no code changes required. V24D6G is complete unless future UX review requests additional polish. V24D6G6B remains optional/deferred.

---

## 2. Current Backend State

| Item | Value |
|------|-------|
| Latest implementation commit | `980be03` — V24D6H4 (yellow threshold lifecycle integration; V24D6H complete) |
| Latest docs commit | this file (V24D6G7 audit + V24D6H docs update) |
| Tests | 623, 0 failures |
| Injury mutation | Wired behind `use-v24-detailed-engine=true` + `mutate-career-state=true` + `persist-injuries=true` |
| Fatigue mutation | Wired behind `use-v24-detailed-engine=true` + `mutate-career-state=true` + `persist-fatigue=true` |
| Discipline persistence | Wired behind `use-v24-detailed-engine=true` + `mutate-career-state=true` + `persist-discipline=true` |
| Suspension lifecycle | Implemented through V24D6D6A/B (commits `219628d`/`b4291d9`) |
| All mutation flags | Default false |
| DTO/API/frontend suspension visibility | Implemented through V24D6D7A (backend `6aadcd5`) and V24D6D7B1/B2 (frontend `8097ca9`+`69bf879`) |
| Form/morale | Deferred |
| Backend test count | 623 total (602 baseline after V24D6D7 + 21 V24D6H), 0 failures |

**Injury mutation target fields on `SessionPlayer`:**
- `injured` (boolean)
- `injuryType` (String)
- `injuryRemainingMatches` (int)

**Fatigue mutation target field on `SessionPlayer`:**
- `energy` (int, 0–100, default 100)

Discipline/card persistence and suspension lifecycle are implemented through V24D6D2-D6. V24D6D7A/B completed suspension visibility: backend DTO/API exposure and lineup blocking in `6aadcd5`, frontend dashboard/squad warnings in `8097ca9`, and PlayerCard/LineupPlayerCard suspended badges in `69bf879`. Form/morale remains deferred. V24D6G originally covered injury/fatigue indicators; suspension indicators were completed later through V24D6D7A/B.

---

## 3. UX Problem

### 3.1 The Visibility Gap

After a round simulation with V24 mutation enabled, the backend modifies `SessionPlayer` state:
- A player may become `injured=true` for N remaining matches
- A player's `energy` may drop from 100 to 70–88 after a match

If the frontend does not display these changes, users experience:
1. **Confusion** — a player who "looked fine" last round is now unavailable with no explanation
2. **Perceived unfairness** — the simulation "randomly" removes players without justification
3. **Inability to plan** — no warning before the next round about tired or injured players
4. **Baffled lineup construction** — injured or exhausted players can be selected without warning, producing unexpected match results

### 3.2 Why Manager Games Need Transparent Player Condition

Games like Football Manager, FIFA Career Mode, and NBA 2K MyGM all surface player condition before each match. Reasons:
- Players need to feel real consequence from match events
- Managers need to make informed rotation decisions
- Squad depth becomes meaningful strategy
- Transparency maintains trust in the simulation's fairness

### 3.3 What the UI Must Explain

Users must be able to answer at a glance:
- Is this player injured? For how long?
- Is this player tired? How much?
- If I start this player, what is the risk?
- How many of my players are unavailable before I simulate the next round?

---

## 4. Existing Data Available to Frontend

V24D6G2 confirmed the following `SessionPlayer` fields are accessible to the frontend:

| Field | Type | Frontend Access | Notes |
|-------|------|-----------------|-------|
| `energy` | int (0–100, default 100) | ✅ Squad endpoint + lineup endpoint | Target of V24D6C fatigue mutation |
| `injured` | boolean | ✅ Squad endpoint + lineup endpoint | Target of V24D6B injury mutation |
| `injuryType` | String | ✅ Squad endpoint only | e.g., "MATCH_INJURY" default from applier |
| `injuryRemainingMatches` | int | ✅ Squad endpoint only | Set by V24 injury mutation; automatic injury recovery/decrement is not implemented yet |
| `form` | int? | ✅ Via squad endpoint | Not mutated by V24D6C |
| `position` | String (GK/DEF/MID/WINGER/ATT) | ✅ Already available | Used for lineup filtering |
| `name` | String | ✅ Already available | Display label |
| `overall` | int | ✅ Via `calculateOverall()` | OVR display |
| **Discipline fields (V24D6D7A/B)** | | | |
| `yellowCards` | number | ✅ Squad endpoint + lineup endpoint | V24D6D7A exposure; frontend model support in V24D6D7B1/B2 |
| `redCards` | number | ✅ Squad endpoint + lineup endpoint | V24D6D7A exposure; frontend model support in V24D6D7B1/B2 |
| `suspended` | boolean | ✅ Squad endpoint + lineup endpoint | Used by dashboard/squad warnings and card badges |
| `suspensionRemainingMatches` | number | ✅ Squad endpoint + lineup endpoint | Used by suspended badge/detail and backend lineup blocking |

V24D6G3 used the squad endpoint data to implement `PlayerCardComponent` indicators.

No new backend fields are created by V24D6C. The fields already exist on `SessionPlayer`. V24D6G2 confirmed they traverse the API boundary to the frontend via the squad endpoint.

---

## 5. Proposed Player Status Model

All status categories are **display-only** frontend constructs. The backend simulation does not use these labels — they are derived from `energy`, `injured`, `injuryType`, and `injuryRemainingMatches` for UI purposes only.

### 5.1 Injury Status

Derived from `SessionPlayer.injured` and `SessionPlayer.injuryRemainingMatches`:

| Condition | Label | Action |
|----------|-------|--------|
| `injured == true` AND `injuryRemainingMatches > 1` | **Injured** | Player unavailable |
| `injured == true` AND `injuryRemainingMatches == 1` | **Returning Soon** | Player unavailable but nearly fit |
| `injured == true` AND `injuryRemainingMatches == 0` | **Injured** | Treat as injured (edge case; log warning) |
| `injured == false` | **Available** | Normal selection |

### 5.2 Energy Status

Derived from `SessionPlayer.energy` (0–100, default 100):

| Energy Range | Label | Color Suggestion | Warning Level |
|-------------|-------|------------------|---------------|
| 80–100 | **Fresh** | Green | None |
| 60–79 | **Good** | Light green | None |
| 40–59 | **Tired** | Yellow/amber | Low |
| 20–39 | **Very Tired** | Orange | Medium |
| 0–19 | **Exhausted** | Red | High |

These are recommended display thresholds only. Backend simulation uses raw `energy` value internally. Frontend thresholds can be adjusted based on UX testing.

### 5.3 Combined Status

When both injury and energy status could apply, injury takes priority:

| Priority | Status | Reason |
|----------|--------|--------|
| 1 | **Injured** | Player cannot perform regardless of energy |
| 2 | **Exhausted** | Energy so low performance is severely affected |
| 3 | **Very Tired** | Energy significantly depleted |
| 4 | **Tired** | Energy moderately depleted |
| 5 | **Available/Fresh** | Normal state |

The combined status displayed to the user is the highest applicable priority.

---

## 6. UI Surfaces to Update

The following pages/views in the frontend require indicators. This document designs the UX intent. V24D6G3 squad indicators, V24D6G4A lineup player card warnings, V24D6G4B lineup confirmation warning, V24D6G5A dashboard warning, and V24D6G6A match detail condition summary are complete. V24D6G7 UX polish/accessibility audit is complete and found no code changes required. V24D6G6B remains optional/deferred.

### 6.1 Squad Management Page

**What to show:**
- Each player row/card displays an injury badge and/or energy badge
- Injured badge: "Injured" label, injury type if available, remaining matches
- Energy badge: energy level label + optional color-coded bar
- Tooltip on hover/focus explaining the status

**States:**
- Injured players: show badge + greyed state + "Unfit" label
- Healthy but tired players: show energy indicator with appropriate color
- Fresh players: minimal or no indicator needed

**Do not implement in V24D6G1:** Sorting/filtering by status. That is a later phase.

### 6.2 Lineup Selection Page

**What to show:**
- Injury badges on players in the squad panel
- Energy indicator near each selectable player
- Clear visual distinction between selectable and unavailable players

**States:**
- **Injured players:** Strong warning or block — visually distinct (greyed + badge + warning text)
- **Exhausted players:** Warning but still selectable — "This player is exhausted. Starting him may affect performance."
- **Very tired players:** Low-priority warning — "This player is very tired."
- **Tired players:** Informational — no blocking
- **Available players:** No warning

**Design note:** Blocking injured players from lineup is strongly recommended. Blocking exhausted players may be optional in V24D6G — test with warnings first, then add blocking if UX proves it necessary.

### 6.3 Match Detail Page

**What to show:**
- Injury events are already in the match timeline (V24D5E3/E6)
- Player ratings table (V24D5E4) could optionally show "played while injured" or "played while exhausted" annotations in the future
- Post-match injury/event summary: INJURY events from timeline, player names and minutes, optional card counts (V24D6G6A implemented — no energy delta as match detail DTO does not expose before/after energy)

**Current state:** Timeline already shows INJURY events. V24D6G does not need to add events — only surface existing event data more clearly if not already visible.

**Future (V24D6G6B, optional):** Deeper match detail polish — enhanced event detail, richer player stats, or expanded condition summaries. Requires UX review to determine if current V24D6G6A scope is sufficient.

### 6.4 Dashboard / Next Match Preview

**What to show:**
- Squad health summary before simulating next round
- "N players unavailable (injury)"
- "N players very tired"
- "N players tired"
- Strong warning banner if key players are unavailable

**Purpose:** Give the manager a heads-up before they commit to simulating the next round. If they have 3 injured and 4 exhausted players, they should see that before clicking "Simulate Round".

---

## 7. Recommended Visual Language

### 7.1 Badge Design

**Injury badges** should be prominent and immediately recognizable:

| Badge | Content | Color |
|-------|---------|-------|
| "Injured" | "Injured" + match count e.g., "Out 2 matches" | Red/crimson |
| "Returning Soon" | "Returning" + "1 match" | Orange/amber |

**Energy badges** can be compact:

| Badge | Content | Color |
|-------|---------|-------|
| Fresh | "Fresh" | Green |
| Good | "Good" | Light green |
| Tired | "Tired" | Yellow |
| Very Tired | "Very Tired" | Orange |
| Exhausted | "Exhausted" | Red |

### 7.2 Icon Recommendations

- **Injury:** 🩹 (medical cross) or ⚠️ (warning triangle)
- **Energy:** ⚡ (lightning bolt) or 🔋 (battery)
- **Fresh:** ✅ or 💪 (flexed bicep)

Use icon + text, not text alone. Color-only badges are inaccessible to color-blind users.

### 7.3 Accessibility

- Never use color as the only differentiator — always pair color with text or icon
- Energy color scale should have sufficient contrast in all states
- Consider a legend or key for color codes
- Screen reader labels must be descriptive: "Player João Silva: Injured, Out 2 matches" not just a red badge

### 7.4 Tooltip Copy

Tooltips should provide actionable information:

| Status | Tooltip Copy |
|--------|-------------|
| Injured | "This player is injured and unavailable for {n} more match(es)." |
| Returning Soon | "This player is close to returning. Remaining injury time: {n} match." |
| Exhausted | "This player is exhausted and likely to underperform. Consider resting him." |
| Very Tired | "This player is very tired. Starting him may affect performance or increase future fatigue." |
| Tired | "This player has reduced energy. Performance may be slightly affected." |
| Fresh | "This player is fully rested and ready to start." |

Tooltip copy should be human-readable, not technical. Avoid exposing raw field names or internal simulation details.

---

## 8. Lineup Rules Recommendation

### 8.1 Injured Players

**Recommendation:** Injured players should be **blocked from being selected** in the starting XI.

**Rationale:**
- An injured player physically cannot play — this is a hard constraint
- Blocking (not just warning) prevents user frustration from unknowingly fielding an unavailable player
- The backend does not currently reject injured players in lineup selection (no validation exists), so this requires frontend AND potentially backend lineup validation

**Implementation path:**
- Frontend: prevent injured players from being added to starting XI
- Future backend (V24D6F): add lineup validation rejecting injured players at persist time

### 8.2 Exhausted Players

**Recommendation:** Exhausted players should receive a **warning** but may remain selectable.

**Rationale:**
- Exhausted players can still play but performance will be degraded
- Blocking immediately may feel too punishing (energy recovers in 1–2 rounds of rest)
- Warning allows manager to make informed risk decisions

**Future refinement:** After UX testing, if exhausted players always lose matches or always perform terribly, consider escalating to block.

### 8.3 Very Tired Players

**Recommendation:** Very tired players should receive a **low-priority warning** only.

**Rationale:**
- Performance degradation is mild-to-moderate
- The manager should know but should not be forced to rest
- Warning is sufficient for informed decision-making

### 8.4 Tired Players

**Recommendation:** Tired players should show an **informational indicator** with no warning dialog.

**Rationale:**
- Performance impact is minimal
- Over-warning creates noise and desensitizes users to real warnings
- Simply showing the energy level is enough for informed management

### 8.5 Summary Table

| Status | Blocking | Warning | Informational |
|--------|----------|---------|---------------|
| Injured | ✅ YES | — | Badge visible |
| Exhausted | ❌ NO | ✅ YES | Energy badge |
| Very Tired | ❌ NO | ✅ LOW | Energy badge |
| Tired | ❌ NO | ❌ NO | Energy badge |
| Fresh | ❌ NO | ❌ NO | None/minimal |

---

## 9. Tooltip Copy — Full Reference

### 9.1 Injury Tooltips

**Injured:**
> "This player is injured and unavailable for {n} more match(es)."

**Returning Soon:**
> "This player is close to returning. Remaining injury time: {n} match."

**Played Injured (future):**
> "This player played while injured this match."

### 9.2 Energy Tooltips

**Exhausted:**
> "This player is exhausted. Consider resting him."

**Very Tired:**
> "This player is very tired. Starting him may affect performance or future fatigue."

**Tired:**
> "This player has reduced energy. Starting him may affect performance."

**Good:**
> "This player is in good condition."

**Fresh:**
> "This player is fully rested and ready to start."

### 9.3 Tooltip Implementation Notes

- Tooltip copy is display text only — no backend logic
- `{n}` is a placeholder for the actual remaining match count or energy value
- Copy should be reviewed by a UX writer before implementation
- Consider localization if the app is ever internationalized

---

## 10. API/DTO Considerations

### 10.1 Data Availability Audit Result (V24D6G2 — Complete)

V24D6G2 confirmed the following:

1. **Squad endpoint** (`/api/v1/career/players/squad`) exposes all four mutation fields: `energy`, `injured`, `injuryType`, `injuryRemainingMatches`.

2. **Lineup endpoint** (`/api/v1/career/lineup/current`) exposes `energy` and `injured` only. `injuryType` and `injuryRemainingMatches` are not in `PlayerLineupDTO`.

3. V24D6G3 used squad endpoint data for `PlayerCardComponent` indicators. No backend/API change was required.

4. Richer lineup tooltips (e.g., `injuryRemainingMatches` in lineup editor) may require cross-referencing the squad endpoint or extending `PlayerLineupDTO` later.

### 10.2 No New Backend Fields Required

V24D6G does not create new `SessionPlayer` fields. All fields needed for UI already exist:
- `energy`
- `injured`
- `injuryType`
- `injuryRemainingMatches`

If these fields are confirmed available to the frontend, no backend schema, API, or Redis changes are needed for V24D6G UI implementation.

### 10.3 Redis Schema

No Redis schema changes. The mutation fields were designed to use existing `SessionPlayer` fields (V24D6B design principle: "no new schema"). V24D6G uses the same existing fields.

### 10.4 Backend Validation

V24D6G does not include backend lineup validation. Injured player blocking in lineup selection is a future phase (V24D6F or V24D6G extension). The UI can warn but the backend does not currently enforce.

---

## 11. Implementation Breakdown

V24D6G is too large to implement in one phase. Recommended phased approach:

| Phase | Content | Risk | Dependency |
|-------|---------|------|------------|
| **V24D6G1** | This design document | NONE | V24D6C3 complete |
| **V24D6G2** | Frontend DTO/data availability audit — verify which mutation fields are accessible to frontend | LOW | V24D6G1 complete |
| **V24D6G3** | Squad management injury + energy indicators | LOW | ✅ COMPLETE (`3675431`, front-ciber/project mvp-1) |
| **V24D6G4A** | Lineup player card condition badges/warnings | LOW | ✅ COMPLETE (`362c647`, front-ciber/project mvp-1) |
| **V24D6G4B** | Lineup confirmation warning (two-click pattern) | MEDIUM | ✅ COMPLETE (`c4681e2`, front-ciber/project mvp-1) |
| **V24D6G5A** | Dashboard next-match warning (before "Jugar Fecha") | LOW | ✅ COMPLETE (`18543dc`, front-ciber/project mvp-1) |
| **V24D6G5B** | Dashboard polish (optional, UX-driven) | LOW | Deferred — V24D6G5A only |
| **V24D6G6A** | Match detail injury/event summary | LOW | ✅ COMPLETE (`80ad1ed`, front-ciber/project mvp-1) |
| **V24D6G6B** | Deeper match detail polish (optional, UX-driven) | LOW | Deferred — V24D6G6A scope sufficient |
| **V24D6G7** | UX polish/accessibility audit | LOW | ✅ COMPLETE — audit only, no code changes needed |

**V24D6G is complete.** All V24D6G1 (design), V24D6G2 (audit), V24D6G3 (squad indicators), V24D6G4A (lineup card warnings), V24D6G4B (confirmation warning), V24D6G5A (dashboard warning), and V24D6G6A (match detail summary) phases are implemented. V24D6G7 (UX/accessibility audit) is complete — no code changes were required. All five V24D6G UI surfaces passed the audit with good UX and accessibility compliance: text+icon badges, role="alert", null-safe energy, no hard blocking, no forbidden energy delta claims. No further V24D6G work is planned unless UX review requests specific polish. V24D6G4C (squad-editor-modal) was deferred and remains dead/untracked — NOT committed.

---

## 12. Testing Strategy

Tests are future work for V24D6G3+. This section defines test intent for later implementation phases.

### 12.1 Squad Indicators (V24D6G3)

- Component renders injured badge when `injured=true`
- Component renders energy badge when `energy < 80`
- Missing `injuryRemainingMatches` defaults to "Injured" with no count
- Missing `energy` defaults to 100 (fresh)
- Mobile layout still usable at 375px width

### 12.2 Lineup Warnings (V24D6G4)

- Injured player cannot be added to starting XI when block is implemented
- Exhausted player shows warning but remains selectable
- Tired player shows informational badge only, no dialog
- Warning dialog contains actionable copy
- Warning can be dismissed without removing player from lineup

### 12.3 Dashboard Warnings (V24D6G5)

- Dashboard shows correct unavailable player count
- Warning banner appears when key players are injured/exhausted
- Warning banner does not appear when all players are fresh/available

### 12.4 Graceful Degradation

- When `energy` field is missing from API response: assume 100 (fresh), do not show energy indicator
- When `injured` field is missing: assume false (available)
- When both fields missing: all players show as "Available" with no indicators

### 12.5 Accessibility

- Screen reader announces player status on focus
- Color is not the only indicator
- Keyboard navigation works for injured/exhausted player selection

---

## 13. Risks

### Risk 1: API Fields Not Exposed

**Description:** The mutation fields (`energy`, `injured`, `injuryRemainingMatches`) may exist in Redis but not be serialized in the API response to the frontend.

**Impact:** V24D6G UI cannot be implemented until a backend API phase adds these fields.

**Mitigation:** V24D6G2 confirmed all required fields are accessible via the squad and lineup endpoints. No backend API change was needed for V24D6G3. This risk is resolved.

### Risk 2: Blocking Injured Players Too Early Frustrates Users

**Description:** If injured players are blocked from lineup selection before users understand why, support tickets may increase.

**Impact:** MEDIUM — user confusion, perceived game unfairness.

**Mitigation:** Use warning dialog with clear explanation before implementing hard block. Test with warning-only first (V24D6G4 without block), then add block only after UX validation.

### Risk 3: Stale UI State After Simulating Round

**Description:** If the UI does not refresh after a round simulation, the user may see outdated player condition.

**Impact:** LOW/MEDIUM — user sees incorrect player condition before next lineup decision.

**Mitigation:** Round simulation success handler must refresh squad/lineup state. Consider optimistic UI update or explicit refresh trigger.

### Risk 4: Color-Only Indicators Are Inaccessible

**Description:** Energy badges relying on color (green/yellow/red) without text or icon are inaccessible to color-blind users.

**Impact:** MEDIUM — exclusion of color-blind users.

**Mitigation:** Always pair color with text label and/or icon. Test with color-blind simulation tools (e.g., Chrome DevTools emulation).

### Risk 5: User Confusion When Mutation Flags Are Disabled

**Description:** If mutation flags (`mutate-career-state`, `persist-fatigue`, `persist-injuries`) are all false, players never become injured or tired. But the UI would still show energy indicators (always at 100) and no injury badges.

**Impact:** LOW — the UI indicators would be meaningless noise if mutation is disabled.

**Mitigation:** UI indicators should be data-driven: if `energy == 100` and `injured == false`, show nothing. The indicators only appear when there is actual reduced availability to report.

### Risk 6: Mutation Enables Mid-Career Without UI Warning

**Description:** If an operator enables `mutate-career-state=true` on an existing career that already has players with accumulated fatigue or injuries, the UI may suddenly change without the user expecting it.

**Impact:** MEDIUM — unexpected sudden injury/fatigue displays.

**Mitigation:** Consider a one-time notification when mutation is first enabled: "Player conditions will now affect match outcomes." This is a future backend/admin feature, not V24D6G scope.

---

## 14. Recommendation

### 14.1 V24D6G Complete — Recommended Next Phases

V24D6G is complete. All implementation and audit phases are done — no further V24D6G work is planned unless UX review requests specific polish.

**V24D6D (discipline/card persistence), V24D6D7 (suspension visibility), V24D6F (mutation regression tests), and V24D6H (yellow-card threshold) are all complete.**

**Recommended next phases (in priority order):**

1. **V24D6E — Form/Morale Persistence** — design and implement form/morale mutation and persistence
2. **V24D6I — Fatigue/Injury Regression Pacing Audit** — validate that V24D6B injury and V24D6C fatigue mutation rates produce balanced match-to-match player availability
3. **V24D6J — Discipline UI Polish** — enhance suspension indicators based on UX feedback from V24D6D7A/B deployment

Richer injury detail (e.g., `injuryRemainingMatches` tooltip) in lineup may require cross-referencing the squad endpoint or extending `PlayerLineupDTO` in a later backend change.

### 14.2 Phased Implementation Order

1. **V24D6G2** — Audit frontend data availability (blocking) — ✅ COMPLETE
2. **V24D6G3** — Squad management indicators (injury badge + energy badge) — ✅ COMPLETE (`3675431`, front-ciber/project mvp-1)
3. **V24D6G4A** — Lineup player card condition badges/warnings — ✅ COMPLETE (`362c647`, front-ciber/project mvp-1)
4. **V24D6G4B** — Lineup confirmation warning (two-click pattern) — ✅ COMPLETE (`c4681e2`, front-ciber/project mvp-1)
5. **V24D6G5A** — Dashboard squad condition warning (before "Jugar Fecha") — ✅ COMPLETE (`18543dc`, front-ciber/project mvp-1)
6. **V24D6G6A** — Match detail injury/event summary (from existing timeline) — ✅ COMPLETE (`80ad1ed`, front-ciber/project mvp-1)
7. **V24D6G7** — UX polish/accessibility audit — ✅ COMPLETE (audit only, no code changes needed)
8. **V24D6G6B / V24D6G5B** — Optional polish — Deferred until UX review requests it

### 14.3 What NOT to Do Yet

- Do **not** add new backend fields unless V24D6G2 proves fields exist but are not serialized
- Do **not** reopen V24D6G lineup UX unless UX review requests specific polish; V24D6G4A/B are complete
- Do **not** add form/morale UI until V24D6E form/morale model is designed

### 14.4 Future Backend Work (V24D6F)

V24D6F (career mutation regression tests) is now complete (V24D6F1/F2/F3 — commits `9e52b08`/`5933d1c`/`6250f11`, +15 tests). V24D6G4 lineup blocking is also already complete without waiting for V24D6F.

---

## 15. Non-Goals

V24D6G (overall) does NOT include:
- **No backend/API/Redis/schema changes** — V24D6G3, V24D6G4A, V24D6G4B, V24D6G5A, and V24D6G6A were frontend work in the separate frontend repo. V24D6G5A reused the existing endpoint `GET /api/v1/career/players/squad`; V24D6G6A used the existing `MatchDetail.timeline`; neither introduced a new API contract. V24D6G7 was audit-only and required no code changes.
- **No form/morale UI** — V24D6E not yet designed
- **No production flag changes** — mutation flags remain default-false

V24D6G1 was design-only. V24D6G2 was audit-only. V24D6G3, V24D6G4A, V24D6G4B, V24D6G5A, and V24D6G6A were frontend work in the separate frontend repo. V24D6G7 was audit-only and required no code changes. No backend/API/Redis/schema changes were introduced.

---

## 16. Completion Criteria

- [x] Document created
- [x] UX problem clearly articulated — visibility gap between mutation and user knowledge
- [x] Current backend state documented with correct commits and test count
- [x] Display status categories defined (injury + energy)
- [x] Combined status priority defined
- [x] UI surfaces identified (squad management, lineup selection, match detail, dashboard)
- [x] Visual language defined (badges, colors, icons, accessibility)
- [x] Lineup rules recommendation defined (injured block, exhausted warn, tired info)
- [x] Tooltip copy provided
- [x] API/DTO considerations documented — V24D6G2 completed; V24D6G3 implemented without backend/API changes
- [x] Phased implementation plan defined (G1–G7)
- [x] Testing strategy outlined for future phases
- [x] Risks documented with mitigations
- [x] Recommendation: V24D6G is complete. All implementation and audit phases done — no further V24D6G work unless UX review requests specific polish. Recommended next: V24D6E form/morale, injury recovery lifecycle, advanced discipline rules, or optional yellow-card counter UI if UX requests it. V24D6D, V24D6F, V24D6D7, and V24D6H are complete. V24D6G4C deferred — squad-editor-modal was untracked/dead work and was NOT committed.
- [x] Non-goals explicit
- [x] V24D6D cards/suspensions persistence is complete; suspension visibility completed through V24D6D7A/B
- [x] V24D6E form/morale UI deferred

---

*This document is the authoritative V24D6G design specification. V24D6G is complete — all implementation and audit phases done (V24D6G3 through V24D6G7). No further V24D6G work planned unless UX review requests specific polish. V24D6G4C (squad-editor-modal) was NOT committed — modal was untracked/dead work.*