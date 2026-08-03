# PB1.2.3A Neon Free provisioning report

Status: `NOT PROVISIONED - AUTHENTICATION REQUIRED`

Neon Free is documented as $0, no time limit and no credit card. The allowance is 0.5 GB storage and 100 CU-hours per project/month, with scale-to-zero and a six-hour time-travel/restore window. Source: [Neon pricing](https://neon.com/pricing).

## Planned steps

1. Create one staging project in the closest region compatible with Render and Upstash.
2. Record only sanitized project/branch identifiers.
3. Require TLS and obtain direct and pooled connection details.
4. Use direct JDBC for Flyway; test pooled versus direct R2DBC before selecting one.
5. Configure DB variables in Render `sync: false` fields.
6. Run `SELECT version()`, `SELECT current_database()` and `SELECT current_user` without storing credentials.
7. Run Flyway and the MVP 1 smoke suite.

No project ID, host, password, URL or query output is present in this report.
