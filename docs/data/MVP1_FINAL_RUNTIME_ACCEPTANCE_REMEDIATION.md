# MVP 1 final runtime acceptance remediation

Date: 2026-07-30

## Stack restart and recovery

The application was restarted against the principal `football_manager` database using `.env` variables loaded without printing secrets.

Validated after restart:

- PostgreSQL reachable on `localhost:5432`.
- Redis reachable on `localhost:6379`.
- Backend started on `localhost:8080`.
- Frontend served `http://localhost:4200/` with HTTP 200.
- Backend login endpoint responded with HTTP 400 for an intentionally empty body, proving request path reached the application after restart.
- Backend Flyway log showed `football_manager` schema at migration `1`, up to date.

## Principal database invariants

Real DB query result:

| Check | Result |
| --- | ---: |
| Countries (`ESP`, `ARG`, `BRA`) | 3 |
| Leagues | 3 |
| Clubs | 70 |
| Teams | 70 |
| Players | 1680 |
| Player trait rows | 3360 |
| Players without exactly two traits | 0 |
| Duplicate player source IDs | 0 |

## Three-league runtime API acceptance

World API checks with a real authenticated user after `/api/v1/dashboard/reload-world`:

| Country | Teams | Sample team | Players | Players with two traits |
| --- | ---: | --- | ---: | ---: |
| ESP | 20 | Real Madrid | 24 | 24 |
| ARG | 30 | River Plate | 24 | 24 |
| BRA | 20 | Flamengo | 24 | 24 |

Career API checks with real authenticated users:

| Country | Team | Squad | Auto-select lineup | Round 1 fixtures | Standings |
| --- | --- | ---: | --- | ---: | ---: |
| ESP | Real Madrid | 24 | 11 players / 11 slots | 10 | 20 |
| ARG | River Plate | 24 | 11 players / 11 slots | 15 | 20 |
| BRA | Flamengo | 24 | 11 players / 11 slots | 10 | 20 |

## Restart issue remediated during acceptance

The first backend restart attempt failed because an embedded PowerShell `.env` parser stripped the first and last characters from unquoted values, turning `localhost` into `ocalhos`. The accepted runtime path loads `.env` in the parent PowerShell session and launches Spring Boot with inherited environment variables. The backend then connected to `jdbc:postgresql://localhost:5432/football_manager`.

## Result

Runtime and restart acceptance are complete for MVP 1.

---

## Final evidence closure addendum — 2026-07-30

The final acceptance pass added concrete browser and process-restart artifacts:

- browser smoke artifact: `D:\temp\mvp1-browser-smoke\visual-smoke-results.json`;
- screenshots for Spain, Argentina and Brazil under `D:\temp\mvp1-browser-smoke`;
- pre-restart runtime artifact: `D:\temp\mvp1-runtime-before-restart.json`;
- post-restart runtime artifact: `D:\temp\mvp1-runtime-after-restart.json`;
- backend listener PID changed from `25364` to `26580`;
- same persisted career, lineup, fixture and standings were recovered after restart.

The final principal database validation remains:

```text
countries=3
leagues=3
clubs=70
teams=70
players=1680
player_special_attributes=3360
missing_player_source=0
orphan_traits=0
duplicate_player_source_ids=0
players_without_two_traits=0
question_mark_names=0
clubs_with_less_than_two_gk=0
```

Final runtime remediation verdict: `APPROVED`.
