# PB1.2.3 Managed PostgreSQL runbook

This runbook is executed only after the user approves provisioning. It contains no real project, host, credential or domain.

## Provider and region

Create one Neon staging project in the closest region shared with the selected Cloud Run and Redis locations. Neon Free has no time limit and no card, but its allowance is 0.5 GB and 100 CU-hours per project/month. If the dataset or restore window exceeds that allowance, use Launch or Supabase Pro rather than silently accepting failures.

## Provisioning

1. Create a dedicated staging project and database role with only application privileges.
2. Require TLS and record the provider CA/SSL mode required by the driver.
3. Obtain both provider endpoints when available:
   - JDBC URL for Flyway at startup;
   - pooled R2DBC URL for request traffic.
4. Configure the standard application variables without inventing aliases:
   `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.
5. Set R2DBC pool size below the provider connection limit; begin with a small pool and measure saturation.
6. Start the existing `prod` container and verify `flyway_schema_history` before loading the validated MVP 1 dataset.

## First-start checks

- Flyway completes once and is idempotent on restart.
- `SELECT 1` readiness succeeds.
- Dataset counts match the MVP 1 closure audit.
- Login, career creation, lineup save, fixture, standings and detailed match smoke tests pass.
- No JDBC password, connection URL or provider token is printed.

## Backup and restore

Before every schema-changing staging deploy, run a compressed custom-format `pg_dump` and record a SHA-256 checksum outside Git. Keep seven daily and four weekly dumps for staging. Restore into a new database/project, never over the source first. Validate schema history, countries/leagues/clubs/players/traits, users, careers, fixtures, standings and detailed match rows, then run the smoke suite against the restored endpoint.

Initial staging objectives: RPO 24 hours, RTO 4 hours. Public beta must tighten these after measuring a real restore. Neon time travel is useful for short rollback but is not a substitute for an independently retained logical dump.

## Rotation and teardown

Rotate the database password before public beta and after any exposure. Revoke the old role, update the provider secret, restart the revision, and verify readiness. Delete disposable branches and old staging projects after the evidence is archived.
