# World V2 Null Semantics

World V2 uses explicit presence metadata. A missing field preserves the canonical value, a present field with `null` writes an explicit historical null, and a present non-null value replaces the canonical value.

`WorldSnapshotOverlay.changedSnapshotFields` applies this contract to `createdAt` and `lastUpdated`. `WorldTeamDelta`, `WorldPlayerDelta`, and `WorldLeagueDelta` already use typed `changedFields` sets, so their nullable values have the same three-state behavior.

The typed authority classifies 26 overlay values:

- 24 nullable scalar/timestamp fields permit canonical null and owner explicit null;
- `skillLevels` and `specialTraits` are structural collections whose empty state is material and whose model contract normalizes null.

Physical coverage includes all 24 nullable values with canonical non-null and owner explicit null, the timestamp inverse/null/null/no-delta cases, explicit empty string, and explicit empty map. Every case crosses Redis, PREPARED, COMMITTED, a new repository instance, and the semantic comparator.
