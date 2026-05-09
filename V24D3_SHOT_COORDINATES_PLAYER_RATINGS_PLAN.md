# V24D3 — Shot Coordinates + Player Ratings Planning

**Status:** PLANNING ONLY — No code implementation until plan is reviewed and approved
**Branch:** `mvp-1-performance-cleanup`
**Created:** 2026-05-08
**Parent commit:** `72c40d7` (docs: update engine docs after V24D2 completion)
**Tests:** 237 baseline, 0 failures

---

## 1. V24D2 State Audit

### What V24D2 Delivered

| Component | Status |
|-----------|--------|
| `V24AssistModel` | Complete — pure function assist/key-pass selection |
| `selectAssistProvider(...)` | Weighted by formation/style/stamina |
| `assistProbability(...)` | Clamped [0.10, 0.85] |
| GOAL `relatedPlayerId`/`relatedPlayerName` | Attached via V24DetailedMatchEngine integration |
| V24MatchEvent schema | **Unchanged** — no field added |
| V24PlayerSelector | **Unchanged** — original methods preserved |
| V24MatchContext | **Unchanged** |
| V24DetailedMatchResult | **Unchanged** |
| Tests | 22 new (`V24AssistModelTest`), 237 total |

### Current V24MatchEvent Fields

```java
int minute;
V24MatchEventType type;
String teamId;
String playerId;          // real from SessionPlayer
String playerName;        // real from SessionPlayer
String relatedPlayerId;  // assist provider (may be null)
String relatedPlayerName;
double xg;
String description;
```

**No shot coordinate fields. No player rating fields.**

### Current V24ShotLocation (Categorical Only)

```java
SIX_YARD_BOX,        // xG ≈ 0.40–0.60
PENALTY_AREA_CENTER, // xG ≈ 0.20–0.35
PENALTY_AREA_WIDE,   // xG ≈ 0.12–0.20
OUTSIDE_BOX,         // xG ≈ 0.05–0.10
LONG_RANGE           // xG ≈ 0.01–0.05
```

### Current V24ShotQuality (Input Bundle)

```java
V24ShotLocation location;
double shooterQuality;    // [0, 100]
double assistQuality;    // [0, 100]
double defensivePressure; // [0, 100]
double goalkeeperQuality; // [0, 100]
double tacticModifier;    // [0.5, 1.5]
```

### Current V24DetailedMatchResult

```java
String matchId;
String homeTeamId, awayTeamId;
int homeGoals, awayGoals;
double homeXg, awayXg;
int homeShots, awayShots;
int homePossession, awayPossession;
V24MatchTimeline timeline;
String summary;
```

**No playerRatings field. No per-player stat map.**

---

## 2. Current Limitations

| Gap | Impact |
|-----|--------|
| No x/y coordinates | No shot map UI possible |
| V24ShotLocation is categorical | Cannot compute distance/angle to goal |
| No shot angle/distance | xG cannot use physical proximity modifier |
| No goalkeeper save event quality | Saves are binary per xG threshold |
| No per-match player rating | Cannot rank player performance post-match |
| No rating contribution tracking | No assist/goal/skill contribution scoring |
| No per-player stat summary | No Map<playerId, statBundle> in result |
| V24DetailedMatchResult is summary-only | Ratings would need new result field or helper |

---

## 3. Shot Coordinate Model Options

### Option SA-1 — V24ShotCoordinate Value Object (Recommended for V24D3A)

```java
public final class V24ShotCoordinate {
    private final double x;          // 0–100, attacking toward 100
    private final double y;          // 0–100, left to right
    private final V24ShotLocation location;
    private final double distanceToGoal;
    private final double angleToGoal;
    private final boolean insideBox;

    public V24ShotCoordinate(double x, double y, V24ShotLocation location,
            double distanceToGoal, double angleToGoal, boolean insideBox) { ... }
    // immutable, validated, getters
}
```

**Pros:** Clean OO model, easy to test, future shot map ready
**Cons:** Requires decision on where to attach/store (event schema vs internal only)

### Option SA-2 — Keep Coordinates Internal Only

Generate `V24ShotCoordinate` inside `V24DetailedMatchEngine.attemptShot()` but do not persist to event. Use only to derive distance/angle for potential xG refinement.

**Pros:** No schema change required
**Cons:** Cannot be retrieved from timeline/result later

### Recommended Path

**V24D3A:** Add `V24ShotCoordinate` + `V24ShotCoordinateGenerator`. Attach as optional metadata to `V24MatchEvent` only if V24MatchEvent schema change is deemed safe. Otherwise keep internal.

---

## 4. V24MatchEvent Schema Decision

### Decision Point: Add shotCoordinate to V24MatchEvent?

**Option EV-1 — Add optional field:**
```java
private final V24ShotCoordinate shotCoordinate; // null for non-shot events
```
**Pros:** Shot map ready, coordinates retrievable from timeline, clean
**Cons:** Schema change to isolated v24 class, requires test updates

**Option EV-2 — Keep V24MatchEvent unchanged:**
Store coordinates in `V24ShotQuality` or separate `V24ShotRecord` attached to shot events.
**Pros:** No event schema change, lower risk
**Cons:** No shot map without timeline re-serialization

**Decision:** Prefer EV-2 for maximum safety (no V24MatchEvent change). If coordinates prove valuable in V24D3A testing, promote to EV-1 in V24D3C.

---

## 5. Coordinate Generation Design

### Pitch Coordinate System

```
x: 0 ←————————————————————————→ 100
y: 0 ↓
    Goal center: x=100, y=50

Penalty box (insideBox=true):
  x >= 83 AND 21 <= y <= 79

Six-yard box:
  x >= 94 AND 36 <= y <= 64

Goal posts approximation:
  left post: x≈100, y≈43
  right post: x≈100, y≈57
  center: x=100, y=50
```

### Coordinate by V24ShotLocation (Deterministic)

```java
// All coordinates use passed Random for determinism
// Ranges are [min, max] inclusive

SIX_YARD_BOX:       x ∈ [94, 99], y ∈ [42, 58]
PENALTY_AREA_CENTER: x ∈ [83, 95], y ∈ [30, 70]
PENALTY_AREA_WIDE:   x ∈ [83, 93], y ∈ [18, 82]
OUTSIDE_BOX:         x ∈ [60, 84], y ∈ [15, 85]
LONG_RANGE:          x ∈ [35, 62], y ∈ [10, 90]
```

### Derived Values

**distanceToGoal:**
```java
// Euclidean distance from shot point to goal center (100, 50)
double dx = 100.0 - x;
double dy = 50.0 - y;
double distance = Math.sqrt(dx*dx + dy*dy);  // ≈ 5–65
```

**angleToGoal (approximate):**
```java
// Simple angular approximation relative to goal center
double angle = Math.toDegrees(Math.atan2(dy, dx)); // degrees
// Clamped to [-90, 90] where 0 = straight on
```

**insideBox:**
```java
boolean insideBox = (x >= 83.0 && y >= 21.0 && y <= 79.0);
```

---

## 6. xG Integration Options

### Option X1 — No xG Formula Change (Default)
Coordinates are descriptive metadata only. Existing xG formula unchanged.

### Option X2 — Add Coordinate Modifier to xG
```java
double distMod = 1.0 - (distanceToGoal / 100.0) * 0.15; // up to -15% for long shots
double xgModified = xg * distMod;
```
**Risk:** MEDIUM — changes xG distribution. Requires revalidation against 237 tests.

**Recommendation:** Use X1 for V24D3A. Coordinate modifier can be explored in V24D3B if time permits.

---

## 7. Player Ratings Model Options

### Option R1 — V24PlayerRatingModel Helper (Recommended)

```java
public final class V24PlayerRatingModel {

    public double computePlayerRating(V24PlayerMatchState player,
            V24MatchTimeline timeline, String playerId) {
        double rating = BASE_RATING;
        rating += goalBonus(timeline.goalsFor(playerId));
        rating += assistBonus(timeline.assistsFor(playerId));
        rating += shotBonus(timeline.shotsFor(playerId), timeline.xgFor(playerId));
        rating += yellowCardPenalty(timeline.yellowCardsFor(playerId));
        rating += redCardPenalty(timeline.redCardsFor(playerId));
        rating += injuryPenalty(timeline.injuriesFor(playerId));
        return clampRating(rating);
    }

    private static final double BASE_RATING = 6.0;
    private static final double GOAL_BONUS = 0.8;
    private static final double ASSIST_BONUS = 0.5;
    private static final double SHOT_BONUS = 0.1;       // per non-goal shot
    private static final double KEY_PASS_BONUS = 0.3;
    private static final double YELLOW_PENALTY = -0.3;
    private static final double RED_PENALTY = -1.5;
    private static final double INJURY_PENALTY = -0.2;

    private double clampRating(double r) {
        return Math.max(1.0, Math.min(10.0, r));
    }
}
```

**Pros:** Pure helper, no state mutation, easy to test, no schema change
**Cons:** Ratings computed post-match only, not available during simulation

### Option R2 — Mutable currentRating in V24PlayerMatchState
Add `double currentRating` field, update during events.

**Risk:** HIGH — mutates V24PlayerMatchState which is intended to be match-state snapshot only. Rejected by V24 isolation principle.

### Option R3 — Rating Map in V24DetailedMatchResult
Compute ratings post-simulation and attach as `Map<String, Double> playerRatings` field.

**Pros:** Clean result enrichment
**Cons:** Requires V24DetailedMatchResult schema change

**Recommendation:** Prefer R1 (helper-only, no schema change). If result enrichment is desired, do R3 as V24D3C.

---

## 8. V24DetailedMatchResult Schema Decision

### Option DR-1 — No Result Schema Change (Default)
Keep ratings helper-only. V24DetailedMatchResult unchanged.

### Option DR-2 — Add playerRatings Field
```java
private final Map<String, Double> playerRatings; // playerId → rating
```
**Pros:** Complete result includes performance data
**Cons:** Schema change, builder update needed

**Decision:** Defer DR-2 to V24D3C if at all. R1 is sufficient for V24D3A/B.

---

## 9. Test Plan

### V24ShotCoordinateTest (~10 tests)
- `sixYardBoxCoordinateIsCloseToGoal`: distance < 10
- `longRangeCoordinateIsFartherThanInsideBox`: distance > insideBox distance
- `insideBoxFlagTrueWhenInPenaltyArea`: x >= 83 && y in [21, 79]
- `insideBoxFlagFalseWhenOutsideBox`: x < 83
- `tapInCoordinateIsWithinSixYardBoxBounds`
- `penaltyAreaWideIsLeftOrRightOfCenter`: y far from 50
- `sameSeedProducesSameCoordinate`: determinism
- `coordinateStaysWithinPitch`: x ∈ [0, 100], y ∈ [0, 100]
- `distanceAndAngleAreFinite`: no NaN/Infinity
- `penaltyKickLocationIsCentral`: y ≈ 50, x near 88

### V24PlayerRatingModelTest (~12 tests)
- `baseRatingIsSix`: fresh player with no events gets 6.0
- `goalIncreasesRatingBy08`: 1 goal → 6.8
- `twoGoalsIncreaseRatingBy16`: 2 goals → 7.6
- `assistIncreasesRatingBy05`: 1 assist → 6.5
- `multipleAssistsStack`: 2 assists → 7.0
- `yellowCardDecreasesBy03Each`: 1 yellow → 5.7, 2 → 5.4
- `redCardDecreasesBy15`: red card → 4.5
- `injuryDecreasesBy02`: injury → 5.8
- `ratingClampedBetweenOneAndTen`: combinations stay in range
- `sameTimelineProducesSameRating`: determinism
- `noEventsGivesBaseRating`: empty timeline → 6.0
- `shotsContributeSmallBonus`: shots without goals add small value

### Integration Tests
- `V24DetailedMatchEngineStillDeterministic`: existing 237-test regression gate must pass
- `V24TimelineConsistencyTestStillPasses`: no stat consistency regressions

---

## 10. Risk Assessment

| Implementation | Risk | Mitigation |
|----------------|------|------------|
| V24ShotCoordinate value object | LOW | Pure data class, easy tests |
| Coordinate generator with Random | LOW | Determinism tests cover seeded behavior |
| Attach coordinates to V24MatchEvent | MEDIUM | Schema change, requires existing test updates |
| V24PlayerRatingModel helper | LOW | Pure helper, no state mutation |
| Rating map in V24DetailedMatchResult | MEDIUM | Schema + builder change |
| xG recalibration with coordinates | MEDIUM/HIGH | Changes goal distribution; requires full revalidation |
| Production integration | HIGH | Not allowed in V24D3 scope |

---

## 11. Recommended V24D3 Split

### V24D3A — Shot Coordinates (Isolated, Low Risk)
- `V24ShotCoordinate` value object
- `V24ShotCoordinateGenerator` (uses passed Random)
- Coordinates stay internal to engine or attach to `V24ShotQuality` only
- Do NOT change `V24MatchEvent` schema yet
- 10 new tests (`V24ShotCoordinateTest`)

### V24D3B — Player Ratings Helper (Isolated, Low Risk)
- `V24PlayerRatingModel` helper
- Rating computation from `V24MatchTimeline`
- Helper only — no result schema change
- 12 new tests (`V24PlayerRatingModelTest`)

### V24D3C — Event/Result Schema Enrichment (Optional, Medium Risk)
- If V24D3A proves safe: add optional `shotCoordinate` to `V24MatchEvent`
- If V24D3B proves valuable: add optional `playerRatings` to `V24DetailedMatchResult`
- Requires careful test updates
- Document decision at V24D3A/B completion

### V24D3D — Documentation Update
- Update V24D plan to reflect V24D3A/B completion
- Mark recommended next: V24D4 (Storage/API design) or Phase 6C

---

## 12. Non-Negotiable Constraints

- **No production wiring** — V24 stays isolated under `application/service/simulation/v24/`
- **No Redis/API/frontend changes**
- **No V23 source modifications**
- **No MatchEngineImpl changes**
- **No LeagueSimulator changes**
- **No SessionPlayer mutation** — V24PlayerMatchState is a match-state snapshot only
- **No SessionTeam mutation**
- **No V24MatchContext modification**
- **V24MatchEvent schema change only if V24D3A/B explicitly decides it is safe**
- **Existing 237 tests are the regression gate** — all must pass after any V24D3 change

---

## 13. Required Regression Command

```
mvn test -Dtest=V24AssistModelTest,V24FormationParserTest,V24SubstitutionEngineTest,V24InjuryModelTest,V24DisciplineModelTest,V24FatigueModelTest,V24DetailedMatchEngineDeterminismTest,V24TimelineOrderingTest,V24DetailedMatchResultAdapterTest,V24MatchContextValidationTest,V24TimelineConsistencyTest,V24ShotXgModelTest,V24PlayerAttributionTest,LeagueSimulatorTest,MatchResultDataAdapterTest,TeamOverallCalculatorTest,MatchEngineImplStrengthSimulationTest,MatchEngineImplStyleSimulationTest,MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,MatchEngineImplMetricsValidationTest,MatchEngineImplPoissonValidationTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest
```

**Expected:** 237 tests + new V24D3A/B tests, 0 failures total.

---

## 14. Decision Points Before Code Implementation

1. **V24MatchEvent schema change?** — Decide before V24D3A coding begins. Safe default: no change until V24D3C.
2. **V24DetailedMatchResult playerRatings field?** — Decide before V24D3B coding begins. Safe default: helper-only (R1) until V24D3C.
3. **Coordinate attachment point?** — Options: internal-only (SA-2), V24ShotQuality metadata, or V24MatchEvent optional field (EV-1).
4. **xG coordinate modifier?** — Defer to V24D3B or V24D3C if at all. Safe default: X1 (no formula change).
5. **V24D3C needed?** — Only if V24D3A/B reveal that schema enrichment is clearly beneficial.

---

## 15. Files Likely Affected

| File | Change | Risk |
|------|--------|------|
| `V24ShotCoordinate.java` | New — value object | LOW |
| `V24ShotCoordinateGenerator.java` | New — coordinate factory | LOW |
| `V24PlayerRatingModel.java` | New — rating helper | LOW |
| `V24DetailedMatchEngine.java` | Use coordinate generator in attemptShot() | LOW |
| `V24DetailedMatchResult.java` | Optional: add playerRatings field | MEDIUM |
| `V24MatchEvent.java` | Optional: add shotCoordinate field | MEDIUM |
| `V24ShotCoordinateTest.java` | New — 10 tests | NONE |
| `V24PlayerRatingModelTest.java` | New — 12 tests | NONE |

**Non-Goals:** No V24PlayerMatchState mutation, no SessionPlayer mutation, no SessionTeam changes.

---

*This document is the authoritative V24D3 implementation specification. No code implementation begins until this document is reviewed and a specific V24D3 phase is approved.*
