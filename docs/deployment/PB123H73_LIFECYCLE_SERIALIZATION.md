# PB1.2.3H7.3 - lifecycle serialization

`CareerLifecycleCoordinator` maintains a bounded reactive queue per owner.
Career save, reset and continuation use the same owner queue. Derived detail
and baseline writes first validate `career-owner:{careerId}` and then join that
queue before touching ownership keys or writing the child.

The queue is non-blocking, releases on success, error and cancellation, and
removes idle owner entries. A 30-second default coordination timeout is
configurable through `REDIS_LIFECYCLE_COORDINATION_TIMEOUT`.

Reset is represented as an owner-scoped resetting state. Writes already in the
queue finish in order; writes submitted while reset is active wait behind it.
The coordinator protects one JVM only. It does not claim Redis-distributed
locking, and a future multi-instance deployment must supply that boundary.
