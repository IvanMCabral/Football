# PB1.2.3H7.5 — Mapping compensation contract

Each career save obtains a fresh operation token in
`career-mapping-token:{careerId}`. The owner mapping remains immutable and is
never reassigned to another owner. A failed save executes a Redis compare-and-
delete script that removes mapping, mapping token, index membership and (only
when it did not exist before the operation) the root, but only when the token
still equals the token created by that save.

If a later save advances the token, the earlier compensation returns zero and
leaves the later mapping, root and index intact. This closes the
GET/compare/DELETE race. Owner mismatches and missing mappings fail closed.

The cleanup repository remains the sole owner of root deletion. It scans and
unlinks children in batches of at most 100, deletes ownership records and the
index, and processes the career root last. A root failure is retryable; the
cleanup tombstone preserves discovery metadata for the bounded retry window.
