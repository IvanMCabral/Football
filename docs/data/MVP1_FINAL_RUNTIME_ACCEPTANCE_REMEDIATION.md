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
