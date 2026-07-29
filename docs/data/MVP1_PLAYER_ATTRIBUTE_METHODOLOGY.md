# MVP 1 Player Attribute Methodology

## Verdict

`APPROVED WITH ISSUES` for the current repository dataset.

The methodology is suitable for MANAGER-owned fictional or explicitly licensed player datasets. It is not evidence that real player identities can be redistributed.

## Scale

All gameplay attributes use a 1-99 scale:

- 1-39: non-competitive for a first-division squad.
- 40-59: squad-depth profile.
- 60-74: regular professional profile.
- 75-84: strong first-division profile.
- 85-99: elite profile.

## Attributes consumed by the game

| Attribute | Meaning | Main consumers | Validation |
| --- | --- | --- | --- |
| attack | Finishing, chance threat, offensive positioning | match engine, lineup scoring, attacking balance | 1-99 |
| defense | Tackling, marking, defensive reliability | match engine, lineup scoring, defensive balance | 1-99 |
| technique | Passing, control, execution quality | match events, possession, ratings | 1-99 |
| speed | Pace, acceleration, transition threat | wide play, counters, event selection | 1-99 |
| stamina | Work rate sustain, fatigue resistance | energy, intensity, ratings | 1-99 |
| mentality | Composure, pressure handling, discipline baseline | match volatility, morale, ratings | 1-99 |

## Position consistency rules

The dataset stores concrete domain positions:

- GK
- LB, CB, RB, LWB, RWB
- CDM, CM, CAM, LM, RM
- LW, RW
- CF, ST

Validation groups them as GK, DEF, MID, WINGER and ATT.

Rules:

- Goalkeepers must have stronger defense/stability than attack.
- Central defenders should have defense and height profiles compatible with aerial/defensive traits.
- Fullbacks and wingbacks should not be slow low-stamina profiles unless intentionally defensive.
- Midfielders should have technique and mentality close to their overall level.
- Wingers should have speed and technique coherent with wide-play traits.
- Forwards should have attack above their defensive profile.

These are guardrails, not hard stereotypes; profiles can vary within sane limits.

## Trait assignment

Each player source file must explicitly define exactly two different special attributes. The importer never assigns traits by index.

Validation checks:

- both traits exist in the catalog;
- the two traits are different;
- trait slots are stable;
- trait position compatibility is respected;
- orphan trait relations do not exist after import.

## Source policy

For fictional players:

- identity, ratings, value, salary proxy and traits are MANAGER-created;
- the dataset is redistributable as project data.

For real players:

- names and personal data require a source with redistribution rights or legal approval;
- commercial ratings must not be copied;
- closed-provider data must not be used as a source for versioned repository data.

## Current repository status

The current `src/main/resources/data/initial/players` dataset is explicit and reviewable player by player, but it is fictional. It replaces runtime roster generation as the default import source while preserving legal safety.
