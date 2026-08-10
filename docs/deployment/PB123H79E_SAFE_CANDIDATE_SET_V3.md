# PB1.2.3H7.9E — Safe candidate set V3

## Candidate set

The V3 candidate set is intentionally empty. The dependency identity is known,
but the current canonical PostgreSQL authority and complete owner-reference
search are not freshly verifiable. The set therefore fails rule A/B and cannot
be used for deletion.

| Field | Value |
|---|---:|
| Candidate owners | 0 |
| Candidate careers | 0 |
| Candidate keys | 0 |
| Candidate family counts | `{}` |
| Deduplicated keys | 0 |
| Foreign-owner keys | 0 |
| Protected/excluded keys | 9,638 |
| Unknown-prefix keys | 0 |
| Candidate measured bytes | 0 |
| Candidate unknown bytes | 0 |
| Minimum provable freed bytes | 0 |
| Candidate list hash | `4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945` |

The hash is SHA-256 of the canonical empty JSON list `[]`. No key names or
payloads are exported.

## Projection

Projected state is unchanged: `DBSIZE=9638`, provider storage `256 MB / 256 MB`,
and projected free space `0`. The set cannot demonstrate a reduction below
200 MB or 160–180 MB because no deletion is authorized.
