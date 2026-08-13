# PB1.2.3H7.9F — Provider accounting evidence

This document records the provider-side baseline used by the one-owner dry-run.
It is read-only evidence; no Redis data or configuration was changed.

## Observed dashboard values

| Field | Value |
|---|---|
| Database | Manager |
| Plan | Free Tier |
| Region | AWS sa-east-1 (Sao Paulo) |
| Storage display | 253 MB / 256 MB |
| Commands | 150K / 500K per month |
| Bandwidth | 0 B / 50 GB |
| TLS | Enabled |
| `PING` | `PONG` |
| `DBSIZE` | 9555 |

## Accounting classification

`PROVIDER_ACCOUNTING_TOO_COARSE`

The dashboard exposes an integer MB display only.  No exact byte API was used
and no undocumented conversion from the display to bytes was assumed.  The
official Upstash pricing reference states that the Free plan has a 256 MB
maximum data size and that total storage includes the data stored at replicas
and regions:

<https://upstash.com/pricing/redis>

That source does not define whether the displayed integer is rounded, truncated,
bucketed, allocator-backed, or inclusive of provider metadata for this account.
Therefore the lower/upper byte bounds for the current dataset remain
`UNKNOWN`, and no capacity gate can be approved from `253 MB` alone.

## Consequence

The dry-run stopped earlier on `CANARY_BLOCKED_RUNTIME_CHANGED`.  Even after
runtime reconciliation, a future canary must obtain a provider-side accounting
bound conservative enough to prove the transient peak plus safety reserves.  A
local Redis `MEMORY USAGE` estimate is not interchangeable with Upstash durable
storage accounting.

No credentials, tokens, payloads, keys, or personal data are included here.
