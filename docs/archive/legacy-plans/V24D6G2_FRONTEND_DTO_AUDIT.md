# V24D6G2 — Frontend DTO/Data Availability Audit

**Status:** V24D6G2 — COMPLETE
**Branch:** `mvp-1-performance-cleanup`
**Audit date:** 2026-05-13
**Backend implementation commit:** `0dc184a`
**Backend tests:** 506, 0 failures
**Frontend repo:** `front-ciber/project`, branch `mvp-1`

---

## 1. Executive Summary

**Finding: All fields required for V24D6G3 squad indicators are available through the squad endpoint. Lineup-specific detail fields are partially available: `energy` and `injured` are present, while `injuryType` and `injuryRemainingMatches` are not present in `PlayerLineupDTO`.**

The four fields needed for injury and fatigue UI indicators (`energy`, `injured`, `injuryType`, `injuryRemainingMatches`) are:
- Stored correctly in `SessionPlayer` on the backend
- Correctly mapped to `SessionPlayerDTO` via `SessionEntityMapper.toDTO()`
- Present in the `/api/v1/career/players/squad` endpoint response
- `PlayerLineupDTO` contains `energy` and `injured`, but not `injuryType` or `injuryRemainingMatches`
- Correctly typed in both frontend models (`SessionPlayer` interface and `LineupPlayerData` interface)
- Already rendered in the UI (injured emoji badge on lineup cards, energy percentage on squad cards)

No backend API change, DTO change, schema migration, or Redis change is required to implement V24D6G3 squad indicators.

---

## 2. Backend API Surface — SessionPlayer Fields

### 2.1 SessionPlayerDTO (backend DTO)

**Location:** `src/main/java/com/footballmanager/adapters/in/web/career/dto/response/SessionPlayerDTO.java`

```java
public record SessionPlayerDTO(
    String sessionPlayerId,
    UUID basePlayerId,
    String name,
    Integer age,
    String position,
    Integer attack,
    Integer defense,
    Integer technique,
    Integer speed,
    Integer stamina,
    Integer mentality,
    BigDecimal marketValue,
    Integer energy,                    // ✅ PRESENT
    Integer form,
    Boolean injured,                     // ✅ PRESENT
    String injuryType,                   // ✅ PRESENT
    Integer injuryRemainingMatches,     // ✅ PRESENT
    String origin,
    Integer overall
) {}
```

### 2.2 SessionEntityMapper (backend mapper)

**Location:** `src/main/java/com/footballmanager/adapters/in/web/career/mappers/SessionEntityMapper.java`

`SessionEntityMapper.toDTO(SessionPlayer)` maps all four fields correctly:

```java
player.getEnergy(),              // → energy
player.getInjured(),             // → injured
player.getInjuryType(),          // → injuryType
player.getInjuryRemainingMatches(), // → injuryRemainingMatches
player.calculateOverall()        // → overall
```

### 2.3 PlayerLineupDTO (lineup endpoint DTO)

**Location:** `src/main/java/com/footballmanager/adapters/in/web/career/lineup/dto/PlayerLineupDTO.java`

```java
public record PlayerLineupDTO(
    String playerId,
    String name,
    String position,
    Integer overall,
    Integer energy,      // ✅ PRESENT
    Boolean injured,     // ✅ PRESENT
    Integer age
) {
    // injuryType and injuryRemainingMatches are NOT in this DTO
    // This is relevant for lineup-specific UI surfaces
}
```

Note: `PlayerLineupDTO` only has `energy` and `injured`. It does **not** include `injuryType` or `injuryRemainingMatches`. These fields are available in the squad endpoint (`/career/players/squad`) but not in the lineup endpoint.

---

## 3. Frontend API Endpoints

### 3.1 Squad Endpoint — `/api/v1/career/players/squad`

**Called from:** `SquadManagementComponent.ngOnInit()` → line 200

```
GET /api/v1/career/players/squad
```

**Response type (inline in component):**
```typescript
interface SessionPlayer {
  sessionPlayerId: string;
  name: string;
  age: number;
  position: string;
  attack: number;
  defense: number;
  technique: number;
  speed: number;
  stamina: number;
  mentality: number;
  marketValue: number;
  energy: number;          // ✅ PRESENT
  form: number;
  injured: boolean;         // ✅ PRESENT
  injuryType: string|null;  // ✅ PRESENT (NOT rendered yet)
  injuryRemainingMatches: number; // ✅ PRESENT (NOT rendered yet)
  origin: string;
}
```

**Full shared model (player.model.ts):**
```typescript
export interface SessionPlayer {
  sessionPlayerId: string;
  basePlayerId: string | null;
  name: string;
  age: number;
  position: string;
  attack: number;
  defense: number;
  technique: number;
  speed: number;
  stamina: number;
  mentality: number;
  marketValue: number;
  energy: number;              // ✅ PRESENT
  form: number;
  injured: boolean;           // ✅ PRESENT
  injuryType: string | null;   // ✅ PRESENT
  injuryRemainingMatches: number; // ✅ PRESENT
  origin: 'CLONED' | 'CUSTOM' | 'RANDOM';
}
```

### 3.2 Lineup Endpoint — `/api/v1/career/lineup/current`

**Called from:** `SquadManagementComponent.ngOnInit()` (line 179). The tracked lineup flow uses the squad-management component auto-select + confirm flow. The untracked SquadEditorModalComponent was audited during V24D6G4B and discarded as dead/untracked work — it was not imported, not routed, build passed without it, and was NOT committed.

```
GET /api/v1/career/lineup/current
```

**Response type:**
```typescript
interface LineupDTO {
  formation: string;
  players: PlayerLineupDTO[];
  confirmed: boolean;
}

interface PlayerLineupDTO {
  playerId: string;
  name: string;
  position: string;
  overall: number;
  energy: number;       // ✅ PRESENT
  injured: boolean;     // ✅ PRESENT
  age: number;
  // injuryType NOT in this DTO
  // injuryRemainingMatches NOT in this DTO
}
```

---

## 4. Field Availability Summary

| Field | Squad Endpoint (`/players/squad`) | Lineup Endpoint (`/lineup/current`) | Squad Management Component | Lineup Editor Component | Shared Model |
|-------|-----------------------------------|-------------------------------------|--------------------------|------------------------|--------------|
| `energy` | ✅ Full `SessionPlayer` | ✅ `PlayerLineupDTO` | ✅ Used in `PlayerCardComponent` | ✅ `stamina` mapped from `energy` | ✅ `player.model.ts` |
| `injured` | ✅ Full `SessionPlayer` | ✅ `PlayerLineupDTO` | ✅ Not rendered | ✅ `active: !p.injured` | ✅ `player.model.ts` |
| `injuryType` | ✅ Full `SessionPlayer` | ❌ NOT in DTO | ❌ Not used | ❌ Not used | ✅ `player.model.ts` |
| `injuryRemainingMatches` | ✅ Full `SessionPlayer` | ❌ NOT in DTO | ❌ Not used | ❌ Not used | ✅ `player.model.ts` |

---

## 5. Current UI Usage of Mutation Fields

### 5.1 PlayerCardComponent (squad display)

**File:** `front-ciber/project/src/app/shared/components/player-card/player-card.component.html`

```html
<div class="player-meta">
  <span>Age: {{player.age}}</span>
  <span *ngIf="player.form !== undefined">Form: {{player.form}}%</span>
  <span>Energy: {{player.energy}}%</span>   <!-- ✅ energy displayed -->
</div>
```

**Model:** `PlayerCardData` (player-card.model.ts) — has `energy` but NOT `injured`, `injuryType`, or `injuryRemainingMatches`

**Note:** `PlayerCardComponent` is used for squad display in `squad-management.component.html` line 206. It currently only shows `energy`, not injury status.

### 5.2 LineupPlayerCardComponent (lineup display)

**File:** `front-ciber/project/src/app/shared/components/lineup-player-card/lineup-player-card.component.html`

```html
<span class="lineup-energy" [class.low-energy]="player.energy <= 50">
  {{player.energy}}%
</span>
...
<span *ngIf="player.injured" class="injured-badge">🤕</span>  <!-- ✅ injured badge shown -->
```

**Model:** `LineupPlayerData` (lineup-player-card.model.ts) — has `energy` AND `injured`

**Note:** The injured badge (🤕 emoji) is already implemented! When `player.injured === true`, a badge is shown. No injury type or remaining matches shown.

### 5.3 SquadEditorModalComponent (DEAD/UNTRACKED — NOT COMMITTED)

The `squad-editor-modal` component was audited during V24D6G4B and found to be dead/untracked work:
- Not imported in any module
- Not routed in `app.routes.ts`
- Build passes without it
- Entire file (~1208 lines) showed as untracked with no git history
- Condition warning logic for modal selection was NOT committed

The tracked lineup flow is handled by:
- `squad-management.component.ts` — auto-select + two-click confirm pattern (V24D6G4B)
- `lineup-player-card.component.ts` — condition badges on lineup cards (V24D6G4A)
- `dashboard.component.ts` — squad condition warning before "Jugar Fecha" (V24D6G5A)

The squad-editor-modal remains untracked and should be ignored unless revived with a legitimate use case.

---

## 6. Gap Analysis

### Gap 1: PlayerCardComponent now uses `injured` field

✅ **RESOLVED by V24D6G3.** `PlayerCardData` model now includes `injured`, `injuryType`, and `injuryRemainingMatches`. Injury badge + "Out N matches" / "Returning soon" are displayed in `PlayerCardComponent`.

### Gap 2: PlayerCardComponent now uses `injuryType` and `injuryRemainingMatches`

✅ **RESOLVED by V24D6G3.** Injury detail now displays "Out N matches" or "Returning soon" when available, with "Unavailable" fallback for null/zero remaining matches.

### Gap 3: LineupPlayerCardComponent shows injured badge but no match count

The injured emoji badge is shown but no remaining match count is displayed. Adding `"Out {{player.injuryRemainingMatches}} matches"` would require the model to include that field.

### Gap 4: PlayerLineupDTO does not include `injuryType` or `injuryRemainingMatches`

The lineup endpoint response does not include these fields. However, `injuryRemainingMatches` is primarily useful for squad management (knowing how long until a player returns), not for lineup selection (where injured players should be blocked anyway).

---

## 7. Findings Detail

### Finding 1: All mutation fields traverse the API boundary correctly

- `SessionPlayerDTO` includes all four fields
- `SessionEntityMapper.toDTO()` maps all four fields correctly
- Squad endpoint (`/career/players/squad`) returns all fields
- Frontend `SessionPlayer` interface in `player.model.ts` has all fields correctly typed

### Finding 2: Injury badge (🤕) already exists in LineupPlayerCardComponent

The `injured: boolean` is already used in the template to show a "🤕" badge. This is proof the field traverses the API and the frontend can conditionally render based on it.

### Finding 3: Energy display already exists in both components

- `PlayerCardComponent` displays `Energy: {{player.energy}}%`
- `LineupPlayerCardComponent` displays `{{player.energy}}%` with a `low-energy` CSS class when `energy <= 50`

### Finding 4: No backend changes needed for V24D6G3

The API already exposes all required fields. V24D6G3 is a pure frontend UI enhancement using already-available data.

### Finding 5: `injuryType` and `injuryRemainingMatches` are only in squad endpoint

These fields are NOT in `PlayerLineupDTO` (lineup endpoint). This is acceptable because:
- `injuryRemainingMatches` is most useful for squad management display, not lineup selection
- Injured players should be blocked from lineup selection regardless of remaining matches
- Lineup editor can still check `injured` from `PlayerLineupDTO` without needing remaining matches

---

## 8. Implementation Recommendation

### V24D6G3 — Squad Management Indicators (COMPLETED ✅)

**Commit:** `3675431` (`feat: add V24D6G3 squad player condition indicators`, front-ciber/project mvp-1)

**No backend changes were required.** All data was already available from the squad endpoint.

**What was implemented:**
- `PlayerCardData` model extended with `injured?`, `injuryType?`, `injuryRemainingMatches?`
- `PlayerCardComponent` added condition helpers: `isInjured()`, `injuryLabel()`, `injuryDetail()`, `injuryTooltip()`, `energyStatus()`, `energyLabel()`, `energyTooltip()`, `energyPercent()`
- Energy display upgraded: "Energy: 86% · Fresh" with status thresholds (Fresh 80–100, Good 60–79, Tired 40–59, Very Tired 20–39, Exhausted 0–19)
- Injury display added: 🤕 "Injured" + "Out N matches" / "Returning soon" / "Unavailable" fallback

### V24D6G4A — Lineup Player Card Condition Warnings (COMPLETED ✅)

**Commit:** `362c647` (`feat: add V24D6G4A lineup condition warnings`, front-ciber/project mvp-1)

**No backend changes were required.** Lineup endpoint already exposed `energy` and `injured`.

**What was implemented:**
- `LineupPlayerCardComponent` added condition helpers: `isInjured()`, `energyPercent()`, `isExhausted()`, `isVeryTired()`, `isTired()`, `conditionWarningLabel()`, `conditionWarningTooltip()`, `conditionClass()`, `hasConditionWarning()`
- Condition badges: `⚠️ Injured`, `⚡ Exhausted`, `⚡ Very tired`, `⚡ Tired`
- CSS classes: `condition-injured` (crimson), `condition-exhausted` (orange), `condition-very-tired` (amber), `condition-tired` (green)
- Injury takes priority over exhaustion when both apply
- Warnings only — no hard blocking

**NOT committed in this phase:** squad-editor-modal condition warning logic (showConditionWarning, conditionWarning$ state). The modal file showed as untracked with a large diff (~1208 lines), which was not safe to commit without path/diff hygiene review.

### V24D6G4B — Lineup Confirmation Warning (COMPLETED ✅)

**Commit:** `c4681e2` (`feat: add V24D6G4B lineup confirmation warning`, front-ciber/project mvp-1)

**No backend changes required.** Frontend-only change in the separate frontend repo.

**What was implemented:**
- `confirmationWarning$` and `pendingRiskyConfirm$` BehaviorSubjects added to `SquadManagementComponent`
- `buildRiskyLineupMessage()` — detects injured/exhausted/very-tired players in lineup (risk rules: injured === true, exhausted: energy <= 19, very tired: energy 20–39)
- `resetLineupWarning()` — clears warning state
- `onConfirmLineup()` — two-click pattern: first click warns if lineup contains risky players, second click proceeds
- `onAutoSelect()` — resets warning when user auto-selects lineup
- Amber warning box with ⚠️ icon displayed above confirm button when risky players detected
- No hard blocking — warning only, user chooses to proceed
- No new HTTP calls — uses existing `/career/lineup/confirm` only
- No backend/API/Redis/schema changes

**V24D6G4C — Squad-editor-modal condition warning logic (DEFERRED)**
The `squad-editor-modal` component was untracked/dead work — not imported, not routed, build passes without it. The condition warning logic for modal selection was NOT committed. This remains a potential future phase if the modal is ever revived with a legitimate use case.

---

## 9. Files Inspected

### Backend (root repo)
| File | Purpose |
|------|---------|
| `SessionPlayerDTO.java` | DTO record with all mutation fields |
| `SessionEntityMapper.java` | Maps SessionPlayer → SessionPlayerDTO |
| `CareerPlayerController.java` | Exposes `/career/players/squad` endpoint |
| `LineupController.java` | Exposes `/career/lineup/current` endpoint |
| `PlayerLineupDTO.java` | DTO for lineup player data |

### Frontend (front-ciber/project repo)
| File | Purpose |
|------|---------|
| `player.model.ts` | `SessionPlayer` interface with all fields |
| `player-card.model.ts` | `PlayerCardData` model (missing injury fields) |
| `lineup-player-card.model.ts` | `LineupPlayerData` model with `energy` + `injured` |
| `squad-management.component.ts` | Calls `/career/players/squad` and `/career/lineup/current` |
| `squad-management.component.html` | Uses `app-player-card` and `app-lineup-player-card` |
| `player-card.component.html` | Shows `energy` but no injury badge |
| `lineup-player-card.component.html` | Already shows 🤕 injured badge and energy |
| `squad-editor-modal.component.ts` | DEAD/UNTRACKED — not imported, not routed, NOT committed — audited and discarded during V24D6G4B |
| `career.service.ts` | CareerService endpoint definitions |
| `career.model.ts` | `SessionTeam`, `CareerStatus` interfaces |

---

## 10. Non-Goals for This Audit

- No API changes recommended or required
- No backend implementation
- This audit itself did not implement frontend code — it was design-only
- Subsequent frontend phases V24D6G3, V24D6G4A, V24D6G4B, and V24D6G5A were implemented in the separate frontend repo
- V24D6G5A reused existing endpoint `GET /api/v1/career/players/squad` and introduced no new API contract
- No backend/API/Redis/schema changes were required
- No Redis schema changes
- No new test files needed for this audit

---

## 11. Conclusion

**V24D6G2 audit is complete. V24D6G3 squad indicators are also complete.**

**✅ V24D6G3 — Committed as `3675431`** (front-ciber/project mvp-1)

**Summary:**
- `energy` — ✅ available everywhere, already displayed
- `injured` — ✅ available everywhere, already triggers 🤕 badge in lineup card (V24D6G3 adds to squad card)
- `injuryType` — ✅ in squad endpoint, not in lineup endpoint (acceptable)
- `injuryRemainingMatches` — ✅ in squad endpoint, not in lineup endpoint (acceptable for V24D6G3–G4)

**Lineup endpoint detail:** `PlayerLineupDTO` exposes `energy` and `injured`. `injuryType` and `injuryRemainingMatches` are not present — acceptable because injured players are blocked from lineup selection anyway, and remaining match count is most useful for squad management display. V24D6G4 may cross-reference squad data or extend `PlayerLineupDTO` if richer lineup tooltips are needed.

**✅ V24D6G5A — Committed as `18543dc`** (front-ciber/project mvp-1)

**Summary:**
- Dashboard squad condition warning added above "Jugar Fecha" button
- Uses existing endpoint `GET /api/v1/career/players/squad` (no new API contract)
- Shows injured/exhausted/very-tired player counts before round simulation
- No hard blocking — warning only, user can proceed
- Error-safe: squad load failure emits empty array, warning hidden

**✅ V24D6G6A — Committed as `80ad1ed`** (front-ciber/project mvp-1)

**Summary:**
- Post-match injury/event summary added to match detail page
- Uses existing `MatchDetail.timeline` — INJURY, YELLOW_CARD, RED_CARD events
- No new HTTP calls — data already loaded by the page
- No API/DTO/model changes
- No energy/fatigue delta claims — match detail DTO does not expose before/after energy or energy delta
- Shows: injury count, player names, minutes, card counts
- Empty state: "No injury events recorded for this match."

**V24D6G7-0 Audit — Committed as part of `8acfae7` docs commit**

**Audit result:** All five V24D6G UI surfaces passed UX/accessibility audit — no code changes required.

**Surfaces audited:**
- Squad player cards — text+color energy labels, injury badge with text, null-safe handling, tooltips ✅
- Lineup player cards — condition badges with icon+text, injury priority, null-safe energy, tooltips ✅
- Lineup confirmation warning — two-click pattern clear, role="alert", warning resets correctly, no hard blocking ✅
- Dashboard warning — warning above "Jugar Fecha", role="alert", no button blocking, error-safe, no double-counting ✅
- Match detail condition summary — uses existing timeline, no energy delta, empty state clear, card counts conditional ✅

**Validation:** `npx tsc --noEmit` ✅ `npx ng build` ✅ BUILD SUCCESS

**V24D6G is complete.** All V24D6G3 through V24D6G7 phases are implemented and audited. No further V24D6G work planned unless UX review requests specific polish.

**Recommended next phases (from V24D6G design):** V24D6D (discipline/cards), V24D6E (form/morale), or V24D6F (mutation regression tests).

*This document is the authoritative V24D6G2 audit record.*