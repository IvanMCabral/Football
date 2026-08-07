# PB1.2.3H7.5 — Real Redis integration evidence

The real Redis integration suite runs against the ephemeral Redis configured by
the test runtime. It verifies:

* stale career and world callbacks after reset;
* replacement with a new generation;
* tokenized mapping compensation while another save owns the mapping;
* owner mismatch and index cardinality limits;
* root-last cleanup, bounded unlink batches and accounting;
* ownership TTL renewal, world TTL and owner-B preservation.

Mockito is retained only for deterministic impossible-driver responses. The
stale-writer and compensation scenarios use Redis keys and the production
scripts directly. No public Redis, Neon, Render or Firebase service is touched
by this local remediation.

The final review records the exact Surefire totals, duration and any skipped
tests after the complete local suite has finished.
