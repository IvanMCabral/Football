# PB1.2.3H7.3 - public cleanup error contract

Cleanup failures are translated by the WebFlux exception handler into stable,
sanitized JSON containing only `code`, public `message`, `status` and the
correlation `requestId`.

| Internal state | Public code | HTTP |
|---|---|---:|
| `REJECTED_OWNERSHIP` | `CAREER_CLEANUP_OWNERSHIP_REJECTED` | 422 |
| `PARTIAL_RETRYABLE` | `CAREER_CLEANUP_RETRYABLE` | 503 |
| `FAILED` | `CAREER_CLEANUP_FAILED` | 503 |
| index cardinality exceeded | `CAREER_INDEX_LIMIT_REACHED` | 409 |

Redis keys, UUIDs, stack traces, SQL and driver messages are never returned.
The reset endpoint cannot report 204 when cleanup has failed or remains
partial. The existing frontend receives a finite HTTP response and can display
a generic retry message; no frontend change is required for this contract.
