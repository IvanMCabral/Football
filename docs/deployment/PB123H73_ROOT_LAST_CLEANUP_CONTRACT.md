# PB1.2.3H7.3 - root-last cleanup contract

Cleanup order is explicit:

1. validate every career mapping;
2. stop owner/career registries in the application service;
3. remove world, projections, runtime, state, commands, details and baselines;
4. remove the owner index and career-owner mappings;
5. remove `career:{owner}` as the final destructive operation.

The root is no longer included in the first generic pattern. Any scan, UNLINK,
EXISTS or timeout error keeps the root as the retry anchor. If a later phase
fails after discovery keys were removed, the validated owner mapping and index
are restored best-effort with bounded TTL so a second reset can converge.

`deleted > requested` is a hard `FAILED` result. An unexplained shortfall is
`PARTIAL_RETRYABLE`; neither result is a successful reset or HTTP 204.
