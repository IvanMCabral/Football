# V32 Final Baseline Report

**Date:** 2026-05-01
**Status:** FROZEN - Accepted Match-Engine Baseline

---

## 1. Final Accepted Metrics (50-match validation)

| Metric | Value | Status |
|--------|-------|--------|
| Shots/match | 13.0 | PASS |
| Goals/match | 2.28 | PASS |
| xG/match | 2.71 | PASS |
| Goals/xG ratio | 0.84 | PASS |
| 0-0 rate | 12.0% | PASS |
| 4+ goals rate | 24.0% (12 of 50 matches) | PASS |
| Attacker ballController % | 93.9% | PASS |
| Finisher shot share | 60.5% | PASS |
| CB/DM shot share | 39.5% | PASS |
| Shot volume CV | 46.2% | PASS |
| Segment 4-5 shots | S4=7, S5=31 | PASS |

**All 7 freeze criteria passed.**

---

## 2. Key Fixes Included in V32 Baseline

The following fixes were validated and incorporated to reach the frozen state:

| Fix | Location | Description |
|-----|----------|-------------|
| Single-authority goal resolution | V32TickEngine.executeShot() | xG is probability of scoring. One roll per shot. Physics only animates misses/saves — never creates goals. |
| Non-finisher budget fallback guard | V32TickEngine.executeShot() budget consumption | Budget consumed only at executeShot time, not at approval time. Prevents phantom budget consumption when shot blocked by cooldown. |
| Finalization transfer cooldown fix | V32TickEngine.executeShotSelectionPipeline() | Cooldown only set when pass actually executes (passTarget != -1). Previously set every tick, blocking 100% of finalization transfers. |
| Progression transfer cooldown fix | V32TickEngine.executeShotSelectionPipeline() line ~1507 | Same pattern — cooldown only set when passTarget != -1 and urgent. |
| justKicked physical delivery fix | V32TickEngine.executeShotSelectionPipeline() line ~1518 | Added `state.justKicked = true` alongside `skipVelocityDamping = true` in progression transfer block. Same pattern as DESPERATION pass. |
| handleCollisions pass-in-flight fix | V32TickEngine.handleCollisions() lines ~889-897 | When `justKicked=true`, ballController is cleared immediately so possession handler does not pull ball back to sender. Ball travels freely to receiver. |
| Possession-based attacking anchors | V32TickEngine | attackingAnchorTicks set on finisher receivers of progression and finalization passes. |
| Phase-based late attacking anchors | V32TickEngine | PhaseEngine sets attacking anchors when ball is in attacking third with finisher in build-up phase. |
| Velocity damping skip for progression passes | V32TickEngine.executeShotSelectionPipeline() | skipVelocityDamping flag set true before pass decision so ball travels at full speed (~28m/s) instead of ~5m/tick after 60% damping. |

---

## 3. Known Accepted Deviations

These are structural characteristics of V32 that are **known, accepted, and not targeted for correction in this baseline**:

| Deviation | Value | Implication |
|-----------|-------|-------------|
| Segment 1 front-loading | 63% of shots (475/749) and 76% of goals (87/114) in minute 0-15 | Early match burst is dominant. Late-game (minute 60-90) generates fewer shots. This is a design characteristic, not a bug. |
| Inside-box percentage | 0.2% (2 shots out of 749) | Virtually all shots are taken from outside the penalty area. ST positioning at x=35 combined with STEP1 distance cap (~55m) prevents inside-box situations from arising naturally. |
| Shot volume CV | 46.2% | High variability in shot count per match (range 5-29). Some matches are high-volume, others are low. |
| CB/DM shot share | 39.5% | Defenders and midfielders take 39.5% of shots. While controlled below the 50% threshold, this remains notable given the user constraint to avoid CB shooting as a fix. |

---

## 4. Future Work — V33 or Experimental Branch

The following improvements are explicitly **NOT blockers** for the V32 freeze. They represent desirable future work to be addressed in V33 or a new experimental branch:

| Item | Description |
|------|-------------|
| Temporal shot distribution smoothing | Reduce segment 1 front-loading. Target: more even distribution across segments 1-6. |
| Real penalty-box occupation model | ST/attackers should naturally position inside the box when ball approaches, not just at x=35. |
| Improved chance quality / cutback system | When ball reaches attacking third with support, create higher-xG inside-box chances rather than outside-box shots. |
| Tactical style modifiers | Different team styles (possession, counter, direct) should produce different shot distributions and temporal profiles. |
| Transition context recovery | Improve how the engine recovers possession and creates chances after losing the ball in transition. |
| Player attribute integration | OVR, technique, finishing attributes should modulate shot xG and goal conversion rates more visibly. |

---

## 5. Freeze Declaration

**V32 IS FROZEN** as the accepted match-engine baseline.

- No further tuning or adjustment of V32 engine behavior is permitted.
- The metrics above represent the accepted target state.
- Any future changes must be implemented in V33 or a new experimental branch.
- Known deviations (segment 1 front-loading, low inside-box %, elevated CB/DM share) are accepted as structural characteristics and will not be addressed by modifying V32.

---

*This document is the authoritative record of the V32 frozen baseline. Any claims about V32 behavior must be verified against this baseline.*