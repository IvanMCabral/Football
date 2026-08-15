# PB1.2.3H7.9F.1 — Owner authority recertification

## Scope

This record corrects the owner digest algorithm while preserving the exact
historically selected `PROVEN_TEST_OWNER`. It contains sanitized authority only.

## Recertification record

| Field | Value |
|---|---|
| Historical sanitized fingerprint | `6d963e62a2a6095b976ca78156a7ef0a` |
| Historical algorithm | `MD5(UTF-8 canonical UUID string)` |
| Canonical input | Strictly parsed UUID → `UUID.toString()` lowercase 8-4-4-4-12 form → UTF-8 |
| New algorithm | SHA-256, lowercase full hexadecimal |
| Certified owner SHA-256 | `7fcae17b55af464cc929f242689bb456d9cc06718cf47d2e9642966041cd71e8` |
| Same owner proven | YES |
| Provenance classification | `PROVEN_TEST_OWNER` |
| Owner selection changed | NO |
| Raw owner persisted | NO |
| Recertification time | `2026-08-15T18:14:49Z` |

## Lineage proof

The retained private 144-owner evidence contains exactly one canonical UUID
whose MD5 equals the historical fingerprint. The same canonical UUID produced
the new SHA-256 with two independent local implementations. Both results match
the production authority constant. The raw UUID never left transient process
memory and is intentionally absent from this record.

Historical documents are not rewritten. Their 32-character owner identifier
remains evidence of the original selection, now correctly classified as MD5
provenance rather than SHA-256 authority.

## Authorization

- Public validation authorized: NO.
- Public canary execution authorized: NO.
- Bulk migration authorized: NO.
- Cleanup authorized: NO.
