# PB1.2.1 Frontend Hosting Runbook

Date: 2026-08-01

Frontend path:

```text
D:\ProyectosOpenCode\MANAGER\front-ciber\project
```

## Build

```powershell
npm run build
node tools/inspect-production-artifact.mjs
```

Production output:

```text
dist/demo/browser
```

The inspection verifies:

- `index.html` exists;
- JavaScript files are hashed;
- no `debug/test-harness`;
- no `test-harness-page`;
- no localhost API references;
- no public source map references.

## Firebase Hosting

Files added:

- `firebase.json`;
- `.firebaserc.example`;
- `tools/inspect-production-artifact.mjs`.

`firebase.json` serves `dist/demo/browser`, rewrites SPA routes to `/index.html`, keeps `index.html` uncached and caches hashed assets for one year.

No real Firebase project ID is committed. Copy `.firebaserc.example` to `.firebaserc` locally or configure the project through Firebase CLI when PB1.2.2 creates the staging resource.

## API routing

The Angular production environment uses a relative API base:

```text
/api/v1
```

For Firebase Hosting, PB1.2.2 must choose one of:

- proxy/rewrite `/api/**` to the backend if the selected Firebase setup supports the required SSE behavior; or
- configure the frontend to call the Cloud Run backend origin directly and allow it through production CORS.

No final backend URL is invented in PB1.2.1.

## SSE caveat

SSE must be tested behind the selected edge. Firebase Hosting/CDN behavior around buffering and idle timeouts is not certified yet.
