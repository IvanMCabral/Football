# PB1.2.3 Required user decisions

These are the only decisions that cannot be inferred safely from the repository. They are recorded for PB1.2.3A; no decision was made or provisioned by this task.

1. **Billing/card:** accept a Google billing account for Cloud Run/Firebase Blaze and configure a hard monthly budget alert. Recommendation: accept for staging only with max instances and alerts; alerts are not hard caps.
2. **Provider stack:** approve Firebase Hosting + Cloud Run + Neon + Upstash. Alternative: Koyeb + Neon + Upstash, accepting US/EU region latency and weaker SSE suitability.
3. **Region:** prefer `southamerica-east1` for Cloud Run if Neon and Upstash offer an acceptable nearby endpoint; otherwise choose the nearest common region after measuring RTT.
4. **Firebase project/domain:** choose project ID, staging hostname and whether a custom domain is used in addition to `web.app`/`firebaseapp.com`.
5. **Redis loss policy:** choose paid persistent Redis/backup for beta, or explicitly accept disposable staging while durable career/match state is moved to PostgreSQL. Recommendation: do not open public beta with loss-tolerant career state.
6. **Budget:** choose a monthly ceiling for the three scenarios in `PB123_CLOUD_COST_MODEL.md` and who receives alerts.
7. **Runtime availability:** choose `min-instances=0` for cheapest staging or `1` for lower cold-start risk; recommendation is 0 for staging, 1 for a beta rehearsal.
8. **Registry:** choose Artifact Registry for Cloud Run IAM simplicity or GHCR for provider neutrality and existing GitHub integration. Recommendation: Artifact Registry primary, GHCR documented fallback.
