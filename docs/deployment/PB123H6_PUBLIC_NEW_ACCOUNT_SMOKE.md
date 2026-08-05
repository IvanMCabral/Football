# PB1.2.3H6 — Public new-account smoke

The first three independent accounts created through the public registration
UI passed the required functional chain before the later Redis outage:

`register → dashboard → world catalog → league/team → career → squad →
11/11 lineup → first live round → first SSE`.

Observed catalog contents were the imported three leagues (Spain, Brazil and
Argentina), with 20 Spanish teams visible for the selected league. No identity
or data was fabricated. The account/career identifiers and sanitized evidence
are in `evidence/pb123h6/public-new-account-smoke.json`.

Result: **3/3 passed at the time of the smoke**. This does not override the
later readiness failure and does not certify the public beta gate.
