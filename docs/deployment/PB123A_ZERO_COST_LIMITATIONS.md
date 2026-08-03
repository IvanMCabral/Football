# PB1.2.3A zero-cost staging limitations

- Render Free is one 512 MB/0.1 CPU web instance, sleeps after 15 minutes, has an ephemeral filesystem, 750 instance-hours/month and no horizontal scaling.
- Render Free is documented for testing/hobby use, not production.
- The current container smoke used 768 MB; Render memory/OOM compatibility is unverified.
- Render Free may suspend services when bandwidth/build quotas are exhausted. Without a payment method, over-quota behavior is suspension or disabled builds rather than billing, but this must be verified in the account.
- Neon Free is limited to 0.5 GB and 100 CU-hours/month with a six-hour restore window.
- Upstash Free is limited to 256 MB, 500K commands and 10 GB/month; durable Redis restore is not certified until a real drill.
- Firebase Spark shuts off a product after its no-cost quota is exceeded and linking billing upgrades to Blaze.
- No staging URL, region latency, cold-start, CORS, Flyway, backup, restore, browser or SSE evidence exists yet.
