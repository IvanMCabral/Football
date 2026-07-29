# MVP 1 Three-League Data Requirements

## Purpose

This document defines the dataset requirements for the next MVP 1 phase. It does not load the full dataset. It describes what must be true before importing at least three complete leagues.

## League selection criteria

Choose three leagues that provide:

- clear country and league identity;
- enough clubs for a playable season;
- enough public information to create believable squads;
- no licensing risk from copying proprietary datasets directly;
- good football variety for testing the engine.

Recommended first set for development:

- Spain first division style league;
- England first division style league;
- Argentina first division style league.

Names and data must be entered from permitted sources or manually curated. Avoid copying protected database dumps.

## Countries

Each country must include:

- stable id;
- ISO-like code;
- display name;
- demonym/nationality;
- optional confederation label.

## Clubs

Each club must include:

- stable source id;
- name;
- short name;
- country;
- league/division;
- stadium when known or generated;
- reputation;
- budget baseline;
- primary/secondary colors if needed by frontend.

Avoid using name as the only key. `source_system + source_id` must be stable.

## Expected player count

For each club:

- minimum squad size: 18 players;
- preferred squad size: 23 to 25 players;
- maximum initial import size: 30 players.

For three 20-club leagues, expected MVP 1 dataset size:

- minimum: 1080 players;
- preferred: 1380 to 1500 players;
- maximum: 1800 players.

## Required player fields

Each player must include:

- stable source id;
- full name;
- display name;
- age or date of birth;
- nationality;
- club/team relation or explicit free-agent status;
- primary position;
- secondary positions;
- dominant foot;
- height;
- shirt number when available;
- market value when used;
- salary when used;
- physical state defaults;
- injury/suspension defaults;
- six core football attributes;
- exactly two special attributes.

## Required football attributes

All imported players must have:

- attack;
- defense;
- technique;
- speed;
- stamina;
- mentality.

Scale:

- minimum: 1;
- maximum: 99;
- default for generated unknowns: 50;
- no nulls in final MVP dataset.

## Special attributes

Every imported MVP 1 player must have exactly two traits selected from `special_attributes`.

Rules:

- no duplicated trait for a player;
- one trait per slot;
- slot values are `1` and `2`;
- traits are qualitative;
- traits must be searchable and reusable.

Initial trait catalog candidates:

- `clutch_finisher`
- `press_resistant`
- `aerial_specialist`
- `line_breaker`
- `leader`
- `workhorse`
- `set_piece_specialist`
- `one_on_one_keeper`
- `wide_runner`
- `ball_winner`

The importer may expand the catalog only through reviewed additions.

The import flow must validate the two trait codes before inserting player rows. `PlayerSpecialAttributeSelectionValidator` rejects zero, one, more than two, duplicates, blanks and unknown codes. PostgreSQL then enforces FK integrity, unique `(player_id, special_attribute_id)`, unique `(player_id, slot)` and slot values `1` and `2`.

## Data quality

Minimum quality gates:

- 100% clubs linked to country and league/division;
- 100% players linked to nationality;
- 100% rostered players linked to club/team;
- 100% players with primary position;
- 100% players with six numeric attributes in range;
- 100% players with exactly two special attributes;
- 100% players with height either known in the valid `160..210` range or explicitly nullable while the row is still non-final/transitional;
- no duplicate player source ids;
- no duplicate club source ids;
- no orphan squad rows.

## Sources and licensing

Allowed:

- manually curated development data;
- public facts used in a transformed way;
- generated fictional lower-tier filler data;
- user-created fixtures.

Avoid:

- copying commercial football database exports;
- scraping sources that disallow reuse;
- importing personal data beyond football-public identity fields;
- embedding external proprietary ids without source review.

## Import/update strategy

The importer must support:

- idempotent upsert;
- source system and source id;
- repeatable updates;
- club updates;
- squad updates;
- player transfers;
- free agents;
- changed shirt numbers;
- changed attributes;
- validation report before commit.
- a single transaction or staging/import status that prevents partial final datasets when player special attributes are invalid.

## Acceptance criteria

The three-league dataset is acceptable when:

- all quality gates pass;
- backend starts from empty database;
- seed/import can be repeated without duplicates;
- a career can be created in each league;
- lineup works for every team;
- at least one detailed match can be simulated per league;
- player statistics persist and can be queried;
- frontend can browse career, squad, match detail and harness flows.
