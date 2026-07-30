# MVP 1 backend restart and recovery evidence

Date: 2026-07-30

## Restart path

The backend process was stopped and relaunched against the principal database with environment variables inherited from a `.env`-loaded PowerShell session.

No secrets were printed.

## Recovery evidence

Backend startup log confirmed:

- Java 21.0.8 runtime.
- Spring Boot 3.2.1 application startup.
- Flyway connected to `jdbc:postgresql://localhost:5432/football_manager`.
- Flyway validated migration `1`.
- Schema was up to date.
- Netty started on port `8080`.

Post-restart HTTP evidence:

- `POST /api/v1/auth/login` with `{}` returned HTTP 400, proving the backend request path was reachable and handled by the app.
- `GET http://localhost:4200/` returned HTTP 200 after frontend restart.

Post-restart data evidence:

- World reload succeeded for a real authenticated user.
- Three leagues loaded.
- Real squads loaded in Spain, Argentina and Brazil.
- Career start, squad read, auto-select, round-1 fixture read and standings read succeeded after backend recovery.

## Result

Backend restart and recovery are proven for MVP 1 acceptance.

---

## Granular process restart evidence — 2026-07-30 12:25 ART

The final closure re-ran the restart path with concrete PID and artifact recovery evidence.

### Before restart

Runtime artifact snapshot was written to `D:\temp\mvp1-runtime-before-restart.json` without secrets:

- user ID: `b8492d2b-453b-4454-9637-164e5ce0553a`
- career ID / session ID: `010f32eb-36cf-4993-91e8-344d09dadcce`
- league: Spanish Primera Division
- club: Real Madrid
- lineup players: 11
- lineup slots: 11
- round-1 fixtures: 10
- selected fixture ID: `437a9650-1d87-483c-80dc-946451eb706c`
- standings rows: 20
- current round: 1
- career phase: `PRE_MATCH`

### Process restart

- Old backend listener PID: `25364`
- Stop confirmation: old PID alive = `False`
- New backend wrapper PID: `12444`
- New backend listener PID: `26580`
- PID changed: `True`
- Backend port 8080 after restart: `True`

### After restart recovery

Recovery artifact snapshot was written to `D:\temp\mvp1-runtime-after-restart.json` without secrets:

- recovered career ID: `010f32eb-36cf-4993-91e8-344d09dadcce`
- same career as before restart: `true`
- current round: 1
- career phase: `PRE_MATCH`
- squad size: 24
- lineup players: 11
- lineup slots: 11
- round-1 fixtures: 10
- same first fixture as before restart: `true`
- standings rows: 20

### Verdict

The backend restart/recovery blocker is resolved with a real process stop/start, a different listener PID, and recovery of the same persisted career, lineup, fixture and standings through public HTTP APIs.
