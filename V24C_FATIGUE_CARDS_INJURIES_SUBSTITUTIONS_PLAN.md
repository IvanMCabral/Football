# V24C — Fatigue, Cards, Injuries, and Substitutions Plan

**Status:** PLANNING — No code yet
**Branch:** `mvp-1-performance-cleanup`
**Created:** 2026-05-06
**Latest baseline:** `b4735a8` (V24B complete, 142 tests)
**Next:** `057551a` (docs update)

---

## 1. Current V24B State

### What already exists in V24DetailedMatchEngine (V24B)

- **Minute loop** (1-90) with possession tick per minute
- **Chance creation** — probabilistic per minute/style, broader than shot
- **Shot/xG generation** — V24ShotXgCalculator multi-factor model, location, shooter quality, assist, pressure, GK, style
- **Goal resolution** — xG threshold + randomness
- **Player attribution** — V24PlayerSelector uses real SessionPlayer IDs/names
- **Deterministic seed** — `new Random(seed)`, identical result same seed
- **Stats consistency** — goals=goalEvents, shots>=goals, possession=100, xG sum of shot events

### Existing V24B card/injury/substitution logic (basic/implicit)

V24B already has placeholder-style card and injury events:

- **Fouls:** `if (random.nextDouble() < 0.06)` — picks shooter, generates FOUL event
- **Yellow cards:** `if (random.nextDouble() < 0.45 && !f.redCard())` — adds yellow to player via `f.addYellowCard()`
- **Injuries:** `if (random.nextDouble() < 0.005)` — calls `p.injure()` and generates INJURY event
- **Substitutions:** `attemptSubstitution()` after minute 60, max 3 per team, substitutes tired players (currentStamina < 60) off bench

### V24PlayerMatchState fields already available

| Field | Used by |
|-------|---------|
| `currentStamina` | Yes — read in substitution candidate check |
| `yellowCards` | Yes — checked before adding second yellow |
| `redCard` | Yes — checked before yellow card |
| `injured` | Yes — checked in substitution candidate filter |
| `onPitch` | Yes — all selector/onPitch filters check this |

### Gaps in V24B for V24C

1. **No stamina drain** — currentStamina is set once at match start and never reduced; doesn't affect player performance
2. **No fatigue penalty** — xG calculation does not factor in stamina; fatigued players shoot as well as fresh players
3. **No second-yellow-to-red enforcement** — `addYellowCard()` already sets `redCard=true` when yellowCards>=2, but no separate RED_CARD event is generated
4. **No straight red** — not modeled
5. **No injury probability modulation** — injury probability is flat 0.005/minute; doesn't increase with low stamina or high-intensity actions
6. **No foul probability modulation** — foul probability is flat 0.06; doesn't account for stamina, defensive pressure, or style
7. **Substitution engine is simplistic** — only substitutes tired players; doesn't prioritize injured first; red-carded players cannot be substituted
8. **No fatigue-based xG penalty** — shooterQuality from V24PlayerSelector doesn't consider currentStamina
9. **No stamina recovery** — bench players retain their pre-match stamina; no recovery over match duration

---

## 2. V24C Design Goals

V24C extends the isolated V24 detailed engine with:

1. **Stamina drain model** — per-minute drain + extra drain per action (shot, foul, chance involvement)
2. **Fatigue impact on performance** — stamina affects xG, shot accuracy, foul probability, injury probability, substitution priority
3. **Foul model with style/stamina modulation** — foul probability varies by style, stamina level, defensive pressure
4. **Cards model** — yellow card after foul, second yellow → red card with RED_CARD event, red card sets onPitch=false
5. **Injury model** — probability modulated by stamina and action intensity
6. **Substitution logic** — prioritize injured and very tired players; red-carded players are removed but cannot be substituted; team plays with one fewer

All V24C changes remain isolated under `src/main/java/com/footballmanager/application/service/simulation/v24/`. No production wiring.

---

## 3. Non-Goals

Explicitly out of scope for V24C:

- **No Redis persistence** — stamina/card/injury state lives only in V24PlayerMatchState during match simulation
- **No API changes** — V24DetailedMatchResult is output-only; MatchFixture.MatchResultData stays unchanged
- **No frontend changes**
- **No LeagueSimulator integration** — V24 remains parallel, not wired into production flow
- **No MatchEngineImpl or V23 behavior changes**
- **No full tactical AI** — no x/y coordinates, no pressing logic, no off-ball movement
- **No 2D player coordinates**
- **No advanced medical system** — no severity levels, no multi-match injuries, no recovery modeling
- **No xG penalty for carded players beyond their stamina/performance effect**

---

## 4. Proposed Helper Classes

**Recommended approach: Option B — small focused helpers**

| Helper | Purpose | Rationale |
|--------|---------|-----------|
| `V24FatigueModel` | Pure function: given player state + style + action → stamina drain + fatigue factor | Testable, deterministic, no side effects |
| `V24DisciplineModel` | Pure function: given player state + style + minute → foul probability + yellow probability | Testable, deterministic |
| `V24InjuryModel` | Pure function: given player state + action type → injury probability | Testable, deterministic |
| `V24SubstitutionEngine` | Stateful but isolated: manages bench, tracks subs used, selects candidate | Moderate complexity |

### V24FatigueModel

```java
public class V24FatigueModel {
    // Base drain per minute of play (possession)
    int baseDrainPerMinute(TeamStyle style); // returns 3-6 depending on style

    // Extra drain when player is involved in high-intensity action
    int actionDrain(V24PlayerMatchState player, boolean shotAttempt, boolean foulCommitted);

    // Fatigue factor [0.0, 1.0] from currentStamina
    double fatigueFactor(V24PlayerMatchState player); // 1.0 = fresh, 0.0 = exhausted

    // Apply drain to player state
    void applyDrain(V24PlayerMatchState player, int amount);
}
```

### V24DisciplineModel

```java
public class V24DisciplineModel {
    // Foul probability per minute, modulated by style + stamina
    double foulProbability(V24PlayerMatchState player, TeamStyle style, boolean defending, Random random);

    // Yellow card probability given a foul was committed
    double yellowCardProbability(V24PlayerMatchState player, int existingYellows, Random random);

    // Should generate second-yellow-red event?
    boolean shouldProduceRedCard(V24PlayerMatchState player); // existingYellows >= 1
}
```

### V24InjuryModel

```java
public class V24InjuryModel {
    // Base injury probability per minute
    double baseInjuryProbability();

    // Adjusted probability based on stamina and action intensity
    double adjustedInjuryProbability(V24PlayerMatchState player, boolean highIntensityAction, Random random);
}
```

### V24SubstitutionEngine

```java
public class V24SubstitutionEngine {
    // maxSubs: 5 per team (allow configurable)
    // track subs used per team
    // select candidate: injured first, then very tired, then tired+yellow; red-carded players cannot be substituted
    // select bench player: same position preferred, compatible fallback, any if needed
    // produce SUBSTITUTION event
}
```

---

## 5. Fatigue Model

### Starting stamina

`V24PlayerMatchState.fromSessionPlayer()` already reads `player.getEnergy()` (starts 100) as `currentStamina`. If null, falls back to 100. This is correct — no change needed.

### Per-minute drain

| Style | Base drain/minute |
|-------|-------------------|
| ATTACKING | 6 |
| POSSESSION | 5 |
| COUNTER | 5 (burst-like — higher drain when active) |
| DEFENSIVE | 4 |
| BALANCED | 5 |

### Action drain

| Action | Extra drain |
|--------|-------------|
| Shot taken | +8 stamina |
| Foul committed | +5 stamina |
| Chance involved (not shot) | +3 stamina |

Total drain per minute = base drain + action extras. Clamped to [0, 100].

### Fatigue factor table

| currentStamina | Fatigue factor | Effect |
|----------------|----------------|--------|
| 80-100 | 1.0 (fresh) | No penalty |
| 60-79 | 0.95 | Tiny xG penalty |
| 40-59 | 0.85 | Moderate xG penalty, slightly higher foul probability |
| 20-39 | 0.70 | Strong penalty: xG reduced, shot accuracy lower |
| 0-19 | 0.50 | Severe penalty: xG -50%, very low shot accuracy |

### Fatigue impact on xG

In `V24PlayerSelector.shooterQuality()`, multiply by fatigue factor:

```java
double shooterQuality(V24PlayerMatchState player) {
    double base = attack/99 * 0.6 + form/100 * 0.4;
    double fatigue = fatigueFactor(player); // from V24FatigueModel
    return base * fatigue;
}
```

Fatigue also increases foul probability (tired players commit more fouls) and injury probability.

---

## 6. Cards/Fouls Model

### Foul probability per minute

Base: 0.04 (lower than current 0.06 flat rate). Modulated by:

| Factor | Effect |
|--------|--------|
| DEFENSIVE style | +0.02 |
| COUNTER style | +0.01 |
| Low stamina (<50) | +0.015 |
| Very low stamina (<30) | +0.03 |
| Defender position | +0.01 |
| High defensive pressure | +0.01 |

### Yellow card probability after foul

Base: 0.40. Modified by:
- Player already has 1 yellow: 0.55
- BALANCED/DEFENSIVE style: +0.05

### Second yellow → red card

When `player.yellowCards() >= 2`:
1. Generate `YELLOW_CARD` event (the second yellow)
2. Generate `RED_CARD` event with the same player
3. Call `player.giveRedCard()` → onPitch = false, redCard = true
4. **Do NOT generate a SUBSTITUTION event.** Red-carded players cannot be replaced; the team continues with one fewer player.

### Red card event

`RED_CARD` event generated at the minute of the second yellow. Player removed from pitch immediately and cannot be substituted. Team plays with one fewer player.

### Straight red (deferred)

Not included in V24C. Can be added in V24D if needed.

---

## 7. Injury Model

### Base injury probability

Base: 0.003 per minute (lower than current flat 0.005).

### Modulation factors

| Factor | Effect |
|--------|--------|
| Stamina < 40 | +0.004 |
| Stamina < 20 | +0.008 |
| High-intensity action (shot, chance) | +0.002 |
| ATTACKING/COUNTER style | +0.001 |

Maximum adjusted probability: 0.02 per minute.

### Injury event

When injury occurs:
1. Generate `INJURY` event with real player ID/name
2. Call `player.injure()` → injured=true, onPitch=false
3. Generate `SUBSTITUTION` event if bench available

### No severity levels

V24C uses simple binary: injured or not. No multi-match injury modeling (that's persistence/career-layer, not match simulation).

---

## 8. Substitution Model

### Constraints

- Max 5 substitutions per team per match
- Substitution windows: after minute 60, and at halftime (minute 45) if enabled
- V24C default: after minute 60 only (simpler)

### Priority order for substitution candidate

1. **Injured players** (onPitch=true, injured=true) — must come off immediately
2. **Very tired players** (currentStamina < 30) — tactical
3. **Tired + yellow-carded players** (currentStamina < 50, yellowCards >= 1) — risk of second yellow

**Red-carded players are never substitution candidates.** They leave the pitch but cannot be replaced; the team continues with one fewer player.

### Bench selection

1. Prefer same position as player being substituted off
2. Compatible fallback: GK→GK only, DEF→DEF/MID, MID→MID/DEF/WINGER, WINGER→WINGER/MID/ATT, ATT→ATT/WINGER
3. Any on-bench player if no positional match

### Substitution event

```java
timeline.addEvent(new V24MatchEvent(
    minute, V24MatchEventType.SUBSTITUTION,
    teamRole,
    subOff.sessionPlayerId(), subOff.name(),
    subOn.sessionPlayerId(), subOn.name(),
    0.0,
    "Substitution: " + subOn.name() + " on for " + subOff.name()
));
```

### Duplicate prevention

Track `Set<String> substitutedPlayerIds` per team. Once a player is substituted off, they cannot be substituted back on.

---

## 9. Event Consistency Rules

These invariants must hold after V24C:

| Rule | Enforcement |
|------|-------------|
| Red-carded players are not onPitch | V24PlayerMatchState.giveRedCard() sets onPitch=false |
| Red-carded players are never substituted | V24SubstitutionEngine never selects redCard==true as candidate |
| RED_CARD events must not have a relatedPlayerId | V24DetailedMatchEngine does not pass relatedPlayerId for RED_CARD |
| SUBSTITUTION events must not use red-carded players | V24SubstitutionEngine filters redCard==true from candidate set |
| Injured players are not onPitch | V24PlayerMatchState.injure() sets onPitch=false |
| Substituted-off players are not onPitch | V24SubstitutionEngine.substituteOff() |
| Substituted-on players are onPitch | V24SubstitutionEngine.substituteOn() |
| No player receives more than 2 yellows | V24PlayerMatchState.addYellowCard() caps at 2 |
| Second yellow creates red event + state | V24DetailedMatchEngine detects yellowCards==2 → RED_CARD event + giveRedCard() |
| No more than 5 substitutions per team | V24SubstitutionEngine tracks subsRemaining per team |
| Events sorted by minute | V24MatchTimeline.addEvent() sorts |
| Event minutes 1-90 | V24MatchEvent constructor validates |
| All card/injury/sub events use real player IDs | V24PlayerMatchState.sessionPlayerId used directly |
| Goals = goalEvents count | Verified in V24TimelineConsistencyTest (unchanged) |
| Shots >= goals | Verified in V24TimelineConsistencyTest (unchanged) |
| Possession sums to 100 | Verified in V24TimelineConsistencyTest (unchanged) |

---

## 10. Stats Impact

V24C adds new event types but must not break existing stats invariants:

| Stat | V24B guarantee | V24C impact |
|------|---------------|-------------|
| Goals = GOAL event count | Must hold | Unchanged — V24C adds no GOAL logic |
| Shots >= goals | Must hold | Unchanged — V24C adds no shot logic |
| xG = sum of shot event xG | Must hold | Unchanged — V24C adds no xG calculation |
| Possession = 100 | Must hold | Unchanged — V24C adds no possession logic |
| Timeline ordered | Must hold | V24C events must respect minute ordering |
| Real player IDs | Must hold | V24C must use real IDs for all card/injury/sub events |

---

## 11. Files Likely Affected

### Source files

| File | Change |
|------|--------|
| `V24DetailedMatchEngine.java` | Integrate V24C helpers into minute loop; replace flat probabilities with modulated models; apply stamina drain per action; generate RED_CARD events |
| `V24PlayerMatchState.java` | Likely minimal — already has all needed fields; may add `fatigueFactor()` helper method |
| `V24TeamMatchState.java` | Likely minimal — already manages player lists |
| `V24FatigueModel.java` | **New** — stamina drain, fatigue factor |
| `V24DisciplineModel.java` | **New** — foul/yellow/red probability |
| `V24InjuryModel.java` | **New** — injury probability |
| `V24SubstitutionEngine.java` | **New** — substitution selection and event generation |

### Test files

| File | Tests | Purpose |
|------|-------|---------|
| `V24FatigueModelTest.java` | ~5 | Stamina drain, fatigue factor ranges, clamp behavior |
| `V24DisciplineModelTest.java` | ~5 | Foul probability, yellow card, second-yellow-red |
| `V24InjuryModelTest.java` | ~3 | Injury probability, low stamina increase |
| `V24SubstitutionEngineTest.java` | ~6 | Priority order, max subs, position preference, no duplicates |
| `V24TimelineConsistencyTest.java` | add ~3 assertions | Cards/injuries/subs in timeline with valid minutes and real IDs |

---

## 12. Testing Plan

### V24FatigueModelTest

| Test | What it validates |
|------|-------------------|
| `staminaNeverBelowZero` | drain cannot make currentStamina negative |
| `staminaNeverAbove100` | no overflow above starting stamina |
| `fatigueFactorIsInRange` | fatigueFactor returns [0.0, 1.0] |
| `actionDrainReducesStamina` | shot/foul action increases drain |
| `lowStaminaProducesLowerFatigueFactor` | fatigue factor decreases as stamina decreases |

### V24DisciplineModelTest

| Test | What it validates |
|------|-------------------|
| `foulProbabilityVariesWithStyle` | DEFENSIVE > BALANCED > POSSESSION |
| `lowStaminaIncreasesFoulProbability` | stamina < 40 increases foul chance |
| `yellowCardUsesRealPlayer` | YELLOW_CARD event has real playerId |
| `secondYellowProducesRedCard` | RED_CARD event generated when yellowCards reaches 2 |
| `redCardedPlayerOffPitch` | after RED_CARD, player.onPitch() == false |

### V24InjuryModelTest

| Test | What it validates |
|------|-------------------|
| `injuryProbabilityIsLow` | base probability <= 0.01 |
| `lowStaminaIncreasesInjuryRisk` | stamina < 40 gives higher probability |
| `injuryEventUsesRealPlayer` | INJURY event has real playerId |

### V24SubstitutionEngineTest

| Test | What it validates |
|------|-------------------|
| `injuredPlayerIsSubstitutedWhenBenchAvailable` | priority order, injured first |
| `tiredPlayerCanBeSubstituted` | stamina < 30 triggers sub candidacy |
| `noMoreThanFiveSubstitutions` | subsRemaining decrements, stops at 0 |
| `replacementPrefersSamePosition` | bench selection prefers matching position |
| `noDuplicateSubstitutions` | same player not subbed twice |
| `redCardedPlayerCannotBeSubstituted` | red-carded player off pitch and no SUBSTITUTION event generated for that player; substitutionsUsed does not increase because of red card |

### V24TimelineConsistencyTest additions

- `cardsAndInjuriesHaveValidMinutes` — all events in [1, 90]
- `cardEventsHaveRealPlayerIds` — YELLOW/RED card playerId from SessionPlayer
- `substitutionEventsHaveBothPlayerIds` — relatedPlayerId present for SUBSTITUTION

---

## 13. Regression Command

After all V24C subphases, the full validation command:

```
mvn test -Dtest=V24DetailedMatchEngineDeterminismTest,V24TimelineOrderingTest,V24DetailedMatchResultAdapterTest,V24MatchContextValidationTest,V24TimelineConsistencyTest,V24ShotXgModelTest,V24PlayerAttributionTest,V24FatigueModelTest,V24DisciplineModelTest,V24InjuryModelTest,V24SubstitutionEngineTest,LeagueSimulatorTest,MatchResultDataAdapterTest,TeamOverallCalculatorTest,MatchEngineImplStrengthSimulationTest,MatchEngineImplStyleSimulationTest,MatchQualityMetricsTest,V23SimulationQualityGateTest,MatchEngineImplRoleContributionTest,MatchEngineImplEventConsistencyTest,MatchEngineImplDeterminismTest,MatchEngineImplMetricsValidationTest,MatchEngineImplPoissonValidationTest,MatchQualityComputerTest,MatchEngineImplTest,DivisionTest
```

Expected: 162+ tests, 0 failures.

---

## 14. Phased Implementation Recommendation

Split V24C into 4 sequential commits for incremental validation:

### V24C1 — Fatigue Model

**Source:** `V24FatigueModel.java` + integration into `V24DetailedMatchEngine`
**Tests:** `V24FatigueModelTest.java` + existing V24 consistency tests

Changes:
- Add `V24FatigueModel` with `baseDrainPerMinute()`, `actionDrain()`, `fatigueFactor()`
- In minute loop: call `V24FatigueModel.applyDrain()` on player involved in shot/action
- In `V24PlayerSelector.shooterQuality()`: multiply by `fatigueFactor()`
- No card/injury/substitution changes yet

**Validation:** Fatigue tests pass + all existing V24 tests pass
**Risk:** LOW — isolated class, additive changes only

### V24C2 — Discipline Model

**Source:** `V24DisciplineModel.java` + integration into `V24DetailedMatchEngine`
**Tests:** `V24DisciplineModelTest.java` + existing V24 consistency tests

Changes:
- Add `V24DisciplineModel` with modulated foul/yellow probability
- In minute loop: replace flat `random.nextDouble() < 0.06` with `V24DisciplineModel.foulProbability()`
- Generate `YELLOW_CARD` and `RED_CARD` events with real player IDs
- Second yellow triggers RED_CARD event + `giveRedCard()` → player off pitch; no substitution attempted for red-carded player

**Validation:** Discipline tests pass + all existing V24 tests pass
**Risk:** LOW — isolated class, existing V24B foul/yellow logic replaced with better model

### V24C3 — Injury Model

**Source:** `V24InjuryModel.java` + integration into `V24DetailedMatchEngine`
**Tests:** `V24InjuryModelTest.java` + existing V24 consistency tests

Changes:
- Add `V24InjuryModel` with modulated injury probability
- Replace flat `random.nextDouble() < 0.005` with `V24InjuryModel.adjustedInjuryProbability()`
- Generate `INJURY` event with real player ID

**Validation:** Injury tests pass + all existing V24 tests pass
**Risk:** LOW — isolated class, existing V24B injury logic replaced with better model

### V24C4 — Substitution Engine

**Source:** `V24SubstitutionEngine.java` + integration into `V24DetailedMatchEngine`
**Tests:** `V24SubstitutionEngineTest.java` + existing V24 consistency tests

Changes:
- Add `V24SubstitutionEngine` with priority-based candidate selection and bench selection
- Replace existing `attemptSubstitution()` with calls to `V24SubstitutionEngine`
- Apply priority order: injured > very tired > tired+yellow; red-carded players are never substitution candidates
- Prevent duplicate substitutions

**Validation:** Substitution tests pass + all existing V24 tests pass
**Risk:** MEDIUM — substitution logic is the most complex V24C component

### Rollback Plan

For any V24C subphase, rollback is:
```bash
git checkout HEAD~1 -- src/main/java/com/footballmanager/application/service/simulation/v24/
git checkout HEAD~1 -- src/test/java/com/footballmanager/application/service/simulation/v24/
```

V24 remains isolated — rollback does not affect production V23 behavior.

---

## 15. Risk Assessment

| Subphase | Risk | Success Criteria |
|----------|------|------------------|
| V24C1 Fatigue | LOW | fatigueFactor [0.5-1.0], stamina drains, xG penalty visible, no existing test fails |
| V24C2 Discipline | LOW | foul rate stable, second-yellow-red works, no existing test fails |
| V24C3 Injury | LOW | injury rate low, real player attribution, no existing test fails |
| V24C4 Substitution | MEDIUM | priority order correct, max 5 subs enforced, no duplicate subs, no existing test fails |

**Because V24 is isolated from production, V24C errors do not affect V23 simulation or production behavior.**

---

*This document is the authoritative V24C implementation specification. All code changes must conform to this plan. Update this document before making any V24C code changes if scope changes.*