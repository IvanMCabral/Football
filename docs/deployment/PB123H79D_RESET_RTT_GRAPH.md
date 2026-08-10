# PB1.2.3H7.9D — Reset RTT graph

## Scope

This graph records the physical remote layers of the owner-scoped career reset.
It does not change ownership, generation fencing, tombstone/retry semantics or
the root-last contract.  The superseded public runtime (`9c69eace`) exposed
stage timings but not command counts, which is why its cleanup total could not
be reconciled.  The current runtime emits the missing counters and uses a
bounded atomic finalization for the verified empty modern-manifest case.

## Physical graph

| Layer | Operation | Redis command(s) | Dependency | Can combine | Current instrumentation |
|---:|---|---|---|---|---|
| 1 | lifecycle admission and authoritative validation | coordinator queue; `MGET career-owner`, manifest-version, generation; owner tombstone `SET` in parallel | first | tombstone and validation run concurrently | `ownershipValidationMs`, `tombstoneCommands` |
| 2 | modern discovery | manifest `SMEMBERS` and owner projection `SCAN` in parallel | after layer 1 | independent reads are zipped | `manifestReadMs`, `projectionScanMs`, `projectionScanMatches`, `scanCount` |
| 3 | empty-modern bounded finalization | one Lua `EVAL` using exact `UNLINK` keys and final root `UNLINK`; tombstone `DEL` last | after layer 2 | metadata, projection keys and root are one script; root remains final data delete | `atomicCleanupMs`, `atomicScriptCommands` |
| 3a | non-empty child deletion | bounded `UNLINK` batches, max 100 keys | after layer 2 | child batches are coalesced and ordered | `childDeleteCommands`, `childUnlinkMs` |
| 4 | non-empty metadata deletion | one or more bounded `UNLINK` batches | after child batches | exact metadata keys are coalesced | `metadataDeleteCommands`, `metadataMs` |
| 5 | non-empty root finalization | one bounded `UNLINK` batch | after metadata | intentionally separate | `rootDeleteCommands`, `rootUnlinkMs` |
| 6 | non-empty tombstone clear | exact owner tombstone `DEL` | after root | intentionally separate for retry semantics | `tombstoneClearCommands`, `tombstoneMs` |
| error | shortfall/timeout recovery | grouped `EXISTS` Lua script only after a short delete | only on shortfall | no normal-path request | `existsCommands`, `existsMs` |

There is no global `SCAN`, `KEYS`, fire-and-forget publisher, or unbounded
delete. Projection matching is a count only; values and personal data are not
logged.

## Empty modern contract

The atomic path is selected only when all of the following are true:

- marker is exactly `1`;
- owner mapping and lifecycle generation were read together and match the
  authenticated owner/generation;
- the reset tombstone is active and in `RESETTING` state;
- the exact manifest set is empty;
- the bounded projection discovery has completed;
- the root exists and is the final destructive command;
- no partial/retry state is being resumed.

The script validates every fence before deleting anything. It then removes
only the exact discovered projection/metadata keys, removes mapping, index,
generation and manifest metadata, unlinks the career root last, and clears the
tombstone after the root. A negative script result fails closed and leaves the
tombstone available for retry. If the bounded key list exceeds 100 entries or
the generation is unavailable, the implementation falls back to the existing
ordered batch path.

`manifestEntries=0` therefore produces `childDeleteCommands=0`; the protected
projection scan is still retained when required for legacy user-scoped keys.

## Layer count and latency model

For the superseded public sample, the measured stage headers summed to about
1.22 s while cleanup was about 2.11 s. That gap was an instrumentation defect,
not an accepted provider explanation. The current counters make hidden
sequential work explicit.

The empty modern path has three sequential remote layers (validation/tombstone,
parallel discovery, atomic finalization). At a 175 ms provider RTT the modeled
Redis floor is approximately 525 ms plus server processing and HTTP overhead.
The non-empty path remains bounded and root-last; its layer count is emitted as
`X-Reset-Sequential-Layers` and includes each actual child, metadata, root and
tombstone-clear batch.

## Local evidence

Against ephemeral real Redis, the empty modern integration scenario passed with
one atomic script, zero child-delete commands, root-last deletion, mapping and
generation removal, and no remaining owner tombstone. Existing non-empty
profiles remain bounded to batches of at most 100 keys and preserve owner-B
isolation. The real-Redis boundary matrix passed for manifest sizes 1, 10,
100, 101, 500 and 1,024; child batches were respectively 1, 1, 2, 2, 6 and
11 (the additional key in each case is the protected owner projection index).
The dedicated empty-manifest N=20 profile measured p50/p95/max **2/7/27 ms**.
The focused cleanup unit and real-Redis suites are green.

## Public gate

The exact runtime `e175d718` was deployed and the response exposed the new
headers. Fresh public N=3 and N=10 runs were 100% `MODERN_MANIFEST`, HTTP 204,
with zero 500/503 responses. N=10 client p50/p95 was 1292.5/1309 ms and the
cleanup p50/p95 was 698/705 ms, so the explicit performance gate is closed.
Render does not expose its live SHA through the public API; its classification
therefore remains `SHA_NOT_EXPOSED`. Later commits in this branch only update
documentation and tests; they do not alter the deployed reset code.
