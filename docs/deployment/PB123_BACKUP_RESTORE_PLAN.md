# PB1.2.3 Backup and restore plan

## Ownership and objectives

The staging owner runs the drills and stores evidence outside Git. Initial targets are RPO 24 hours and RTO 4 hours; public beta must revise these after measuring real data size and provider recovery time.

## PostgreSQL drill

1. Freeze schema-changing deploys and record source revision.
2. Run `pg_dump -Fc` against the managed staging database; encrypt the dump and checksum it.
3. Restore into a new database/project with a new role.
4. Start the exact backend image against the restore using a separate secret set.
5. Compare migration history and counts for countries, leagues, clubs, teams, players, traits, users, careers, fixtures, standings and match details.
6. Run login, career, lineup, fixture, round, standings and detailed-match smoke tests.
7. Record duration, checksum, missing rows and decision. PASS requires zero unexplained differences.

## Redis drill

1. Record provider plan, region, key count, memory, command rate and representative TTLs.
2. Create a provider backup/export; do not print tokens or values.
3. Restore into a separate database.
4. Verify each family in `PB123_MANAGED_REDIS_RUNBOOK.md`, including TTLs and career/detail reads.
5. Simulate source loss and confirm backend readiness is 503, no silent cache-miss success occurs, and recovery is explicit.
6. Repeat with an active LiveSession and document exactly what is lost.

## Release gates

| Drill | Frequency | PASS evidence | FAIL action |
| --- | --- | --- | --- |
| PostgreSQL dump/restore | before schema change, then weekly staging | checksum, restore duration, count comparison, smoke output | stop release; restore from prior dump |
| Redis backup/restore | before public beta, then monthly | provider backup ID, key/TTL comparison, loss/reconnect result | keep beta closed or move durable state to SQL |
| Recovery after Cloud Run restart | every release candidate | readiness, login, SSE reconnect, no duplicate commands | rollback revision and investigate |
