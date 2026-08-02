# PB1.2.3 Cloud cost model

All figures are current published provider allowances observed on 2026-08-02. They are estimates, not a quote. A billing account may be required even when monthly usage remains inside a no-cost allowance.

## Components

| Component | No-cost allowance / published starting point | Cost risk |
| --- | --- | --- |
| Firebase Hosting | 10 GB hosting storage and 10 GB/month CDN transfer at no cost | Spark site can be disabled after quota; Blaze overage is $0.026/GB storage and $0.15/GB transfer |
| Cloud Run | 240,000 vCPU-seconds and 450,000 GiB-seconds/month aggregated by billing account; 1 GiB North America network allowance | Billing account required; region, CPU allocation, requests, egress, logs and Artifact Registry can add cost |
| Neon Free | 100 CU-hours and 0.5 GB/project, no time limit/no card | Compute/storage overage or paid Launch; branch retention and connection pressure |
| Supabase alternative | 500 MB database and 5 GB egress; projects pause after one week; Pro from $25/month with seven-day backups | Free pause/no backups; Pro subscription |
| Upstash Free | 256 MB, 10 GB bandwidth, 500K commands/month | Pay-as-you-go $0.20/100K commands; fixed plans from $10/month; provider backup/SLA features vary by plan |
| GHCR | Public packages free; GitHub Free private Packages allowance 500 MB storage/1 GB transfer, shared with Actions artifacts | Private quota exhaustion blocks/pays; clean old digests |
| Domain | External registrar | Always paid and renews annually |

Sources: [Cloud Run pricing](https://cloud.google.com/run/pricing), [Firebase Hosting quotas](https://firebase.google.com/docs/hosting/usage-quotas-pricing), [Neon pricing](https://neon.com/pricing), [Supabase pricing](https://supabase.com/pricing), [Upstash pricing](https://upstash.com/pricing/redis), [GitHub Packages billing](https://docs.github.com/en/billing/concepts/product-billing/github-packages).

## Scenarios

| Scenario | Assumptions | Expected monthly posture (USD) | Interpretation |
| --- | --- | ---: | --- |
| Staging nearly idle | 5 users, two hours/day, few matches, one Cloud Run instance at scale-to-zero | 0-15 plus domain | Likely inside free allowances, but billing/card and quota shutdown remain possible |
| Small beta | 25 users, daily use, occasional simultaneous SSE | 15-45 plus domain | Neon/Upstash may remain free; paid Redis/DB and logs provide safer durability |
| Medium beta | 100 users, concurrent matches, continuous SSE | 45-150+ plus domain | Expect paid Redis/DB, Cloud Run egress/compute and observability; measure before launch |

The ranges intentionally avoid invented provider bills. Before provisioning, set Google budget alerts, Upstash budget/autoupgrade policy, Neon spend limits, and registry cleanup. Alerts do not cap Firebase/Google usage; an owner must act on them.
