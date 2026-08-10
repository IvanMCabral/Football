# PB1.2.3H7.9E — Runtime/data-store identity

## Scope

This is a read-only identity gate. No Redis command that mutates data, no
PostgreSQL mutation, no infrastructure change and no cleanup was performed.

Observation date: 2026-08-10.

## Render

| Field | Observed value |
|---|---|
| Service | `manager-staging-api` |
| Branch | `feat/v25d99.20.3.1-runtime-fixes` |
| Live deployment | `LIVE` |
| Live commit | `bf316f01f3d044fea6391a99364d1c87a24fca15` |
| Plan | Free |
| Region | Oregon |
| Manual instances | 1 |
| Autoscaling | Off |

The dashboard showed the live commit directly. The service is therefore a
single-instance deployment for this observation.

## PostgreSQL dependency

Render's environment page and startup logs agree on the following sanitized
identity:

- DB host fingerprint (SHA-256):
  `6117fb00c0db26a545797814525876f6f1e170f1c28121c23cb88b6ffeb2a0f3`.
- Database name: `neondb`.
- Schema: `public`.
- Startup connection: PostgreSQL 18.4.
- Flyway: one migration validated; schema version 1; no pending migration.

This is the configured Render dependency and is classified
`EXACT_RUNTIME_DEPENDENCY`. The Neon browser session currently shows the login
page, so a fresh SELECT-only owner census against this exact database could not
be executed in this turn. The local `.env` was deliberately not used as an
authority: its `football_manager`/PostgreSQL 15.6 connection is a different
database identity.

## Redis dependency

Render's environment page exposed the Redis host and port without exposing a
password:

- Redis host fingerprint (SHA-256):
  `3cdf2db0b5e1ef0d740da18ce1329f9e9b9c442257cbb9dcf111cb5fa6050e64`.
- Port: `6379`.
- Upstash database: `Manager`, Free Tier, AWS `sa-east-1`, TLS enabled.
- Upstash host fingerprint matches the Render value exactly.

The Redis dependency is classified `EXACT_RUNTIME_DEPENDENCY`. Current provider
state remains `PING=PONG`, `DBSIZE=9638`, and `256 MB / 256 MB`.

Three public health samples were also read without mutation: liveness was 3/3
HTTP 200; readiness was 0/3 HTTP 200 and 3/3 HTTP 503, reporting
`database=UP` and `redis=DOWN`.

## Gate result

| Dependency | Result |
|---|---|
| Render service | `EXACT_RUNTIME_DEPENDENCY` |
| PostgreSQL configured dependency | `EXACT_RUNTIME_DEPENDENCY` |
| Redis configured dependency | `EXACT_RUNTIME_DEPENDENCY` |
| Fresh canonical PostgreSQL authority | `NOT_VERIFIABLE` |
| Neon branch/history | `NOT_VERIFIABLE` (current browser session unauthenticated) |

The data stores are identified, but canonical authority has not been freshly
verified for this run. The cleanup gate therefore stops before deletion
eligibility.
