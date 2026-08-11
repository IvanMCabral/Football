# World V2 Serialization Authority

The authority cross-checks Java instance fields with Jackson serialization and deserialization properties.

| Type | Jackson properties | Classified | Uncovered |
|---|---:|---:|---:|
| `WorldSnapshot` | 9 | 9 | 0 |
| `WorldTeam` | 10 | 10 | 0 |
| `WorldPlayer` | 17 | 17 | 0 |
| `WorldLeague` | 4 | 4 | 0 |
| Total | 40 | 40 | 0 |

`allWorldTeams` and `allWorldPlayers` are derived read views over `worldTeams` and `worldPlayers`. They are not independent persisted state, are marked `@JsonIgnore`, are classified as `DERIVED_VIEW_IGNORED`, and are not emitted in JSON. Their canonical maps remain the material serialized representation.

The fail-closed check compares both directions: every actual property must be classified and every classification must still exist. A destructive test introduces an extra Jackson getter and verifies that the gate reports it.
