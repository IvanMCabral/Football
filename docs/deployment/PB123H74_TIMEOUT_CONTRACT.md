# PB1.2.3H7.4 — Contrato de timeouts

Los límites explícitos son:

| Fase | Límite |
|---|---:|
| espera de coordinación | 30 s |
| lectura/validación de índice y mappings | 5 s |
| SCAN | 10 s |
| UNLINK | 10 s |
| EXISTS | 5 s |
| cleanup total | 90 s |
| tombstone | 15 min |

La restauración de discovery tiene timeout propio y forma parte de la cadena
reactiva. No hay `subscribe` lateral ni publishers fire-and-forget. Cancelación,
error y timeout liberan la cola; los errores dejan tombstone retryable y una
respuesta pública controlada.
