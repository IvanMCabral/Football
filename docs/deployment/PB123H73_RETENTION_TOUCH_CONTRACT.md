# PB1.2.3H7.3 - coordinated retention touch

The ownership touch contract renews the following together:

- career root: 30 days;
- owner index: 31 days;
- career-owner mapping: 31 days.

Career save and root extension renew the ownership structures. Detailed-match
and baseline writers validate the mapping, join the owner queue, renew root,
index and mapping, and only then write their child. A missing or contradictory
mapping blocks the child write. A child failure after a successful touch is an
inocuous extension, not an orphan-producing write.

The 31-day discovery window remains longer than the 30-day derived data window.
The contract is bounded and local; it does not claim durability beyond Redis
retention or distributed lock semantics.
