# PB1.2.3A Upstash Free provisioning report

Status: `NOT PROVISIONED - AUTHENTICATION REQUIRED`

Upstash Free is documented as $0 with 256 MB data, 500K commands/month and 10 GB monthly bandwidth. TLS and ACL support are available. Adding a card upgrades the database to pay-as-you-go; no card will be added. Source: [Upstash Redis pricing](https://upstash.com/pricing/redis).

## Planned steps

1. Create one Free database in the closest compatible region.
2. Confirm the dashboard says Free / USD 0 and does not request payment.
3. Configure endpoint, port, username when supplied, password and `REDIS_SSL=true` in Render secrets.
4. Test an isolated `PB123A` key with TTL, GET and DELETE only.
5. Record command/storage limits without recording credentials.
6. Execute provider backup/restore only if the Free plan exposes it; otherwise record `REDIS RESTORE NOT AVAILABLE ON FREE PLAN`.

Free Redis is staging-only until loss/reconnect and restore evidence exists. Career and detailed-match state remains classified as durable and not safely reconstructible.
