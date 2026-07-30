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
