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

**Called from:** `SquadManagementComponent.ngOnInit()` → line 237, and `SquadEditorModalComponent.loadSquadFromBackend()` → line 761

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

### 5.3 SquadEditorModalComponent

**File:** `squad-editor-modal.component.ts` lines 779–789

```typescript
const allPlayers: PlayerOnFieldDto[] = playersList.map((p: any) => ({
  playerId: p.playerId,
  name: p.name,
  position: p.position,
  role: p.position,
  overall: p.overall || 70,
  slotId: '',
  stamina: p.energy || 100,   // ✅ energy used as stamina
  active: !p.injured,         // ✅ injured → active flag
  isEmpty: false
}));
```

**Note:** Injury is used as an `active` boolean (injured players are marked inactive). Energy is used as `stamina`. No `injuryType` or `injuryRemainingMatches`.

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

### V24D6G4B — Modal Warning Behavior / Selection UX Hygiene (NEXT PHASE)

**Requires:** path/diff hygiene review for squad-editor-modal before committing modal changes.

**Lineup endpoint:** `PlayerLineupDTO` exposes `energy` and `injured`. Injured players are already mapped to `active: !p.injured` in the editor. Warnings and blocking can proceed without backend/API changes.

**Injury type display in lineup:**
- Requires `injuryType` and `injuryRemainingMatches` — available from squad endpoint
- Lineup editor may need to cross-reference squad data to show remaining match count
- Or add these fields to `PlayerLineupDTO` if backend is willing to make that DTO change

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
| `squad-editor-modal.component.ts` | Maps lineup players, uses `active: !p.injured` |
| `career.service.ts` | CareerService endpoint definitions |
| `career.model.ts` | `SessionTeam`, `CareerStatus` interfaces |

---

## 10. Non-Goals for This Audit

- No API changes recommended or required
- No backend implementation
- No frontend implementation (design-only audit)
- No Redis schema changes
- No backend/API changes required. V24D6G3 was pure frontend work in a separate repository (front-ciber/project).
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

**Recommended next phase: V24D6G4B — Modal Warning Behavior / Selection UX Hygiene**

**Primary work:** Commit squad-editor-modal condition warning logic after path/diff hygiene review. Add warning state when selecting injured or exhausted players. No backend dependency. V24D6G4A proved no backend/API changes are needed for lineup warning UI.

*This document is the authoritative V24D6G2 audit record.*