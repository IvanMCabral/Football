# PB1.2.3H7.5 — Timeout contract

The lifecycle coordinator applies a bounded timeout to every queued owner or
career operation. Redis ownership reads, generation validation and TTL renewal
also have explicit operation timeouts. Cleanup bounds index discovery, SCAN,
UNLINK, tombstone updates, restore discovery and the complete operation.

Cancellation releases the coordinator slot. A timeout does not run a detached
publisher: the operation fails, the tombstone remains retryable, and no
compensation continues after the request publisher terminates. Root deletion
is the final phase and a failed root is reported as retryable rather than a
false success.
