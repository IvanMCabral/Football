# Production Security Headers

Date: 2026-07-31

## Backend responsibility implemented for PB1.1

The backend emits minimum defensive headers on WebFlux responses:

- `X-Request-Id` generated or propagated.
- `X-Content-Type-Options: nosniff`.
- `X-Frame-Options: DENY`.
- `Cache-Control: no-store`.

These headers are intentionally conservative and do not depend on a specific CDN.

## PB1.2 proxy/CDN responsibility

The public edge must own final browser policy headers:

- HSTS after HTTPS/domain is final.
- CSP after all frontend asset origins are known.
- Referrer-Policy.
- frame-ancestors.
- static asset cache policy.

Do not enable edge HSTS before the production domain and HTTPS rollback path are confirmed.

## PB1.2.1 Firebase Hosting baseline

The frontend hosting artifact defines initial static headers in `front-ciber/project/firebase.json`:

- `index.html`: no-cache/no-store so users receive the current application shell.
- hashed JavaScript/CSS assets: one year immutable cache.
- image/font/static assets: one year immutable cache.
- `X-Content-Type-Options: nosniff` for served assets.
- `Referrer-Policy: strict-origin-when-cross-origin` on `index.html`.

Final HSTS and CSP still belong to PB1.2 after the domain, backend origin, Firebase project and rollback path are selected.
