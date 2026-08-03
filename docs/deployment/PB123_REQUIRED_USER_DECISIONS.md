# PB1.2.3 Required user decisions

These are the only decisions that cannot be inferred safely from the repository. They are recorded for PB1.2.3A; no decision was made or provisioned by this task.

1. **Billing/card:** do not add a card, billing account or pay-as-you-go plan. If any dashboard requests one, stop that resource and record the payment gate.
2. **Provider stack:** approve Firebase Hosting Spark + Render Free + Neon Free + Upstash Free. Cloud Run is a historical alternative rejected because it requires billing.
3. **Region:** choose the closest Render region shared by Neon and Upstash; Render's published list is Oregon, Ohio, Virginia, Frankfurt and Singapore, so latency from Argentina is a measured risk.
4. **Firebase project/domain:** choose project ID, staging hostname and whether a custom domain is used in addition to `web.app`/`firebaseapp.com`.
5. **Redis loss policy:** choose paid persistent Redis/backup for beta, or explicitly accept disposable staging while durable career/match state is moved to PostgreSQL. Recommendation: do not open public beta with loss-tolerant career state.
6. **Budget:** choose a monthly ceiling for the three scenarios in `PB123_CLOUD_COST_MODEL.md` and who receives alerts.
7. **Runtime availability:** accept Render Free sleep after 15 minutes and approximately one-minute wake-up; no paid keep-alive.
8. **Registry:** choose GHCR only if its free package quota is acceptable; Render can build directly from the repository and no registry is required for the first attempt.
