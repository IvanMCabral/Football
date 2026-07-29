# MVP 1 Player Attribute Methodology

Verdict: APPROVED

Cutoff date: 2026-07-29.

The MVP 1 dataset uses real public player identities, but all gameplay attributes are MANAGER estimates.

## Attribute scale

All attributes use a 1-99 scale. The generated dataset keeps values inside the playable range and uses club reputation, tactical role and deterministic per-player seed to avoid cloned squads.

Attributes currently consumed:

- attack;
- defense;
- technique;
- speed;
- stamina;
- mentality.

## Estimation inputs

- normalized tactical position;
- club reputation;
- deterministic player identity seed;
- roster balance needed by the current MVP importer;
- MANAGER-owned variation rules.

The methodology intentionally does not use ratings, potential, values or traits copied from Football Manager, EA Sports, eFootball, Transfermarkt or other commercial databases.

## Position normalization

Public sources sometimes expose only broad positions or omit positions for several players. For MVP playability, every club is normalized to:

- 2 GK;
- 7 DEF;
- 7 MID;
- 4 WINGER;
- 4 ATT.

When a public position is unavailable, `positionEstimated=true` and `positionSourceRef` points to the MANAGER methodology instead of presenting the value as source-verified.

## Special traits

Each player has exactly two explicit special traits in source JSON. Trait compatibility is validated against `special-attributes.json` by the importer.

## Review expectation

This methodology is deterministic and good enough for a playable MVP. It is not intended to be a real scouting model.
