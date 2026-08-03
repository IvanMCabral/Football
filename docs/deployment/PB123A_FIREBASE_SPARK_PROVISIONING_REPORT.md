# PB1.2.3A Firebase Spark provisioning report

Status: `NOT PROVISIONED - AUTHENTICATION REQUIRED`

Firebase documents Spark as requiring no payment information. Hosting has no-cost quotas, while Cloud Run and other paid Google Cloud products are unavailable on Spark. Linking a billing account automatically upgrades the project to Blaze. Source: [Firebase pricing plans](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans), [Hosting quotas](https://firebase.google.com/docs/hosting/usage-quotas-pricing).

## Planned steps

1. Authenticate Firebase CLI manually if prompted.
2. Create or select a project without linking billing.
3. Enable Hosting only; do not enable Cloud Functions, App Hosting or Cloud Run.
4. Record sanitized project ID and generated `.web.app` / `.firebaseapp.com` origins.
5. Build the Angular staging configuration only after the real Render URL is known.
6. Deploy `dist/demo/browser` and inspect routes, headers, localhost references and debug chunks.

No Firebase project ID, token or deployment URL is invented or committed.
