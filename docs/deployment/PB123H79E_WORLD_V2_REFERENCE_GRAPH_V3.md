# World V2 Persisted Reference Graph V3

The discovery roots are `WorldSnapshot`, `CareerSave`, `RuntimeMatch`, `MatchState`, `MatchCommand`, `Standing`, `DetailedMatchData`, and `BaselineState`.

Fresh discovery results:

- persisted roots: 8;
- reachable models: 45;
- model fields traversed: 347;
- container paths: 70;
- durable reference paths in the exact registry: 25;
- unresolved generic paths: 0;
- uncovered registered paths: 0.

Traversal covers nested objects, arrays, collections, sets, optionals, atomic references, map keys, map values, parameterized types, and cycles. Two documented compatibility unions resolve historical polymorphic fields without treating raw `Object` as an unchecked terminal.

Self-destruction injects seven uncovered reference shapes: direct scalar, list, set, map key, map value, nested collection, and a removed-players-like map. All seven fail the registry gate. Runtime extraction validates career teams, players, squads, starting XI, lineup slots, fixtures, standings, aliases, and removed-player structures.
