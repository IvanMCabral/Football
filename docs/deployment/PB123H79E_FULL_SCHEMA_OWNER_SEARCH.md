# PB1.2.3H7.9E - Full-schema owner search

## Fresh-query status

No tables or columns were searched in this gate because the authenticated
connection to Render's exact Neon database was unavailable. A users-only join
from retained evidence is not a substitute for the required full-schema
search.

Required columns remain:

`user_id`, `userid`, `owner_id`, `ownerid`, `account_id`, `accountid`,
`created_by`, `createdby`, `career_id`, `careerid`, `player_owner`,
`manager_id`, `profile_id`, and UUID columns requiring classification.

| Inventory item | Fresh result |
|---|---:|
| Tables inspected | 0 |
| Owner-reference columns inspected | 0 |
| Career-reference columns inspected | 0 |
| Inconclusive UUID columns inspected | 0 |
| Redis owners searched across all surfaces | 0/144 |
| Redis careers searched across all surfaces | 0/22 |

The retained users-only evidence remains corroboration only: 73 users, 0/144
Redis owners present in that join, and 21 known PB123 audit users with zero
Redis keys. It does not authorize deletion.
