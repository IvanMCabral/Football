# PB1.2.3H7 - Redis storage root cause

## Verdict

**P - FREE-TIER STORAGE LIMIT EXCEEDED** is confirmed as the immediate runtime
blocker. Upstash reports 257 MB against a 256 MB Free Tier limit, and the Redis
API returned the exact capacity-quota error at 268,979,370 bytes versus a
268,435,456-byte threshold.

## Application contributors to investigate after recovery

- Career and world snapshots are persisted per user and must be measured for
  duplication and ownership cleanup.
- Detailed match and seven-day baseline data are potentially large and must be
  bounded by match/round retention.
- Runtime match, state, and command keys have short TTLs but need a complete TTL
  audit to detect missing or renewed expirations.
- Test accounts created by public audits must have explicit teardown and orphan
  cleanup.

No gameplay, simulation, probabilities, fixtures, or PostgreSQL data were
changed. No structural code change is justified until a complete key-level
inventory identifies the dominant family.
