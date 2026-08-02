# PB1.2.3 Cloud staging provider decision

Date: 2026-08-02

## Decision

**Primary architecture:** Firebase Hosting + Google Cloud Run + Neon Postgres + Upstash Redis. The choice minimizes operations while preserving the Docker artifact already approved in PB1.2.2. It is a staging design only: no account, project, resource, billing configuration, or deployment was created by this document.

| Layer | Primary | Alternative | Initial cost posture | Card/billing | Main risk |
| --- | --- | --- | --- | --- | --- |
| Angular SPA | Firebase Hosting | Cloudflare Pages | Free allowance is sufficient for a small SPA | Firebase Spark needs no payment method; Cloud Run/API requires a Google billing account | 10 GB storage and 10 GB/month transfer no-cost quota; excess can disable Spark hosting |
| Docker API | Cloud Run | Koyeb free instance or Render paid web service | Pay per use; free allowance can cover low traffic | Google billing account required even when usage stays within allowance | Cold start, request/stream timeout, egress and billing coupling |
| PostgreSQL | Neon | Supabase | Neon Free has no time limit and no card; paid Launch is usage-based | No card for Free; paid use requires billing | 0.5 GB/100 CU-hours Free allowance and connection/region validation |
| Redis | Upstash Redis | Redis Cloud or paid Upstash Fixed | Free prototype tier; pay-as-you-go starts at $0.20/100K commands | Free can be used without paid upgrade; budget controls require paid plan | Free 256 MB/500K commands and no production SLA; durable state needs restore drill |
| Image registry | Artifact Registry | GitHub Container Registry | Artifact Registry is natural for Cloud Run; GHCR is already integrated with GitHub | Google billing applies to storage/egress; GHCR public packages are free and private plans have quotas | Retention and IAM must be configured before staging |

## Provider comparison

### Backend

- **Cloud Run:** Docker-native, HTTPS, revisions, traffic splitting, logs and metrics. Maximum request timeout is 60 minutes and maximum concurrency is 1,000; HTTP/2 client concurrent streams are capped at 100. It requires a billing account and can scale to zero. The free allowance is aggregated by Google billing account, not a promise of a permanently free service. Closest candidate region is `southamerica-east1`; confirm database/Redis region latency during provisioning. Source: [Cloud Run pricing](https://cloud.google.com/run/pricing), [request timeout](https://docs.cloud.google.com/run/docs/configuring/request-timeout), [quotas](https://docs.cloud.google.com/run/quotas).
- **Koyeb:** Free instance is permanent but limited to one 512 MB/0.1 vCPU/2 GB instance, only Frankfurt or Washington DC, scales to zero after one hour and is explicitly not intended for production. Good disposable alternative, weak for live SSE from Argentina. Source: [Koyeb pricing FAQ](https://www.koyeb.com/docs/faqs/pricing), [instance limits](https://www.koyeb.com/docs/reference/instances).
- **Render:** Free web services sleep after 15 minutes, wake in about one minute, have 750 free instance-hours/month and no persistent disk or horizontal scaling. Free Postgres expires after 30 days, so Render is an acceptable paid backend alternative, not the primary free beta stack. Source: [Render free services](https://render.com/docs/free), [Postgres refresh](https://render.com/docs/postgresql-refresh).
- **Railway:** Convenient Docker and managed services, but the current pricing page describes a trial/usage model rather than a guaranteed permanent free production tier. Treat any credit as temporary and set spend limits before use. Source: [Railway pricing](https://railway.com/pricing).
- **Fly.io:** Strong regional placement and Docker control, but there is no new permanent free plan; a card and usage billing are expected. A 256 MB shared VM is approximately $2.02/month before volumes, egress and managed services. Source: [Fly pricing](https://fly.io/docs/about/pricing/), [cost management](https://fly.io/docs/about/cost-management/).
- **Oracle Cloud Always Free:** Potentially zero compute cost with an always-free Arm allocation, but capacity, home-region, account verification and VM operations are user-owned risks. It is the lowest cash alternative and the highest maintenance alternative. Source: [Oracle Free Tier](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier.htm).

### PostgreSQL

- **Neon:** Free is $0 with no time limit or card, 0.5 GB/project, 100 CU-hours/project/month, scale-to-zero, pooling and a six-hour time-travel/restore window. Launch is usage-based (typical spend published as about $15/month for intermittent 1 GB) and extends restore history. Source: [Neon pricing](https://neon.com/pricing).
- **Supabase:** Free is $0 with 500 MB database, 5 GB egress and projects paused after one week of inactivity; automatic backups and PITR are not included. Pro starts at $25/month and includes daily backups retained seven days. Source: [Supabase pricing](https://supabase.com/pricing).
- **Railway Postgres:** operationally simple but usage-priced and not selected because the current free permanence and backup contract are not as explicit as Neon.
- **Render Postgres:** free database expires after 30 days and has no backups; paid storage is published at $0.30/GB/month. It is not suitable for the primary staging dataset. Source: [Render free services](https://render.com/docs/free), [Postgres pricing refresh](https://render.com/docs/postgresql-refresh).

### Redis

- **Upstash:** Free is 256 MB, 10 GB bandwidth and 500K commands/month. Pay-as-you-go is $0.20/100K commands; fixed plans start at $10/month. TLS is supported and provider backups can be created/restored; daily backup retention options must be verified on the selected plan before provisioning. Source: [Upstash pricing](https://upstash.com/pricing/redis), [backup and restore](https://upstash.com/docs/redis/features/backup), [FAQ](https://upstash.com/docs/redis/help/faq).
- **Redis Cloud:** viable paid alternative if a larger persistent Redis and explicit SLA are required; pricing and region must be checked at provisioning time.
- **Railway Redis:** easy to attach to a Railway service, but durability and free permanence are usage-plan dependent; not preferred for a cross-provider architecture.
- **Render Key Value:** Free Key Value is in-memory and loses data on restart/upgrade, so it is unsuitable for this application's runtime-critical state. Source: [Render free services](https://render.com/docs/free).

### Frontend and registry

- **Firebase Hosting:** selected because the repository already contains a compatible `firebase.json`, the Angular output is static, HTTPS is built in, and the no-cost quota is 10 GB storage plus 10 GB/month transfer. Spark has no payment method requirement; Blaze is required only when using paid Google services or exceeding no-cost quotas. Source: [Firebase Hosting quotas](https://firebase.google.com/docs/hosting/usage-quotas-pricing), [Firebase plans](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans).
- **Cloudflare Pages:** strong free static hosting/CDN and a simple migration target, with 500 free deploys/month and static asset requests free; it would add a second edge provider and require a new deployment configuration. Source: [Pages overview and limits](https://developers.cloudflare.com/pages/), [Pages pricing](https://developers.cloudflare.com/pages/functions/pricing/).
- **Vercel:** excellent Angular static deployment and previews, but Hobby is for personal/non-commercial use, has a 120-second proxied request timeout and 1-hour runtime log retention. It is not suitable for a public commercial beta without Pro; keep it as a migration alternative. Source: [Vercel pricing](https://vercel.com/pricing), [Hobby limits](https://vercel.com/docs/plans/hobby), [Vercel limits](https://vercel.com/docs/limits).
- **Artifact Registry:** preferred with Cloud Run because IAM, regional placement and image digests are in one Google project. Storage/egress and retention must be costed in the project.
- **GitHub Container Registry (GHCR):** provider-neutral and fits the existing GitHub workflow. Public packages are free; GitHub Free private packages include 500 MB storage and 1 GB transfer shared with Actions artifacts. It is the fallback if avoiding Google registry coupling matters. Source: [GitHub Packages billing](https://docs.github.com/en/billing/concepts/product-billing/github-packages).

## Acceptance boundary

This decision is ready for PB1.2.3A provisioning, but it is not a public-beta approval. The following must be proven with real staging resources: region latency, Flyway over JDBC plus R2DBC pooling, Redis ACL/TLS, SSE reconnect, backup/restore, spend alerts and rollback.
