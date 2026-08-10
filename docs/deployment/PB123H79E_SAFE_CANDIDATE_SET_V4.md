# PB1.2.3H7.9E - Safe candidate set V4

The V4 candidate set is empty because the canonical PostgreSQL gate is blocked.
No key is eligible merely because its owner is absent from the retained
users-only join.

| Field | Value |
|---|---:|
| Candidate owners | 0 |
| Candidate careers | 0 |
| Candidate keys | 0 |
| Family counts | `{}` |
| Deduplicated keys | 0 |
| Current-owner collisions | 0 in retained join; fresh check unavailable |
| Contradictory mappings | 0 observed in Redis |
| Unknown prefixes | 0 |
| Candidate exact measured bytes | 0 |
| Candidate sampled bytes | 0 |
| Candidate unknown bytes | 0 |
| Candidate list SHA-256 | `4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945` |

The hash is SHA-256 of the canonical empty JSON list `[]`. No payload or full
UUID list is exported.

Projected state is unchanged: `DBSIZE=9638`, storage `256 MB / 256 MB`, below
200 MB `false`, preferred 160-180 MB `false`.
