# PB1.2.3H7.5 — World write contract

World data has two explicit write paths:

* `saveInitial` is used only while no career root and no owner career index
  exist. It is the catalog/bootstrap path.
* `saveWithContext` is used for a career-derived update and requires a matching
  `CareerWriteContext`; it validates lifecycle ownership before writing.

The legacy production `save` path fails closed when the ownership service is
present. This prevents a late world callback from silently turning a reset
into a new orphan world. During a reset, the owner tombstone also rejects
initialization. After reset, an existing career root or index rejects the
initial path, even if the world key itself is absent.

The world snapshot retains its bounded TTL. Career cleanup may intentionally
preserve the world catalog while deleting career-owned projections; the next
career update must use the fenced path.
